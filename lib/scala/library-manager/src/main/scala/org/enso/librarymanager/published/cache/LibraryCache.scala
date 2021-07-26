package org.enso.librarymanager.published.cache

import nl.gn0s1s.bump.SemVer
import org.enso.editions.{Editions, LibraryName, LibraryVersion}

import java.nio.file.Path
import scala.util.Try

/** A library cache that is also capable of downloading missing libraries (which
  * will then be cached).
  */
trait LibraryCache extends ReadOnlyLibraryCache {

  /** Returns the path to the library if it is already cached.
    *
    * This method should not attempt to download the library if it is missing,
    * because other providers may have it.
    *
    * As this repository is not immutable - new libraries may be added during
    * the runtime, this method must too be aware of the concurrency - it should
    * use locks to make sure that, if the library is currently being installed,
    * it is not returned before the installation is complete (as otherwise the
    * runtime could access an incompletely installed library which could lead to
    * errors).
    */
  override def findCachedLibrary(
    libraryName: LibraryName,
    version: SemVer
  ): Option[Path]

  /** If the cache contains the library, it is returned immediately, otherwise,
    * it tries to download the missing library.
    *
    * It does not need to install the library's dependencies. However once the
    * library is being compiled, installation of its dependencies will be
    * triggered automatically by the compiler.
    *
    * @param libraryName the name of the library to search for
    * @param version the library version
    * @param recommendedRepository the repository that should be used to
    *                              download the library from, if it is missing
    * @return the path to the library or a failure if the library could not be
    *         installed
    */
  def findOrInstallLibrary(
    libraryName: LibraryName,
    version: SemVer,
    recommendedRepository: Editions.Repository
  ): Try[Path]

  /** Ensures that the given library and all of its dependencies are installed.
    *
    * @param libraryName the name of the library to search for
    * @param version the library version
    * @param recommendedRepository the repository that should be used to
    *                              download the library from, if it is missing
    * @param dependencyResolver a function that will specify what versions of
    *                           dependencies should be also downloaded when
    *                           installing the missing library (if any)
    */
  def preinstallLibrary(
    libraryName: LibraryName,
    version: SemVer,
    recommendedRepository: Editions.Repository,
    dependencyResolver: LibraryName => Option[LibraryVersion]
  ): Try[Unit]
}

object LibraryCache {

  /** Finds a path to a particular library version inside of a local
    * repository/cache according to the cache's directory structure.
    *
    * @param root path to the root of the repository
    * @param libraryName name of the library
    * @param version library version
    * @return the path at which the specified library would be located in the
    *         repository
    */
  def resolvePath(root: Path, libraryName: LibraryName, version: SemVer): Path =
    root
      .resolve(libraryName.namespace)
      .resolve(libraryName.name)
      .resolve(version.toString)
}
