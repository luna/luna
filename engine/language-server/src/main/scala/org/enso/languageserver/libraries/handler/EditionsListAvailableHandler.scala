package org.enso.languageserver.libraries.handler

import akka.actor.{Actor, Props}
import com.typesafe.scalalogging.LazyLogging
import org.enso.distribution.EditionManager
import org.enso.jsonrpc.{Request, ResponseError, ResponseResult}
import org.enso.languageserver.filemanager.FileManagerApi.FileSystemError
import org.enso.languageserver.libraries.LibraryApi._
import org.enso.languageserver.util.UnhandledLogging

import scala.util.{Failure, Success, Try}

/** A request handler for the `editions/listAvailable` endpoint.
  *
  * It is a partial implementation - it already allows to list existing
  * editions, but updating is not yet implemented.
  *
  * @param editionManager an edition manager instance
  */
class EditionsListAvailableHandler(editionManager: EditionManager)
    extends Actor
    with LazyLogging
    with UnhandledLogging {
  override def receive: Receive = {
    case Request(EditionsListAvailable, id, _: EditionsListAvailable.Params) =>
      // TODO [RW] once updating editions is implemented this should be made asynchronous
      Try(editionManager.findAllAvailableEditions()) match {
        case Success(editions) =>
          sender() ! ResponseResult(
            EditionsListAvailable,
            id,
            EditionsListAvailable.Result(editions)
          )
        case Failure(exception) =>
          sender() ! ResponseError(
            Some(id),
            FileSystemError(exception.toString)
          )
      }
  }
}

object EditionsListAvailableHandler {

  /** Creates a configuration object to create [[EditionsListAvailableHandler]].
    *
    * @param editionManager an edition manager instance
    */
  def props(editionManager: EditionManager): Props = Props(
    new EditionsListAvailableHandler(editionManager)
  )
}
