package org.enso.languageserver.libraries.handler

import akka.actor.{Actor, Props}
import com.typesafe.scalalogging.LazyLogging
import org.enso.jsonrpc.{Request, ResponseError, ResponseResult}
import org.enso.languageserver.filemanager.FileManagerApi.FileSystemError
import org.enso.languageserver.libraries.{
  EditionReferenceResolver,
  LibraryEntry
}
import org.enso.languageserver.libraries.LibraryApi._
import org.enso.languageserver.util.UnhandledLogging

import scala.util.{Failure, Success}

/** A request handler for the `editions/listDefinedLibraries` endpoint.
  *
  * @param editionReferenceResolver an [[EditionReferenceResolver]] instance
  */
class EditionsListDefinedLibrariesHandler(
  editionReferenceResolver: EditionReferenceResolver
) extends Actor
    with LazyLogging
    with UnhandledLogging {
  override def receive: Receive = {
    case Request(
          EditionsListDefinedLibraries,
          id,
          EditionsListDefinedLibraries.Params(reference)
        ) =>
      val result = for {
        edition <- editionReferenceResolver.resolveEdition(reference)
      } yield edition.getAllDefinedLibraries.toSeq.map { case (name, version) =>
        LibraryEntry(
          namespace = name.namespace,
          name      = name.name,
          version   = version
        )
      }

      result match {
        case Success(libraries) =>
          sender() ! ResponseResult(
            EditionsListDefinedLibraries,
            id,
            EditionsListDefinedLibraries.Result(libraries)
          )

        case Failure(exception) =>
          // TODO [RW] more detailed errors
          sender() ! ResponseError(
            Some(id),
            FileSystemError(exception.getMessage)
          )
      }
  }
}

object EditionsListDefinedLibrariesHandler {

  /** Creates a configuration object to create
    * [[EditionsListDefinedLibrariesHandler]].
    *
    * @param editionReferenceResolver an [[EditionReferenceResolver]] instance
    */
  def props(editionReferenceResolver: EditionReferenceResolver): Props = Props(
    new EditionsListDefinedLibrariesHandler(editionReferenceResolver)
  )
}
