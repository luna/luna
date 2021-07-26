package org.enso.languageserver.libraries.handler

import akka.actor.{Actor, Props}
import com.typesafe.scalalogging.LazyLogging
import org.enso.cli.task.notifications.ActorProgressNotificationForwarder
import org.enso.jsonrpc.{Request, ResponseError}
import org.enso.languageserver.filemanager.FileManagerApi.FileSystemError
import org.enso.languageserver.libraries.FakeDownload
import org.enso.languageserver.libraries.LibraryApi._
import org.enso.languageserver.util.UnhandledLogging

/** A request handler for the `library/preinstall` endpoint.
  *
  * It is currently a stub implementation which will be refined later on.
  */
class LibraryPreinstallHandler
    extends Actor
    with LazyLogging
    with UnhandledLogging {
  override def receive: Receive = {
    case Request(LibraryPreinstall, id, LibraryPreinstall.Params(_, name)) =>
      // TODO [RW] actual implementation
      val progressReporter =
        ActorProgressNotificationForwarder.translateAndForward(
          LibraryPreinstall.name,
          sender()
        )

      if (name == "Test") {
        FakeDownload.simulateDownload(
          "Download Test",
          progressReporter,
          seconds = 1
        )
      } else {
        FakeDownload.simulateDownload(
          "Downloading something...",
          progressReporter
        )
        FakeDownload.simulateDownload(
          "Downloading something else...",
          progressReporter
        )
      }
      sender() ! ResponseError(
        Some(id),
        FileSystemError("Feature not implemented")
      )
  }
}

object LibraryPreinstallHandler {

  /** Creates a configuration object to create [[LibraryPreinstallHandler]]. */
  def props(): Props = Props(new LibraryPreinstallHandler)
}
