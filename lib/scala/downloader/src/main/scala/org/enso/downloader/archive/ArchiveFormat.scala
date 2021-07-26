package org.enso.downloader.archive

import java.nio.file.Path

/** Enumeration of supported archive formats.
  */
sealed trait ArchiveFormat
object ArchiveFormat {

  /** ZIP file format.
    */
  case object ZIP extends ArchiveFormat

  /** Represents a gzipped TAR archive.
    */
  case object TarGz extends ArchiveFormat

  /** Tries to infer one of the supported archive formats based on the filename.
    *
    * Returns None if it is unable to determine a known format.
    */
  def detect(path: Path): Option[ArchiveFormat] = {
    val fileName = path.getFileName.toString
    if (fileName.endsWith(".zip")) Some(ZIP)
    else if (fileName.endsWith(".tar.gz") || fileName.endsWith(".tgz"))
      Some(TarGz)
    else None
  }
}
