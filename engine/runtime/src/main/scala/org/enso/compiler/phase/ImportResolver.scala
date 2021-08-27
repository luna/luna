package org.enso.compiler.phase

import org.enso.compiler.Compiler
import org.enso.compiler.core.IR
import org.enso.compiler.data.BindingsMap
import org.enso.compiler.data.BindingsMap.ModuleReference
import org.enso.compiler.exception.CompilerError
import org.enso.compiler.pass.analyse.BindingAnalysis
import org.enso.editions.LibraryName
import org.enso.interpreter.runtime.Module

import scala.collection.mutable

/** Runs imports resolution. Starts from a given module and then recursively
  * collects all modules that are reachable from it.
  *
  * Each of the reachable modules will be parsed and will have imported modules
  * injected into its metadata. In effect, running this will bring every module
  * that could ever be necessary for the entry point compilation to at least
  * the [[Module.CompilationStage.AFTER_IMPORT_RESOLUTION]] stage.
  *
  * @param compiler the compiler instance for the compiling context.
  */
class ImportResolver(compiler: Compiler) {

  /** Runs the import mapping logic.
    *
    * @param module the entry-point module.
    * @return a list of all modules that need to be compiled in order to run
    *         the program.
    */
  def mapImports(module: Module): List[Module] = {
    @scala.annotation.tailrec
    def go(
      stack: mutable.Stack[Module],
      seen: mutable.Set[Module]
    ): List[Module] = {
      if (stack.isEmpty) {
        seen.toList
      } else {
        val current = stack.pop()
        if (seen.contains(current)) {
          go(stack, seen)
        } else {
          // get module metadata
          compiler.ensureParsed(current)
          val ir = current.getIr
          val currentLocal = ir.unsafeGetMetadata(
            BindingAnalysis,
            "Non-parsed module used in ImportResolver"
          )
          // put the list of resolved imports in the module metadata
          if (
            current.getCompilationStage
              .isBefore(Module.CompilationStage.AFTER_IMPORT_RESOLUTION)
          ) {
            val importedModules: List[
              (IR.Module.Scope.Import, Option[BindingsMap.ResolvedImport])
            ] =
              ir.imports.map {
                case imp: IR.Module.Scope.Import.Module =>
                  val impName = imp.name.name
                  val exp = ir.exports
                    .collect { case ex: IR.Module.Scope.Export.Module => ex }
                    .find(_.name.name == impName)
                  val libraryName = imp.name.parts match {
                    case namespace :: name :: _ =>
                      LibraryName(namespace.name, name.name)
                    case _ =>
                      throw new CompilerError(
                        "Imports should containt at least two segments after " +
                        "desugaring."
                      )
                  }
                  compiler.packageRepository
                    .ensurePackageIsLoaded(libraryName) match {
                    case Right(()) =>
                      compiler.getModule(impName) match {
                        case Some(module) =>
                          (
                            imp,
                            Some(
                              BindingsMap.ResolvedImport(
                                imp,
                                exp,
                                ModuleReference.Concrete(module)
                              )
                            )
                          )
                        case None =>
                          (
                            IR.Error.ImportExport(
                              imp,
                              IR.Error.ImportExport.ModuleDoesNotExist(impName)
                            ),
                            None
                          )
                      }
                    case Left(loadingError) =>
                      (
                        IR.Error.ImportExport(
                          imp,
                          IR.Error.ImportExport.PackageCouldNotBeLoaded(
                            impName,
                            loadingError.toString
                          )
                        ),
                        None
                      )
                  }
                case other => (other, None)
              }
            currentLocal.resolvedImports = importedModules.flatMap(_._2)
            val newIr = ir.copy(imports = importedModules.map(_._1))
            current.unsafeSetIr(newIr)
            current.unsafeSetCompilationStage(
              Module.CompilationStage.AFTER_IMPORT_RESOLUTION
            )
          }
          // continue with updated stack
          go(
            stack.pushAll(currentLocal.resolvedImports.map(x => {
              x.module match {
                case ModuleReference.Concrete(module) => module
                case ModuleReference.Abstract(name) =>
                  throw new CompilerError(
                    s"Abstract reference to module $name found in ImportResolver."
                  )
              }
            })),
            seen += current
          )
        }
      }
    }

    go(mutable.Stack(module), mutable.Set())
  }

}
