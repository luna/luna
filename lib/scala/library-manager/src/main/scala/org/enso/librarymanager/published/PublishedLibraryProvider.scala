package org.enso.librarymanager.published

import nl.gn0s1s.bump.SemVer
import org.enso.editions.Editions.Repository
import org.enso.editions.LibraryName

import java.nio.file.Path
import scala.util.Try

/** A provider of published libraries.
  *
  * It usually should use some kind of a cache to keep already downloaded
  * libraries and download new libraries on demand.
  */
trait PublishedLibraryProvider {

  /** Tries to locate the requested library at a specific version.
    *
    * If the library is not present, a download may be attempted - this is where
    * the `recommendedRepository` is used to guide which repository should be
    * used for the download.
    */
  def findLibrary(
    libraryName: LibraryName,
    version: SemVer,
    recommendedRepository: Repository
  ): Try[Path]
}
