package org.enso.languageserver.libraries.handler

import akka.actor.{Actor, ActorRef, Props, Status}
import akka.pattern.pipe
import com.typesafe.scalalogging.LazyLogging
import org.enso.editions.updater.EditionManager
import org.enso.jsonrpc.{Id, Request, ResponseError, ResponseResult}
import org.enso.languageserver.filemanager.FileManagerApi.FileSystemError
import org.enso.languageserver.libraries.BlockingOperation
import org.enso.languageserver.libraries.LibraryApi._
import org.enso.languageserver.util.UnhandledLogging

/** A request handler for the `editions/listAvailable` endpoint.
  *
  * @param editionManager an edition manager instance
  */
class EditionsListAvailableHandler(editionManager: EditionManager)
    extends Actor
    with LazyLogging
    with UnhandledLogging {
  override def receive: Receive = requestStage

  import context.dispatcher

  case class Result(editions: Seq[String])

  private def requestStage: Receive = {
    case Request(
          EditionsListAvailable,
          id,
          EditionsListAvailable.Params(update)
        ) =>
      BlockingOperation
        .run(editionManager.findAllAvailableEditions(update))
        .map(Result) pipeTo self

      context.become(responseStage(id, sender()))
  }

  private def responseStage(id: Id, replyTo: ActorRef): Receive = {
    case Result(editions) =>
      replyTo ! ResponseResult(
        EditionsListAvailable,
        id,
        EditionsListAvailable.Result(editions.sorted)
      )
      context.stop(self)

    case Status.Failure(exception) =>
      replyTo ! ResponseError(
        Some(id),
        FileSystemError(exception.toString)
      )
      context.stop(self)
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
