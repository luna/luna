package org.enso.languageserver.libraries.handler

import akka.actor.{Actor, ActorRef, Cancellable, Props}
import com.typesafe.scalalogging.LazyLogging
import org.enso.cli.task.notifications.ActorProgressNotificationForwarder
import org.enso.editions.LibraryName
import org.enso.jsonrpc.{Id, Request, ResponseError, ResponseResult, Unused}
import org.enso.languageserver.filemanager.FileManagerApi.FileSystemError
import org.enso.languageserver.libraries.LibraryApi._
import org.enso.languageserver.libraries.LocalLibraryManagerProtocol.{
  FindLibrary,
  FindLibraryResponse
}
import org.enso.languageserver.requesthandler.RequestTimeout
import org.enso.languageserver.util.UnhandledLogging
import org.enso.libraryupload.{auth, LibraryUploader}

import scala.concurrent.duration.FiniteDuration
import scala.util.{Failure, Success}

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
      replyTo ! RequestTimeout
      context.stop(self)

    case Success(FindLibraryResponse(Some(libraryRoot))) =>
      val progressReporter =
        ActorProgressNotificationForwarder.translateAndForward(
          LibraryPublish.name,
          replyTo
        )

      val result = LibraryUploader.uploadLibrary(
        libraryRoot,
        uploadUrl,
        token,
        progressReporter
      )

      result match {
        case Failure(exception) =>
          replyTo ! ResponseError(
            Some(id),
            FileSystemError(s"Upload failed: $exception")
          )
        case Success(_) =>
          if (shouldBumpAfterPublishing) {
            logger.warn(
              "`bumpVersionAfterPublish` was set to true, but this feature " +
              "is not currently implemented. Ignoring."
            )
          }
          replyTo ! ResponseResult(LibraryPublish, id, Unused)
      }

      stop(timeoutCancellable)

    case Success(FindLibraryResponse(None)) =>
      replyTo ! ResponseError(
        Some(id),
        FileSystemError(
          s"The library [$libraryName] was not found in local libraries " +
          s"search paths."
        )
      )

      stop(timeoutCancellable)

    case Failure(exception) =>
      replyTo ! ResponseError(
        Some(id),
        FileSystemError(
          s"Failed to find the requested local library: $exception"
        )
      )

      stop(timeoutCancellable)
  }
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
