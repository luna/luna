package org.enso.languageserver.libraries.handler

import akka.actor.{Actor, ActorRef, Cancellable, Props, Status}
import com.typesafe.scalalogging.LazyLogging
import org.enso.jsonrpc._
import org.enso.languageserver.filemanager.FileManagerApi.FileSystemError
import org.enso.languageserver.libraries.LibraryApi._
import org.enso.languageserver.libraries.LocalLibraryManagerProtocol
import org.enso.languageserver.requesthandler.RequestTimeout
import org.enso.languageserver.util.UnhandledLogging

import scala.concurrent.duration.FiniteDuration

/** A request handler for the `library/listLocal` endpoint.
  *
  * @param timeout request timeout
  * @param localLibraryManager a reference to the LocalLibraryManager
  */
class LibraryListLocalHandler(
  timeout: FiniteDuration,
  localLibraryManager: ActorRef
) extends Actor
    with LazyLogging
    with UnhandledLogging {
  import context.dispatcher

  override def receive: Receive = requestStage

  private def requestStage: Receive = { case Request(LibraryListLocal, id, _) =>
    localLibraryManager ! LocalLibraryManagerProtocol.ListLocalLibraries
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

    case LocalLibraryManagerProtocol.ListLocalLibrariesResponse(libraries) =>
      replyTo ! ResponseResult(
        LibraryListLocal,
        id,
        LibraryListLocal.Result(libraries)
      )
      cancellable.cancel()
      context.stop(self)

    case Status.Failure(exception) =>
      replyTo ! ResponseError(Some(id), FileSystemError(exception.getMessage))
      cancellable.cancel()
      context.stop(self)
  }
}
object LibraryListLocalHandler {

  /** Creates a configuration object to create [[LibraryListLocalHandler]].
    *
    * @param timeout request timeout
    * @param localLibraryManager a reference to the LocalLibraryManager
    */
  def props(timeout: FiniteDuration, localLibraryManager: ActorRef): Props =
    Props(
      new LibraryListLocalHandler(timeout, localLibraryManager)
    )
}
