package org.enso.languageserver.runtime

import akka.actor.{Actor, ActorLogging, ActorRef, Props}
import akka.pattern.pipe
import org.enso.languageserver.runtime.ContextRegistryProtocol.{
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

/**
  * EventListener listens event stream for the notifications from the runtime
  * and send updates to the client. The listener is created per context, and
  * only handles the notifications with the given `contextId`.
  *
  * @param repo the suggestions repo
  * @param rpcSession reference to the client
  * @param contextId exectuion context identifier
  * @param sessionRouter the session router
  * @param updatesSendRate how often send the updates to the user
  */
final class ContextEventsListener(
  repo: SuggestionsRepo[Future],
  rpcSession: JsonSession,
  contextId: ContextId,
  sessionRouter: ActorRef,
  updatesSendRate: FiniteDuration = 1.second
) extends Actor
    with ActorLogging
    with UnhandledLogging {

  import context.dispatcher

  override def preStart(): Unit = {
    context.system.scheduler.scheduleWithFixedDelay(
      updatesSendRate,
      updatesSendRate,
      self,
      ContextEventsListener.RunBatch
    )
  }

  override def receive: Receive = withState(Vector())

  def withState(
    expressionUpdates: Vector[Api.ExpressionValueUpdate]
  ): Receive = {
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

    case Api.ExpressionValuesComputed(`contextId`, apiUpdates) =>
      context.become(withState(expressionUpdates :++ apiUpdates))

    case Api.ExecutionFailed(`contextId`, msg) =>
      val payload =
        ContextRegistryProtocol.ExecutionFailedNotification(contextId, msg)

      sessionRouter ! DeliverToJsonController(rpcSession.clientId, payload)

    case Api.VisualisationEvaluationFailed(`contextId`, msg) =>
      val payload =
        ContextRegistryProtocol.VisualisationEvaluationFailed(contextId, msg)

      sessionRouter ! DeliverToBinaryController(rpcSession.clientId, payload)

    case ContextEventsListener.RunBatch =>
      val updateIds = expressionUpdates.map(_.expressionId)
      repo
        .getAllByExternalIds(updateIds)
        .map { suggestionIds =>
          val valueUpdates = updateIds.zip(suggestionIds).flatMap {
            case (_, Some(suggestionId)) =>
              Some(ExpressionValueUpdate(suggestionId))
            case (id, None) =>
              log.error("Unable to find suggestion with expression id: {}", id)
              None
          }
          val payload =
            ContextRegistryProtocol.ExpressionValuesComputedNotification(
              contextId,
              valueUpdates
            )
          DeliverToJsonController(rpcSession.clientId, payload)
        }
        .pipeTo(sessionRouter)
      context.become(withState(Vector()))
  }
}

object ContextEventsListener {

  /** The action to run the batch. */
  private case object RunBatch

  /**
    * Creates a configuration object used to create a [[ContextEventsListener]].
    *
    * @param repo the suggestions repo
    * @param rpcSession reference to the client
    * @param contextId exectuion context identifier
    * @param sessionRouter the session router
    */
  def props(
    repo: SuggestionsRepo[Future],
    rpcSession: JsonSession,
    contextId: ContextId,
    sessionRouter: ActorRef
  ): Props =
    Props(
      new ContextEventsListener(
        repo,
        rpcSession,
        contextId,
        sessionRouter: ActorRef
      )
    )

}
