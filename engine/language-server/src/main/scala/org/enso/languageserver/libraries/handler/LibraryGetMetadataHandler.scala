package org.enso.languageserver.libraries.handler

import akka.actor.{Actor, ActorRef, Cancellable, Props, Status}
import akka.pattern.pipe
import com.typesafe.scalalogging.LazyLogging
import nl.gn0s1s.bump.SemVer
import org.enso.editions.Editions.Repository
import org.enso.editions.LibraryName
import org.enso.jsonrpc._
import org.enso.languageserver.filemanager.FileManagerApi.FileSystemError
import org.enso.languageserver.libraries.LibraryApi._
import org.enso.languageserver.libraries.{
  LibraryEntry,
  LocalLibraryManagerProtocol
}
import org.enso.languageserver.requesthandler.RequestTimeout
import org.enso.languageserver.util.UnhandledLogging
import org.enso.librarymanager.published.repository.RepositoryHelper.RepositoryMethods

import scala.concurrent.Future
import scala.concurrent.duration.FiniteDuration

/** A request handler for the `library/create` endpoint.
  *
  * @param timeout request timeout
  * @param localLibraryManager reference to the local library manager actor
  */
class LibraryGetMetadataHandler(
  timeout: FiniteDuration,
  localLibraryManager: ActorRef
) extends Actor
    with LazyLogging
    with UnhandledLogging {
  import context.dispatcher

  override def receive: Receive = requestStage

  private def requestStage: Receive = {
    case Request(
          LibraryGetMetadata,
          id,
          LibraryGetMetadata.Params(namespace, name, version)
        ) =>
      val libraryName = LibraryName(namespace, name)
      version match {
        case LibraryEntry.LocalLibraryVersion =>
          localLibraryManager ! LocalLibraryManagerProtocol.GetMetadata(
            libraryName
          )
        case LibraryEntry.PublishedLibraryVersion(version, repositoryUrl) =>
          fetchPublishedMetadata(
            libraryName,
            version,
            repositoryUrl
          ) pipeTo self
      }

      val cancellable =
        context.system.scheduler.scheduleOnce(timeout, self, RequestTimeout)
      context.become(responseStage(id, sender(), cancellable))
  }

  private def responseStage(
    id: Id,
    replyTo: ActorRef,
    cancellable: Cancellable
  ): Receive = {
    case RequestTimeout =>
      logger.error("Request [{}] timed out.", id)
      replyTo ! ResponseError(Some(id), Errors.RequestTimeout)
      context.stop(self)

    case LocalLibraryManagerProtocol.GetMetadataResponse(
          description,
          tagLine
        ) =>
      replyTo ! ResponseResult(
        LibraryGetMetadata,
        id,
        LibraryGetMetadata.Result(description, tagLine)
      )
      cancellable.cancel()
      context.stop(self)

    case Status.Failure(exception) =>
      replyTo ! ResponseError(Some(id), FileSystemError(exception.getMessage))
      cancellable.cancel()
      context.stop(self)
  }

  // TODO [RW] Once the manifests of downloaded libraries are being cached,
  //  it may be worth to try resolving the local cache first to avoid
  //  downloading the manifest again. This should be done before the issues
  //  #1772 or #1775 are completed.
  private def fetchPublishedMetadata(
    libraryName: LibraryName,
    version: String,
    repositoryUrl: String
  ): Future[LocalLibraryManagerProtocol.GetMetadataResponse] = for {
    semver <- Future.fromTry(
      SemVer(version)
        .toRight(
          new IllegalStateException(
            s"Library version [$version] is not a valid semver string."
          )
        )
        .toTry
    )
    manifest <- Repository(repositoryUrl)
      .accessLibrary(libraryName, semver)
      .downloadManifest()
      .toFuture
  } yield LocalLibraryManagerProtocol.GetMetadataResponse(
    description = manifest.description,
    tagLine     = manifest.tagLine
  )
}

object LibraryGetMetadataHandler {

  /** Creates a configuration object to create [[LibraryGetMetadataHandler]].
    *
    * @param timeout request timeout
    * @param localLibraryManager reference to the local library manager actor
    */
  def props(timeout: FiniteDuration, localLibraryManager: ActorRef): Props =
    Props(new LibraryGetMetadataHandler(timeout, localLibraryManager))
}
