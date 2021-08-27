package org.enso.languageserver.libraries.handler

import akka.actor.{Actor, ActorRef, Props, Status}
import akka.pattern.pipe
import com.typesafe.scalalogging.LazyLogging
import nl.gn0s1s.bump.SemVer
import org.enso.jsonrpc.{Id, Request, ResponseError, ResponseResult}
import org.enso.languageserver.filemanager.FileManagerApi.FileSystemError
import org.enso.languageserver.libraries.LibraryApi._
import org.enso.languageserver.libraries.{
  BlockingOperation,
  EditionReferenceResolver
}
import org.enso.languageserver.util.UnhandledLogging

/** A request handler for the `editions/resolve` endpoint.
  *
  * @param editionReferenceResolver an [[EditionReferenceResolver]] instance
  */
class EditionsResolveHandler(editionReferenceResolver: EditionReferenceResolver)
    extends Actor
    with LazyLogging
    with UnhandledLogging {
  override def receive: Receive = requestStage

  import context.dispatcher

  private def requestStage: Receive = {
    case Request(EditionsResolve, id, EditionsResolve.Params(reference)) =>
      BlockingOperation.run {
        val edition = editionReferenceResolver.resolveEdition(reference).get
        edition.getEngineVersion
      } pipeTo self

      context.become(responseStage(id, sender()))
  }

  private def responseStage(id: Id, replyTo: ActorRef): Receive = {
    case engineVersion: SemVer =>
      replyTo ! ResponseResult(
        EditionsResolve,
        id,
        EditionsResolve.Result(engineVersion.toString)
      )

    case Status.Failure(exception) =>
      // TODO [RW] more detailed errors
      replyTo ! ResponseError(
        Some(id),
        FileSystemError(exception.getMessage)
      )
  }
}

object EditionsResolveHandler {

  /** Creates a configuration object to create [[EditionsResolveHandler]].
    *
    * @param editionReferenceResolver an [[EditionReferenceResolver]] instance
    */
  def props(editionReferenceResolver: EditionReferenceResolver): Props = Props(
    new EditionsResolveHandler(editionReferenceResolver)
  )
}
