package org.enso.languageserver.runtime

import akka.actor.{Actor, ActorLogging, ActorRef, Props}
import akka.pattern.pipe
import org.enso.languageserver.data.Config
import org.enso.languageserver.runtime.ContextRegistryProtocol.{
  ExecutionDiagnostic,
  ExecutionDiagnosticKind,
  ExecutionFailure,
  ExecutionStackTraceElement,
  VisualisationContext,
  VisualisationUpdate
}
import org.enso.languageserver.runtime.ExecutionApi.ContextId
import org.enso.languageserver.session.JsonSession
import org.enso.languageserver.session.SessionRouter.{
  DeliverToBinaryController,
  DeliverToJsonController
}
import org.enso.languageserver.util.UnhandledLogging
import org.enso.polyglot.runtime.Runtime.Api
import org.enso.searcher.SuggestionsRepo

import scala.concurrent.Future
import scala.concurrent.duration._

/** EventListener listens event stream for the notifications from the runtime
  * and send updates to the client. The listener is created per context, and
  * only handles the notifications with the given `contextId`.
  *
  * Expression updates are collected and sent to the user in a batch.
  *
  * @param config the language server configuration
  * @param repo the suggestions repo
  * @param rpcSession reference to the client
  * @param contextId exectuion context identifier
  * @param sessionRouter the session router
  * @param updatesSendRate how often send the updates to the user
  */
