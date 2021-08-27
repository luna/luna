package org.enso.compiler.pass.resolve

import org.enso.compiler.context.{FreshNameSupply, InlineContext, ModuleContext}
import org.enso.compiler.core.IR
import org.enso.compiler.core.ir.MetadataStorage.ToPair
import org.enso.compiler.data.BindingsMap
import org.enso.compiler.data.BindingsMap.{
  ModuleReference,
  Resolution,
  ResolvedConstructor,
  ResolvedMethod
}
import org.enso.compiler.exception.CompilerError
import org.enso.compiler.pass.IRPass
import org.enso.compiler.pass.analyse.{AliasAnalysis, BindingAnalysis}
import org.enso.interpreter.Constants

/** Resolves and desugars referent name occurences in non-pattern contexts.
  *
  * 1. Attaches resolution metadata to encountered constructors, modules,
  *    and polygot symbols.
  * 2. Desugars encountered method references into proper applications.
  * 3. Resolves qualified calls to constructors, i.e. a call of the form
  *    `KnownModule.consName a b c` is transformed into `KnownCons a b c`,
  *    if `consName` refers to a constructor and `KnownModule` was successfully
  *    resolved to a module.
  */
case object UppercaseNames extends IRPass {

  /** The type of the metadata object that the pass writes to the IR. */
  override type Metadata = BindingsMap.Resolution

  /** The type of configuration for the pass. */
  override type Config = IRPass.Configuration.Default

  /** The passes that this pass depends _directly_ on to run. */
  override val precursorPasses: Seq[IRPass] =
    Seq(AliasAnalysis, BindingAnalysis)

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
    val scopeMap = ir.unsafeGetMetadata(
      BindingAnalysis,
      "No binding analysis on the module"
    )
    val freshNameSupply = moduleContext.freshNameSupply.getOrElse(
      throw new CompilerError(
        "No fresh name supply passed to UppercaseNames resolver."
      )
    )
    val new_bindings =
      ir.bindings.map(processModuleDefinition(_, scopeMap, freshNameSupply))
    ir.copy(bindings = new_bindings)
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
  ): IR.Expression = {
    val scopeMap = inlineContext.module.getIr.unsafeGetMetadata(
      BindingAnalysis,
      "No binding analysis on the module"
    )
    val freshNameSupply = inlineContext.freshNameSupply.getOrElse(
      throw new CompilerError(
        "No fresh name supply passed to UppercaseNames resolver."
      )
    )
    processExpression(ir, scopeMap, freshNameSupply)
  }

  private def processModuleDefinition(
    definition: IR.Module.Scope.Definition,
    bindings: BindingsMap,
    freshNameSupply: FreshNameSupply
  ): IR.Module.Scope.Definition = {
    definition match {
      case asc: IR.Type.Ascription => asc
      case a =>
        a.mapExpressions(processExpression(_, bindings, freshNameSupply))
    }
  }

  private def processExpression(
    ir: IR.Expression,
    bindings: BindingsMap,
    freshNameSupply: FreshNameSupply,
    isInsideApplication: Boolean = false
  ): IR.Expression =
    ir.transformExpressions {
      case lit: IR.Name.Literal =>
        if (lit.isReferent && !isLocalVar(lit)) {
          val resolution = bindings.resolveUppercaseName(lit.name)
          resolution match {
            case Left(error) =>
              IR.Error.Resolution(
                lit,
                IR.Error.Resolution.ResolverError(error)
              )
            case Right(r @ BindingsMap.ResolvedMethod(mod, method)) =>
              if (isInsideApplication) {
                lit.updateMetadata(this -->> BindingsMap.Resolution(r))

              } else {
                val self = freshNameSupply
                  .newName(isReferent = true)
                  .updateMetadata(
                    this -->> BindingsMap.Resolution(
                      BindingsMap.ResolvedModule(mod)
                    )
                  )
                val fun = lit.copy(name = method.name)
                val app = IR.Application.Prefix(
                  fun,
                  List(IR.CallArgument.Specified(None, self, None)),
                  hasDefaultsSuspended = false,
                  None
                )
                app
              }
            case Right(value) =>
              lit.updateMetadata(this -->> BindingsMap.Resolution(value))
          }

        } else { lit }
      case app: IR.Application.Prefix =>
        app.function match {
          case n: IR.Name.Literal =>
            if (n.isReferent)
              resolveReferantApplication(app, bindings, freshNameSupply)
            else resolveLocalApplication(app, bindings, freshNameSupply)
          case _ =>
            app.mapExpressions(processExpression(_, bindings, freshNameSupply))

        }

    }

  private def resolveReferantApplication(
    app: IR.Application.Prefix,
    bindingsMap: BindingsMap,
    freshNameSupply: FreshNameSupply
  ): IR.Expression = {
    val processedFun = processExpression(
      app.function,
      bindingsMap,
      freshNameSupply,
      isInsideApplication = true
    )
    val processedArgs = app.arguments.map(
      _.mapExpressions(processExpression(_, bindingsMap, freshNameSupply))
    )
    processedFun.getMetadata(this) match {
      case Some(Resolution(ResolvedMethod(mod, method))) =>
        val self = freshNameSupply
          .newName(isReferent = true)
          .updateMetadata(
            this -->> BindingsMap.Resolution(
              BindingsMap.ResolvedModule(mod)
            )
          )
        val selfArg = IR.CallArgument.Specified(None, self, None)
        processedFun.passData.remove(this)
        val renamed = rename(processedFun, method.name)
        app.copy(function = renamed, arguments = selfArg :: processedArgs)
      case _ => app.copy(function = processedFun, arguments = processedArgs)
    }
  }

  private def rename(name: IR.Expression, newName: String): IR.Expression =
    name match {
      case lit: IR.Name.Literal => lit.copy(name = newName)
      case _                    => name
    }

  private def resolveLocalApplication(
    app: IR.Application.Prefix,
    bindings: BindingsMap,
    freshNameSupply: FreshNameSupply
  ): IR.Expression = {
    val processedFun =
      processExpression(app.function, bindings, freshNameSupply)
    val processedArgs =
      app.arguments.map(
        _.mapExpressions(processExpression(_, bindings, freshNameSupply))
      )
    val newApp: Option[IR.Expression] = for {
      thisArgPos <- findThisPosition(processedArgs)
      thisArg = processedArgs(thisArgPos)
      thisArgResolution <- thisArg.value.getMetadata(this)
      funAsVar          <- asGlobalVar(processedFun)
      cons              <- resolveToCons(thisArgResolution, funAsVar)
      newFun =
        buildSymbolFor(cons, freshNameSupply).setLocation(funAsVar.location)
      newArgs = processedArgs.patch(thisArgPos, Nil, 1)
    } yield buildConsApplication(app, cons.cons, newFun, newArgs)
    newApp.getOrElse(
      app.copy(function = processedFun, arguments = processedArgs)
    )
  }

  private def buildConsApplication(
    originalApp: IR.Application.Prefix,
    calledCons: BindingsMap.Cons,
    newFun: IR.Expression,
    newArgs: List[IR.CallArgument]
  ): IR.Expression = {
    if (
      newArgs.isEmpty && (!originalApp.hasDefaultsSuspended || calledCons.arity == 0)
    ) {
      newFun
    } else {
      originalApp.copy(function = newFun, arguments = newArgs)
    }
  }

  private def buildSymbolFor(
    cons: BindingsMap.ResolvedConstructor,
    freshNameSupply: FreshNameSupply
  ): IR.Expression = {
    freshNameSupply
      .newName(isReferent = true)
      .updateMetadata(this -->> BindingsMap.Resolution(cons))
  }

  private def resolveToCons(
    thisResolution: BindingsMap.Resolution,
    consName: IR.Name.Literal
  ): Option[BindingsMap.ResolvedConstructor] =
    thisResolution.target match {
      case BindingsMap.ResolvedModule(module) =>
        module match {
          case ModuleReference.Concrete(module) =>
            val resolution = module.getIr
              .unsafeGetMetadata(
                BindingAnalysis,
                "Imported module without bindings analysis results"
              )
              .resolveExportedName(consName.name)
            resolution match {
              case Right(cons @ ResolvedConstructor(_, _)) => Some(cons)
              case _                                       => None
            }
          case ModuleReference.Abstract(name) =>
            throw new CompilerError(
              s"Abstract reference to module $name found during " +
              s"UppercaseNames resolution."
            )
        }
      case _ => None
    }

  private def findThisPosition(args: List[IR.CallArgument]): Option[Int] = {
    val ix = args.indexWhere(arg =>
      arg.name.exists(
        _.name == Constants.Names.THIS_ARGUMENT
      ) || arg.name.isEmpty
    )
    if (ix == -1) None else Some(ix)
  }

  private def asGlobalVar(ir: IR): Option[IR.Name.Literal] =
    ir match {
      case name: IR.Name.Literal =>
        if (isLocalVar(name) || name.isReferent) None else Some(name)
      case _ => None
    }

  private def isLocalVar(name: IR.Name.Literal): Boolean = {
    val aliasInfo = name
      .unsafeGetMetadata(
        AliasAnalysis,
        "no alias analysis info on a name"
      )
      .unsafeAs[AliasAnalysis.Info.Occurrence]
    val defLink = aliasInfo.graph.defLinkFor(aliasInfo.id)
    defLink.isDefined
  }
}
