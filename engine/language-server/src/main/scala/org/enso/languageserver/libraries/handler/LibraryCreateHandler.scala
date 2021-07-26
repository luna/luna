package org.enso.languageserver.libraries.handler

import akka.actor.{Actor, ActorRef, Cancellable, Props}
import com.typesafe.scalalogging.LazyLogging
import org.enso.editions.LibraryName
import org.enso.jsonrpc._
import org.enso.languageserver.filemanager.FileManagerApi.FileSystemError
import org.enso.languageserver.libraries.LibraryApi._
import org.enso.languageserver.libraries.LocalLibraryManagerProtocol
import org.enso.languageserver.requesthandler.RequestTimeout
import org.enso.languageserver.util.UnhandledLogging

import scala.concurrent.duration.FiniteDuration
import scala.util.{Failure, Success}

/** A request handler for the `library/create` endpoint.
  *
  * @param timeout request timeout
  * @param localLibraryManager a reference to the LocalLibraryManager
  */
class LibraryCreateHandler(
  timeout: FiniteDuration,
  localLibraryManager: ActorRef
) extends Actor
    with LazyLogging
    with UnhandledLogging {
  import context.dispatcher

  override def receive: Receive = requestStage

  private def requestStage: Receive = {
    case Request(
          LibraryCreate,
          id,
          LibraryCreate.Params(namespace, name, authors, maintainers, license)
        ) =>
      localLibraryManager ! LocalLibraryManagerProtocol.Create(
        LibraryName(namespace, name),
        authors     = authors,
        maintainers = maintainers,
        license     = license
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
      replyTo ! RequestTimeout
      context.stop(self)

    case Success(_) =>
      replyTo ! ResponseResult(LibraryCreate, id, Unused)
      cancellable.cancel()
      context.stop(self)

    case Failure(exception) =>
      // TODO [RW] handle LibraryAlreadyExists error
      replyTo ! ResponseError(Some(id), FileSystemError(exception.getMessage))
      cancellable.cancel()
      context.stop(self)
  }
}

object LibraryCreateHandler {

  /** Creates a configuration object to create [[LibraryCreateHandler]].
    *
    * @param timeout request timeout
    * @param localLibraryManager a reference to the LocalLibraryManager
    */
  def props(timeout: FiniteDuration, localLibraryManager: ActorRef): Props =
    Props(
      new LibraryCreateHandler(timeout, localLibraryManager)
    )
}
