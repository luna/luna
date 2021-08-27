package org.enso.librarymanager.dependencies

import org.enso.editions.{LibraryName, LibraryVersion}

case class Dependency(
  libraryName: LibraryName,
  version: LibraryVersion,
  isCached: Boolean
)
