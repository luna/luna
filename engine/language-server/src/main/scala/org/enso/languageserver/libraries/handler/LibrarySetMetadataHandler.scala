package org.enso.languageserver.libraries.handler

import akka.actor.{Actor, Props}
import com.typesafe.scalalogging.LazyLogging
import org.enso.jsonrpc.{Request, ResponseError}
import org.enso.languageserver.filemanager.FileManagerApi.FileSystemError
import org.enso.languageserver.libraries.LibraryApi._
import org.enso.languageserver.util.UnhandledLogging

/** A request handler for the `library/setMetadata` endpoint.
  *
  * It is currently a stub implementation which will be refined later on.
  */
class LibrarySetMetadataHandler
    extends Actor
    with LazyLogging
    with UnhandledLogging {
  override def receive: Receive = {
    case Request(LibrarySetMetadata, id, _: LibrarySetMetadata.Params) =>
      // TODO [RW] actual implementation
      sender() ! ResponseError(
        Some(id),
        FileSystemError("Feature not implemented")
      )
  }
}

object LibrarySetMetadataHandler {

  /** Creates a configuration object to create [[LibrarySetMetadataHandler]]. */
  def props(): Props = Props(new LibrarySetMetadataHandler)
}
