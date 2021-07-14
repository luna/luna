package org.enso.languageserver.libraries.handler

import akka.actor.{Actor, Props}
import com.typesafe.scalalogging.LazyLogging
import org.enso.jsonrpc.{Request, ResponseResult}
import org.enso.languageserver.libraries.LibraryApi._
import org.enso.languageserver.util.UnhandledLogging

class LibraryGetMetadataHandler
    extends Actor
    with LazyLogging
    with UnhandledLogging {
  override def receive: Receive = {
    case Request(LibraryGetMetadata, id, _: LibraryGetMetadata.Params) =>
      // TODO [RW] actual implementation
      sender() ! ResponseResult(
        LibraryGetMetadata,
        id,
        LibraryGetMetadata.Result(None, None)
      )
  }
}

object LibraryGetMetadataHandler {
  def props(): Props = Props(new LibraryGetMetadataHandler)
}
