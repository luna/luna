package org.enso.interpreter.instrument.job

import cats.implicits._
import org.enso.interpreter.instrument.{InstrumentFrame, Visualisation}
import org.enso.interpreter.instrument.execution.{Executable, RuntimeContext}
import org.enso.interpreter.instrument.job.UpsertVisualisationJob.{
  EvalFailure,
  EvaluationFailed,
  ModuleNotFound
}
import org.enso.polyglot.runtime.Runtime.Api.{
  ExpressionId,
  RequestId,
  VisualisationId
}
import org.enso.polyglot.runtime.Runtime.{Api, ApiResponse}

/** A job that upserts a visualisation.
  *
  * @param requestId maybe a request id
  * @param visualisationId an identifier of visualisation
  * @param expressionId an identifier of expression
  * @param config a visualisation config
  * @param response a response used to reply to a client
  */
class UpsertVisualisationJob(
  requestId: Option[RequestId],
  visualisationId: VisualisationId,
  expressionId: ExpressionId,
  config: Api.VisualisationConfiguration,
  response: ApiResponse
) extends Job[Option[Executable]](
      List(config.executionContextId),
      true,
      false
    ) {

  /** @inheritdoc */
  override def run(implicit ctx: RuntimeContext): Option[Executable] = {
    ctx.locking.acquireContextLock(config.executionContextId)
    ctx.locking.acquireWriteCompilationLock()
    try {
      val maybeCallable =
        evaluateExpression(config.visualisationModule, config.expression)

      maybeCallable match {
        case Left(ModuleNotFound) =>
          replyWithModuleNotFoundError()
          None

        case Left(EvaluationFailed(message, result)) =>
          replyWithExpressionFailedError(message, result)
          None

        case Right(callable) =>
          val visualisation = updateVisualisation(callable)
          ctx.endpoint.sendToClient(Api.Response(requestId, response))
          val stack = ctx.contextManager.getStack(config.executionContextId)
          val cachedValue = stack.headOption
            .flatMap(frame => Option(frame.cache.get(expressionId)))
          cachedValue match {
            case Some(value) =>
              ProgramExecutionSupport.sendVisualisationUpdate(
                config.executionContextId,
                stack.headOption.get.syncState,
                visualisation,
                expressionId,
                value
              )
              None
            case None =>
              val stack = ctx.contextManager.getStack(config.executionContextId)
              requireVisualisationSynchronization(stack, expressionId)
              Some(Executable(config.executionContextId, stack))
          }
      }
    } finally {
      ctx.locking.releaseWriteCompilationLock()
      ctx.locking.releaseContextLock(config.executionContextId)
    }
  }

  private def requireVisualisationSynchronization(
    stack: Iterable[InstrumentFrame],
    expressionId: ExpressionId
  ): Unit = {
    stack.foreach(_.syncState.setVisualisationUnsync(expressionId))
  }

  private def updateVisualisation(
    callable: AnyRef
  )(implicit ctx: RuntimeContext): Visualisation = {
    val visualisation = Visualisation(
      visualisationId,
      expressionId,
      callable
    )
    ctx.contextManager.upsertVisualisation(
      config.executionContextId,
      visualisation
    )
    visualisation
  }

  private def replyWithExpressionFailedError(
    message: String,
    executionResult: Option[Api.ExecutionResult.Diagnostic]
  )(implicit ctx: RuntimeContext): Unit = {
    ctx.endpoint.sendToClient(
      Api.Response(
        requestId,
        Api.VisualisationExpressionFailed(message, executionResult)
      )
    )
  }

  private def replyWithModuleNotFoundError()(implicit
    ctx: RuntimeContext
  ): Unit = {
    ctx.endpoint.sendToClient(
      Api.Response(
        requestId,
        Api.ModuleNotFound(config.visualisationModule)
      )
    )
  }

  private def evaluateExpression(
    moduleName: String,
    expression: String
  )(implicit ctx: RuntimeContext): Either[EvalFailure, AnyRef] = {
    val context = ctx.executionService.getContext
    // TODO [RW] more specific error when the module cannot be installed (#1861)
    context.ensureModuleIsLoaded(moduleName)
    val maybeModule = context.findModule(moduleName)

    val notFoundOrModule =
      if (maybeModule.isPresent) Right(maybeModule.get())
      else Left(ModuleNotFound)

    notFoundOrModule.flatMap { module =>
      Either
        .catchNonFatal {
          ctx.executionService.evaluateExpression(module, expression)
        }
        .leftMap { error =>
          EvaluationFailed(
            error.getMessage,
            ProgramExecutionSupport.getDiagnosticOutcome.lift(error)
          )
        }
    }

  }

}

object UpsertVisualisationJob {

  /** Base trait for evaluation failures.
    */
  sealed trait EvalFailure

  /** Signals that a module cannot be found.
    */
  case object ModuleNotFound extends EvalFailure

  /** Signals that an evaluation of an expression failed.
    *
    * @param message the textual reason of a failure
    * @param failure the error description
    */
  case class EvaluationFailed(
    message: String,
    failure: Option[Api.ExecutionResult.Diagnostic]
  ) extends EvalFailure

}
