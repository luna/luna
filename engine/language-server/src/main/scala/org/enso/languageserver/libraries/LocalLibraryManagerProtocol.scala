package org.enso.languageserver.libraries

import org.enso.editions.LibraryName

object LocalLibraryManagerProtocol {

  /** A top class representing any request to the [[LocalLibraryManager]]. */
  sealed trait Request

  /** A request to get metadata of a library. */
  case class GetMetadata(libraryName: LibraryName) extends Request

  /** Response to [[GetMetadata]]. */
  case class GetMetadataResponse(
    description: Option[String],
    tagLine: Option[String]
  )

  /** A request to update metadata of a library. */
  case class SetMetadata(
    libraryName: LibraryName,
    description: Option[String],
    tagLine: Option[String]
  ) extends Request

  /** A request to list local libraries. */
  case object ListLocalLibraries extends Request

  /** A response to [[ListLocalLibraries]]. */
  case class ListLocalLibrariesResponse(libraries: Seq[LibraryEntry])

  /** A request to create a new library project. */
  case class Create(
    libraryName: LibraryName,
    authors: Seq[String],
    maintainers: Seq[String],
    license: String
  ) extends Request

  /** A request to publish a library. */
  case class Publish(
    libraryName: LibraryName,
    authToken: String,
    bumpVersionAfterPublish: Boolean
  ) extends Request
}
