package org.enso.compiler.pass.resolve

import org.enso.compiler.context.{InlineContext, ModuleContext}
import org.enso.compiler.core.IR
import org.enso.compiler.data.BindingsMap
import org.enso.compiler.data.BindingsMap.{ModuleReference, ResolvedModule}
import org.enso.compiler.exception.CompilerError
import org.enso.compiler.pass.IRPass
import org.enso.compiler.pass.analyse.AliasAnalysis

/** Performs a substitution of `this` to `here` in methods defined on the
  * current module.
  *
  * In module-level methods, both names are semantically equivalent, but `here`
  * lends itself to static resolution in later passes and thus better
  * performance characteristics.
  *
  * It also allows the runtime to never pass a valid `this` to such a method
  * (as it is guaranteed to not be used) and therefore perform optimizations,
  * e.g. in method-reexports.
  */
case object ModuleThisToHere extends IRPass {

  /** The type of the metadata object that the pass writes to the IR. */
  override type Metadata = IRPass.Metadata.Empty

  /** The type of configuration for the pass. */
  override type Config = IRPass.Configuration.Default

  /** The passes that this pass depends _directly_ on to run. */
  override val precursorPasses: Seq[IRPass] = Seq(MethodDefinitions)

  /** The passes that are invalidated by running this pass. */
  override val invalidatedPasses: Seq[IRPass] = Seq(AliasAnalysis)

  /** Executes the pass on the provided `ir`, and returns a possibly transformed
    * or annotated version of `ir`.
    *
    * @param ir            the Enso IR to process
    * @param moduleContext a context object that contains the information needed
    *                      to process a module
    * @return `ir`, possibly having made transformations or annotations to that
    *         IR.
    */
  override def runModule(
    ir: IR.Module,
    moduleContext: ModuleContext
  ): IR.Module = {
    val localResolution = BindingsMap.Resolution(
      ResolvedModule(ModuleReference.Concrete(moduleContext.module))
    )
    val newBindings = ir.bindings.map {
      case m: IR.Module.Scope.Definition.Method =>
        if (
          m.methodReference.typePointer
            .getMetadata(MethodDefinitions)
            .contains(localResolution)
        ) {
          val result = m.body.transformExpressions {
            case IR.Name.This(loc, _, _) => IR.Name.Here(loc)
          }

          m match {
            case m: IR.Module.Scope.Definition.Method.Explicit =>
              m.copy(body = result)
            case m: IR.Module.Scope.Definition.Method.Conversion =>
              m.copy(body = result)
            case _ =>
              throw new CompilerError(
                "Impossible method type during `ModuleThisToHere`."
              )
          }
        } else m
      case other => other
    }
    ir.copy(bindings = newBindings)
  }

  /** Executes the pass on the provided `ir`, and returns a possibly transformed
    * or annotated version of `ir` in an inline context.
    *
    * @param ir            the Enso IR to process
    * @param inlineContext a context object that contains the information needed
    *                      for inline evaluation
    * @return `ir`, possibly having made transformations or annotations to that
    *         IR.
    */
  override def runExpression(
    ir: IR.Expression,
    inlineContext: InlineContext
  ): IR.Expression = ir
}
