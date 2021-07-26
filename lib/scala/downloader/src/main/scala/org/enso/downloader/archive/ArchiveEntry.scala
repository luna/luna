package org.enso.downloader.archive

import java.nio.file.Path

/** An archive entry that is provided to the callback when iterating over an
  * archive.
  */
trait ArchiveEntry {

  /** Specifies if the entry represents a directory.
    *
    * If false, it represents a normal file.
    */
  def isDirectory: Boolean

  /** Relative path of the entry inside of the archive.
    */
  def relativePath: Path

  /** Extracts the entry to the provided destination.
    *
    * The destination specifies the full path to the extracted entry including
    * its new filename, not just a parent directory. On UNIX platforms, the
    * permissions of the extracted entry are set as specified in the archive, if
    * possible.
    */
  def extractTo(destination: Path): Unit
}
