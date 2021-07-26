package org.enso.downloader.archive

import org.enso.downloader.archive.internal.ProgressInputStream

import java.io.FileInputStream
import java.nio.file.{Files, Path}

/** A helper that allows to create a [[ProgressInputStream]] for a file located
  * at the given path.
  */
object FileProgressInputStream {

  /** Creates a [[ProgressInputStream]] reading from the file at `path`.
    *
    * The read progress depends on how many bytes have been read from the file.
    * The total size is determined from the file size as returned by the
    * filesystem.
    */
  def apply(path: Path): ProgressInputStream = {
    val size = Files.size(path)
    new ProgressInputStream(
      new FileInputStream(path.toFile),
      Some(size),
      _ => ()
    )
  }
}
