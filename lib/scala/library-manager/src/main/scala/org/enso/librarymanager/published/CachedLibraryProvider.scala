package org.enso.librarymanager.published

import nl.gn0s1s.bump.SemVer
import org.enso.editions.{Editions, LibraryName}
import org.enso.librarymanager.LibraryResolutionError
import org.enso.librarymanager.published.cache.ReadOnlyLibraryCache

import java.nio.file.Path
import scala.annotation.tailrec
import scala.util.Try

/** A [[PublishedLibraryProvider]] that just provides libraries which are
  * already available in the cache.
  */
class CachedLibraryProvider(caches: List[ReadOnlyLibraryCache])
    extends PublishedLibraryProvider
    with PublishedLibraryCache {

  @tailrec
  private def findCachedHelper(
    libraryName: LibraryName,
    version: SemVer,
    caches: List[ReadOnlyLibraryCache]
  ): Option[Path] = caches match {
    case head :: tail =>
      head.findCachedLibrary(libraryName, version) match {
        case Some(found) => Some(found)
        case None        => findCachedHelper(libraryName, version, tail)
      }
    case Nil => None
  }

  /** Looks for the library in the known caches. */
  override def findCachedLibrary(
    libraryName: LibraryName,
    version: SemVer
  ): Option[Path] = findCachedHelper(libraryName, version, caches)

  /** @inheritdoc */
  override def findLibrary(
    libraryName: LibraryName,
    version: SemVer,
    recommendedRepository: Editions.Repository
  ): Try[Path] =
    findCachedLibrary(libraryName, version)
      .toRight(
        LibraryResolutionError(
          s"Library [$libraryName:$version] was not found in the cache."
        )
      )
      .toTry

  /** @inheritdoc */
  override def isLibraryCached(
    libraryName: LibraryName,
    version: SemVer
  ): Boolean = findCachedLibrary(libraryName, version).isDefined
}
