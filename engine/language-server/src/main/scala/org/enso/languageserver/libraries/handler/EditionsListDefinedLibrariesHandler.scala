package org.enso.languageserver.libraries.handler

import akka.actor.{Actor, ActorRef, Props, Status}
import akka.pattern.pipe
import com.typesafe.scalalogging.LazyLogging
import org.enso.editions.LibraryVersion
import org.enso.jsonrpc.{Id, Request, ResponseError, ResponseResult}
import org.enso.languageserver.filemanager.FileManagerApi.FileSystemError
import org.enso.languageserver.libraries.LibraryApi._
import org.enso.languageserver.libraries.{
  BlockingOperation,
  EditionReferenceResolver,
  LibraryEntry
}
import org.enso.languageserver.util.UnhandledLogging
import org.enso.librarymanager.local.LocalLibraryProvider
import org.enso.librarymanager.published.PublishedLibraryCache

/** A request handler for the `editions/listDefinedLibraries` endpoint.
  *
  * @param editionReferenceResolver an [[EditionReferenceResolver]] instance
  * @param localLibraryProvider     a provider of local libraries
  * @param publishedLibraryCache    a cache of published libraries
  */
class EditionsListDefinedLibrariesHandler(
  editionReferenceResolver: EditionReferenceResolver,
  localLibraryProvider: LocalLibraryProvider,
  publishedLibraryCache: PublishedLibraryCache
) extends Actor
    with LazyLogging
    with UnhandledLogging {
  override def receive: Receive = requestStage

  private case class Result(libraries: Seq[LibraryEntry])

  import context.dispatcher

  private def requestStage: Receive = {
    case Request(
          EditionsListDefinedLibraries,
          id,
          EditionsListDefinedLibraries.Params(reference)
        ) =>
      BlockingOperation
        .run {
          val edition = editionReferenceResolver.resolveEdition(reference).get
          edition.getAllDefinedLibraries.toSeq.map { case (name, version) =>
            val isCached = version match {
              case LibraryVersion.Local =>
                localLibraryProvider.findLibrary(name).isDefined
              case LibraryVersion.Published(version, _) =>
                publishedLibraryCache.isLibraryCached(name, version)
            }
            LibraryEntry(
              namespace = name.namespace,
              name      = name.name,
              version   = version,
              isCached  = isCached
            )
          }
        }
        .map(Result) pipeTo self

      context.become(responseStage(id, sender()))
  }

  private def responseStage(id: Id, replyTo: ActorRef): Receive = {
    case Result(libraries) =>
      replyTo ! ResponseResult(
        EditionsListDefinedLibraries,
        id,
        EditionsListDefinedLibraries.Result(libraries)
      )
      context.stop(self)

    case Status.Failure(exception) =>
      // TODO [RW] more detailed errors
      replyTo ! ResponseError(
        Some(id),
        FileSystemError(exception.getMessage)
      )
      context.stop(self)
  }
}

object EditionsListDefinedLibrariesHandler {

  /** Creates a configuration object to create
    * [[EditionsListDefinedLibrariesHandler]].
    *
    * @param editionReferenceResolver an [[EditionReferenceResolver]] instance
    * @param localLibraryProvider     a provider of local libraries
    * @param publishedLibraryCache    a cache of published libraries
    */
  def props(
    editionReferenceResolver: EditionReferenceResolver,
    localLibraryProvider: LocalLibraryProvider,
    publishedLibraryCache: PublishedLibraryCache
  ): Props = Props(
    new EditionsListDefinedLibrariesHandler(
      editionReferenceResolver,
      localLibraryProvider,
      publishedLibraryCache
    )
  )
}
