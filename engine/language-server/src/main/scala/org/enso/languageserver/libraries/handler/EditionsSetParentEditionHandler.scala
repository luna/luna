package org.enso.languageserver.libraries.handler

import akka.actor.{Actor, ActorRef, Cancellable, Props, Status}
import com.typesafe.scalalogging.LazyLogging
import org.enso.jsonrpc._
import org.enso.languageserver.filemanager.FileManagerApi.FileSystemError
import org.enso.languageserver.libraries.LibraryApi._
import org.enso.languageserver.libraries.ProjectSettingsManager
import org.enso.languageserver.libraries.ProjectSettingsManager.SettingsUpdated
import org.enso.languageserver.requesthandler.RequestTimeout
import org.enso.languageserver.util.UnhandledLogging

import scala.concurrent.duration.FiniteDuration

/** A request handler for the `editions/setParentEdition` endpoint.
  *
  * @param timeout request timeout
  * @param projectSettingsManager a reference to the [[ProjectSettingsManager]]
  */
class EditionsSetParentEditionHandler(
  timeout: FiniteDuration,
  projectSettingsManager: ActorRef
) extends Actor
    with LazyLogging
    with UnhandledLogging {

  import context.dispatcher

  override def receive: Receive = requestStage

  private def requestStage: Receive = {
    case Request(
          EditionsSetParentEdition,
          id,
          EditionsSetParentEdition.Params(newEditionName)
        ) =>
      projectSettingsManager ! ProjectSettingsManager.SetParentEdition(
        newEditionName
      )
      val cancellable =
        context.system.scheduler.scheduleOnce(timeout, self, RequestTimeout)
      context.become(responseStage(id, sender(), cancellable))
  }

  private def responseStage(
    id: Id,
    replyTo: ActorRef,
    cancellable: Cancellable
  ): Receive = {
    case RequestTimeout =>
      logger.error("Request [{}] timed out.", id)
      replyTo ! ResponseError(Some(id), Errors.RequestTimeout)
      context.stop(self)

    case SettingsUpdated() =>
      replyTo ! ResponseResult(
        EditionsSetParentEdition,
        id,
        EditionsSetParentEdition.Result(needsRestart = Some(true))
      )
      cancellable.cancel()
      context.stop(self)

    case Status.Failure(exception) =>
      replyTo ! ResponseError(
        Some(id),
        FileSystemError(
          s"Failed to update the settings: ${exception.getMessage}"
        )
      )
      cancellable.cancel()
      context.stop(self)
  }
}

object EditionsSetParentEditionHandler {

  /** Creates a configuration object to create
    * [[EditionsSetParentEditionHandler]].
    *
    * @param timeout request timeout
    * @param projectSettingsManager a reference to the
    *                               [[ProjectSettingsManager]]
    */
  def props(timeout: FiniteDuration, projectSettingsManager: ActorRef): Props =
    Props(
      new EditionsSetParentEditionHandler(timeout, projectSettingsManager)
    )
}
