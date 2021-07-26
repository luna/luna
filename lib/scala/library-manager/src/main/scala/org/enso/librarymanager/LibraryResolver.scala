package org.enso.librarymanager

import org.enso.editions.{Editions, LibraryName, LibraryVersion}
import org.enso.librarymanager.local.LocalLibraryProvider

/** A helper class that figures out which library version should be used in a
  * given configuration.
  *
  * It needs a [[LocalLibraryProvider]] instance, because if
  * `preferLocalLibraries` is set to true, the resulting library version depends
  * on whether a local library with a given name exists.
  */
case class LibraryResolver(
  localLibraryProvider: LocalLibraryProvider
) {

  /** Resolves the library version that entails from the provided configuration.
    *
    * The configuration consists of the edition and the flag specifying if local
    * libraries should override the edition settings.
    *
    * @param libraryName the name of the library to resolve
    * @param edition the edition configuration
    * @param preferLocalLibraries the flag indicating whether to prefer local
    *                             libraries that are present over what the
    *                             edition defines
    * @return either a resolved library version or a resolution error
    */
  def resolveLibraryVersion(
    libraryName: LibraryName,
    edition: Editions.ResolvedEdition,
    preferLocalLibraries: Boolean
  ): Either[LibraryResolutionError, LibraryVersion] = {
    if (preferLocalLibraries) {
      localLibraryProvider
        .findLibrary(libraryName)
        .map(_ => Right(LibraryVersion.Local))
        .getOrElse {
          resolveLibraryFromEdition(libraryName, edition)
        }
    } else resolveLibraryFromEdition(libraryName, edition)
  }

  /** Resolves the library version that entails from the edition.
    *
    * @param libraryName the name of the library to resolve
    * @param edition the edition configuration
    * @return either an error (if the library was not found in the edition) or
    *         the entailed version
    */
  def resolveLibraryFromEdition(
    libraryName: LibraryName,
    edition: Editions.ResolvedEdition
  ): Either[LibraryResolutionError, LibraryVersion] = {
    import Editions.Resolved._
    val immediateResult =
      edition.libraries.get(libraryName).map {
        case LocalLibrary(_) =>
          Right(LibraryVersion.Local)
        case PublishedLibrary(_, version, repository) =>
          Right(LibraryVersion.Published(version, repository))
      }

    immediateResult.getOrElse {
      edition.parent match {
        case Some(parentEdition) =>
          resolveLibraryFromEdition(libraryName, parentEdition)
        case None =>
          Left(
            LibraryResolutionError(
              s"The library `$libraryName` is not defined within " +
              s"the edition."
            )
          )
      }
    }
  }
}
