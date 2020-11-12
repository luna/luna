package org.enso.projectmanager.requesthandler

import akka.actor.{Actor, ActorLogging, ActorRef, Cancellable, Stash, Status}
import akka.pattern.pipe
import org.enso.jsonrpc.Errors.ServiceError
import org.enso.jsonrpc.{Id, Method, Request, ResponseError}
import org.enso.projectmanager.control.effect.Exec
import org.enso.projectmanager.service.versionmanagement.ProgressNotification
import org.enso.projectmanager.service.versionmanagement.ProgressNotification.translateProgressNotification
import org.enso.projectmanager.util.UnhandledLogging

import scala.concurrent.duration.FiniteDuration

/** A helper class that gathers common request handling logic.
  *
  * It manages timeouts and sending the request result (in case of success but
  * also failure or timeout).
  *
  * @param handledMethod method that this handler deals with; used in logging
  *                      and to relate progress updates to the method
  * @param requestTimeout timeout for the request; if set, the request will be
  *                       marked as failed after the specified time; the request
  *                       logic is however NOT cancelled as this is not possible
  *                       to do in a general way
  */
abstract class RequestHandler[
  F[+_, +_]: Exec,
  FailureType: FailureMapper: Manifest
](
  handledMethod: Method,
  requestTimeout: Option[FiniteDuration]
) extends Actor
    with ActorLogging
    with Stash
    with UnhandledLogging {
  override def receive: Receive = requestStage

  import context.dispatcher

  /** Waits for the request, tries to pass it into the [[handleRequest]]
    * function, sets up the timeout and routing of the result.
    */
  private def requestStage: Receive = {
    val composition: Any => Option[Unit] = {
      case request @ Request(_, id, _) =>
        val result = handleRequest.lift(request)
        result.map { f =>
          Exec[F].exec(f).pipeTo(self)
          val cancellable = {
            requestTimeout.map { timeout =>
              context.system.scheduler.scheduleOnce(
                timeout,
                self,
                RequestTimeout
              )
            }
          }
          context.become(responseStage(id, sender(), cancellable))
        }
      case _ => None
    }
    Function.unlift(composition)
  }

  /** Defines the actual logic for handling the request.
    *
    * The partial function should only be defined by requests that are meant to
    * be handled by this instance.
    */
  def handleRequest: PartialFunction[Any, F[FailureType, Any]]

  /** Waits for the routed result or a failure/timeout and reports the result to
    * the user.
    */
  private def responseStage(
    id: Id,
    replyTo: ActorRef,
    cancellable: Option[Cancellable]
  ): Receive = {
    case Status.Failure(ex) =>
      log.error(ex, s"Failure during $handledMethod operation:")
      replyTo ! ResponseError(Some(id), ServiceError)
      cancellable.foreach(_.cancel())
      context.stop(self)

    case RequestTimeout =>
      log.error(s"Request $handledMethod with $id timed out")
      replyTo ! ResponseError(Some(id), ServiceError)
      context.stop(self)

    case Left(failure: FailureType) =>
      log.error(s"Request $id failed due to $failure")
      val error = implicitly[FailureMapper[FailureType]].mapFailure(failure)
      replyTo ! ResponseError(Some(id), error)
      cancellable.foreach(_.cancel())
      context.stop(self)

    case Right(response) =>
      replyTo ! response
      cancellable.foreach(_.cancel())
      context.stop(self)

    case notification: ProgressNotification =>
      replyTo ! translateProgressNotification(handledMethod.name, notification)
  }
}
