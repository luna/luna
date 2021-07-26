package org.enso.librarymanager.published.cache

import nl.gn0s1s.bump.SemVer
import org.enso.distribution.locking.Resource
import org.enso.editions.LibraryName

/** A resource that synchronizes installation of a library in the cache. */
case class LibraryResource(libraryName: LibraryName, version: SemVer)
    extends Resource {
  override def name: String =
    s"cached-library-${libraryName.qualifiedName}-$version"
  override def waitMessage: String =
    s"Another Enso instance is currently installing $libraryName ($version), " +
    s"so this action must wait until the installation is complete."
}
