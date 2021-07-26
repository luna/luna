package org.enso.librarymanager.local

import com.typesafe.scalalogging.Logger
import org.enso.editions.LibraryName
import org.enso.logger.masking.MaskedPath

import java.nio.file.{Files, Path}
import scala.annotation.tailrec

/** A default implementation of [[LocalLibraryProvider]]. */
class DefaultLocalLibraryProvider(searchPaths: List[Path])
    extends LocalLibraryProvider {

  private val logger = Logger[DefaultLocalLibraryProvider]

  /** @inheritdoc */
  override def findLibrary(libraryName: LibraryName): Option[Path] =
    findLibraryHelper(
      libraryName,
      searchPaths
    )

  /** Searches through the available library paths, checking if any one of them contains the requested library.
    *
    * The first path on the list takes precedence.
    */
  @tailrec
  private def findLibraryHelper(
    libraryName: LibraryName,
    searchPaths: List[Path]
  ): Option[Path] = searchPaths match {
    case head :: tail =>
      val potentialPath =
        LocalLibraryProvider.resolveLibraryPath(head, libraryName)
      if (Files.exists(potentialPath) && Files.isDirectory(potentialPath)) {
        logger.trace(
          s"Found a local $libraryName at " +
          s"[${MaskedPath(potentialPath).applyMasking()}]."
        )
        Some(potentialPath)
      } else {
        logger.trace(
          s"Local library $libraryName not found at " +
          s"[${MaskedPath(potentialPath).applyMasking()}]."
        )
        findLibraryHelper(libraryName, tail)
      }
    case Nil => None
  }
}