final class ContextEventsListener(
  config: Config,
  repo: SuggestionsRepo[Future],
  rpcSession: JsonSession,
  contextId: ContextId,
  sessionRouter: ActorRef,
  updatesSendRate: FiniteDuration
) extends Actor
    with ActorLogging
    with UnhandledLogging {

  import ContextEventsListener.RunExpressionUpdates
  import context.dispatcher

  override def preStart(): Unit = {
    if (updatesSendRate.length > 0) {
      context.system.scheduler.scheduleWithFixedDelay(
        updatesSendRate,
        updatesSendRate,
        self,
        RunExpressionUpdates
      )
    }
  }

  override def receive: Receive = withState(Vector())

  def withState(expressionUpdates: Vector[Api.ExpressionUpdate]): Receive = {
    case Api.VisualisationUpdate(ctx, data) if ctx.contextId == contextId =>
      val payload =
        VisualisationUpdate(
          VisualisationContext(
            ctx.visualisationId,
            ctx.contextId,
            ctx.expressionId
          ),
          data
        )
      sessionRouter ! DeliverToBinaryController(rpcSession.clientId, payload)

    case Api.ExpressionUpdates(`contextId`, apiUpdates) =>
      context.become(
        withState(expressionUpdates :++ apiUpdates)
      )

    case Api.ExecutionFailed(`contextId`, error) =>
      val payload =
        ContextRegistryProtocol.ExecutionFailedNotification(
          contextId,
          toProtocolError(error)
        )
      sessionRouter ! DeliverToJsonController(rpcSession.clientId, payload)

    case Api.ExecutionUpdate(`contextId`, diagnostics) =>
      val payload =
        ContextRegistryProtocol.ExecutionDiagnosticNotification(
          contextId,
          diagnostics.map(toProtocolDiagnostic)
        )
      sessionRouter ! DeliverToJsonController(rpcSession.clientId, payload)

    case Api.VisualisationEvaluationFailed(`contextId`, msg) =>
      val payload =
        ContextRegistryProtocol.VisualisationEvaluationFailed(contextId, msg)
      sessionRouter ! DeliverToJsonController(rpcSession.clientId, payload)

    case RunExpressionUpdates if expressionUpdates.nonEmpty =>
      val updates = Vector.newBuilder[ContextRegistryProtocol.ExpressionUpdate]
      val computedExpressions =
        Vector.newBuilder[Api.ExpressionUpdate.ExpressionComputed]
      expressionUpdates.foreach {
        case m: Api.ExpressionUpdate.ExpressionComputed =>
          computedExpressions += m
        case m: Api.ExpressionUpdate.ExpressionFailed =>
          updates += ContextRegistryProtocol.ExpressionUpdate
            .ExpressionFailed(m.expressionId, m.message)
        case m: Api.ExpressionUpdate.ExpressionPoisoned =>
          updates += ContextRegistryProtocol.ExpressionUpdate
            .ExpressionPoisoned(m.expressionId, m.failedExpressionId)
      }
      val notification = ContextRegistryProtocol.ExpressionUpdatesNotification(
        contextId,
        updates.result(),
        None
      )
      if (notification.updates.nonEmpty) {
        sessionRouter ! DeliverToJsonController(
          rpcSession.clientId,
          notification
        )
      }
      runExpressionComputed(computedExpressions.result())
      context.become(withState(Vector()))

    case RunExpressionUpdates if expressionUpdates.isEmpty =>
  }

  /** Process `ExpressionComputed` notifications.
    *
    * Function resolves method pointers to the corresponding suggestion ids in
    * the suggestions database, and creates the API updates.
    */
  private def runExpressionComputed(
    expressionUpdates: Vector[Api.ExpressionUpdate.ExpressionComputed]
  ): Unit = {
    def toMethodPointer(call: Api.MethodPointer): (String, String, String) =
      (call.module, call.definedOnType, call.name)

    val methodPointerToExpression =
      expressionUpdates.flatMap { update =>
        update.methodCall.map(call =>
          toMethodPointer(call) -> update.expressionId
        )
      }.toMap
    val methodPointers = methodPointerToExpression.keys.toSeq
    repo
      .getAllMethods(methodPointers)
      .map { suggestionIds =>
        val methodPointerToSuggestion =
          methodPointers
            .zip(suggestionIds)
            .collect { case (ptr, Some(suggestionId)) =>
              ptr -> suggestionId
            }
            .toMap
        val computedExpressions = expressionUpdates.map { update =>
          ContextRegistryProtocol.ExpressionUpdate.ExpressionComputed(
            update.expressionId,
            update.expressionType,
            update.methodCall.flatMap { call =>
              val pointer = toMethodPointer(call)
              methodPointerToSuggestion.get(pointer) match {
                case suggestionId @ Some(_) => suggestionId
                case None =>
                  log.error(s"Unable to find suggestion for $pointer")
                  None
              }
            }
          )
        }
        val payload = ContextRegistryProtocol.ExpressionUpdatesNotification(
          contextId,
          computedExpressions,
          Some(computedExpressions.map(toExpressionValueUpdate))
        )
        DeliverToJsonController(rpcSession.clientId, payload)

      }
      .pipeTo(sessionRouter)
  }

  /** Conversion between the new and the old expression update message. */
  private def toExpressionValueUpdate(
    m: ContextRegistryProtocol.ExpressionUpdate.ExpressionComputed
  ): ExpressionValueUpdate =
    ExpressionValueUpdate(m.expressionId, m.`type`, m.methodPointer)

  /** Convert the runtime failure message to the context registry protocol
    * representation.
    *
    * @param error the error message
    * @return the registry protocol representation fo the diagnostic message
    */
  private def toProtocolError(
    error: Api.ExecutionResult.Failure
  ): ExecutionFailure =
    ExecutionFailure(
      error.message,
      error.file.flatMap(config.findRelativePath)
    )

  /** Convert the runtime diagnostic message to the context registry protocol
    * representation.
    *
    * @param diagnostic the diagnostic message
    * @return the registry protocol representation of the diagnostic message
    */
  private def toProtocolDiagnostic(
    diagnostic: Api.ExecutionResult.Diagnostic
  ): ExecutionDiagnostic =
    ExecutionDiagnostic(
      toDiagnosticType(diagnostic.kind),
      diagnostic.message,
      diagnostic.file.flatMap(config.findRelativePath),
      diagnostic.location,
      diagnostic.expressionId,
      diagnostic.stack.map(toStackTraceElement)
    )

  /** Convert the runtime diagnostic type to the context registry protocol
    * representation.
    *
    * @param kind the diagnostic type
    * @return the registry protocol representation of the diagnostic type
    */
  private def toDiagnosticType(
    kind: Api.DiagnosticType
  ): ExecutionDiagnosticKind =
    kind match {
      case Api.DiagnosticType.Error()   => ExecutionDiagnosticKind.Error
      case Api.DiagnosticType.Warning() => ExecutionDiagnosticKind.Warning
    }

  /** Convert the runtime stack trace element to the context registry protocol
    * representation.
    *
    * @param element the runtime stack trace element
    * @return the registry protocol representation of the stack trace element
    */
  private def toStackTraceElement(
    element: Api.StackTraceElement
  ): ExecutionStackTraceElement =
    ExecutionStackTraceElement(
      element.functionName,
      element.file.flatMap(config.findRelativePath),
      element.location,
      element.expressionId
    )
}

object ContextEventsListener {

  /** The action to process the expression updates. */
  case object RunExpressionUpdates

  /** Creates a configuration object used to create a [[ContextEventsListener]].
    *
    * @param config the language server configuration
    * @param repo the suggestions repo
    * @param rpcSession reference to the client
    * @param contextId exectuion context identifier
    * @param sessionRouter the session router
    * @param updatesSendRate how often send the updates to the user
    */
  def props(
    config: Config,
    repo: SuggestionsRepo[Future],
    rpcSession: JsonSession,
    contextId: ContextId,
    sessionRouter: ActorRef,
    updatesSendRate: FiniteDuration = 1.second
  ): Props =
    Props(
      new ContextEventsListener(
        config,
        repo,
        rpcSession,
        contextId,
        sessionRouter: ActorRef,
        updatesSendRate
      )
    )

}
