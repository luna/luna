package org.enso.languageserver.libraries.handler

import akka.actor.{Actor, ActorRef, Cancellable, Props, Status}
import akka.pattern.pipe
import com.typesafe.scalalogging.LazyLogging
import org.enso.cli.task.notifications.ActorProgressNotificationForwarder
import org.enso.editions.LibraryName
import org.enso.jsonrpc._
import org.enso.languageserver.filemanager.FileManagerApi.FileSystemError
import org.enso.languageserver.libraries.BlockingOperation
import org.enso.languageserver.libraries.LibraryApi._
import org.enso.languageserver.libraries.LocalLibraryManagerProtocol.{
  FindLibrary,
  FindLibraryResponse
}
import org.enso.languageserver.requesthandler.RequestTimeout
import org.enso.languageserver.util.UnhandledLogging
import org.enso.libraryupload.{auth, LibraryUploader}

import scala.concurrent.Future
import scala.concurrent.duration.FiniteDuration

/** A request handler for the `library/publish` endpoint.
  *
  * @param timeout request timeout
  * @param localLibraryManager a reference to the LocalLibraryManager
  */
class LibraryPublishHandler(
  timeout: FiniteDuration,
  localLibraryManager: ActorRef
) extends Actor
    with LazyLogging
    with UnhandledLogging {
  override def receive: Receive = requestStage

  import context.dispatcher

  private def requestStage: Receive = {
    case Request(
          LibraryPublish,
          id,
          LibraryPublish.Params(
            namespace,
            name,
            authToken,
            uploadUrl,
            bumpVersionAfterPublish
          )
        ) =>
      val shouldBump  = bumpVersionAfterPublish.getOrElse(false)
      val replyTo     = sender()
      val token       = auth.SimpleHeaderToken(authToken)
      val libraryName = LibraryName(namespace, name)
      localLibraryManager ! FindLibrary(libraryName)

      val timeoutCancellable =
        context.system.scheduler.scheduleOnce(timeout, self, RequestTimeout)
      context.become(
        waitForLibraryResolutionStage(
          replyTo,
          libraryName,
          id,
          uploadUrl,
          token,
          timeoutCancellable,
          shouldBump
        )
      )
  }

  private def stop(timeoutCancellable: Cancellable): Unit = {
    timeoutCancellable.cancel()
    context.stop(self)
  }

  /** Waits for the response of LocalLibraryManager and continues the publishing
    * process.
    */
  private def waitForLibraryResolutionStage(
    replyTo: ActorRef,
    libraryName: LibraryName,
    id: Id,
    uploadUrl: String,
    token: auth.Token,
    timeoutCancellable: Cancellable,
    shouldBumpAfterPublishing: Boolean
  ): Receive = {
    case RequestTimeout =>
      logger.error("Request [{}] timed out.", id)
      replyTo ! ResponseError(Some(id), Errors.RequestTimeout)
      context.stop(self)

    case FindLibraryResponse(Some(libraryRoot)) =>
      val progressReporter =
        ActorProgressNotificationForwarder.translateAndForward(
          LibraryPublish.name,
          replyTo
        )

      val future: Future[UploadSucceeded] = BlockingOperation.run {
        LibraryUploader
          .uploadLibrary(
            libraryRoot,
            uploadUrl,
            token,
            progressReporter
          )
          .get

        UploadSucceeded()
      }

      future pipeTo self

      context.become(
        waitingForResultStage(
          replyTo,
          timeoutCancellable,
          id,
          shouldBumpAfterPublishing
        )
      )

    case FindLibraryResponse(None) =>
      replyTo ! ResponseError(
        Some(id),
        FileSystemError(
          s"The library [$libraryName] was not found in local libraries " +
          s"search paths."
        )
      )

      stop(timeoutCancellable)

    case Status.Failure(exception) =>
      replyTo ! ResponseError(
        Some(id),
        FileSystemError(
          s"Failed to find the requested local library: $exception"
        )
      )

      stop(timeoutCancellable)
  }

  private def waitingForResultStage(
    replyTo: ActorRef,
    timeoutCancellable: Cancellable,
    id: Id,
    shouldBumpAfterPublishing: Boolean
  ): Receive = {
    case UploadSucceeded() =>
      if (shouldBumpAfterPublishing) {
        logger.warn(
          "`bumpVersionAfterPublish` was set to true, but this feature " +
          "is not currently implemented. Ignoring."
        )
      }

      replyTo ! ResponseResult(LibraryPublish, id, Unused)
      stop(timeoutCancellable)

    case Status.Failure(exception) =>
      replyTo ! ResponseError(
        Some(id),
        FileSystemError(s"Upload failed: $exception")
      )
      stop(timeoutCancellable)
  }

  private case class UploadSucceeded()
}

object LibraryPublishHandler {

  /** Creates a configuration object to create [[LibraryPublishHandler]].
    *
    * @param timeout request timeout
    * @param localLibraryManager a reference to the LocalLibraryManager
    */
  def props(
    timeout: FiniteDuration,
    localLibraryManager: ActorRef
  ): Props = Props(
    new LibraryPublishHandler(
      timeout,
      localLibraryManager
    )
  )
}
