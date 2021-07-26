package org.enso.librarymanager.published.repository

import org.apache.commons.compress.archivers.tar.{
  TarArchiveEntry,
  TarArchiveOutputStream
}
import org.apache.commons.compress.compressors.gzip.GzipCompressorOutputStream

import java.io.{BufferedOutputStream, FileOutputStream}
import java.nio.file.Path
import scala.util.Using

/** A helper class used for creating TAR-GZ archives in tests. */
object ArchiveWriter {

  /** A file to add to the archive. */
  sealed trait FileToWrite {

    /** The path that this file should have within the archive. */
    def relativePath: String
  }

  /** Represents a text file to be added to a test archive.
    *
    * @param relativePath the path in the archive
    * @param content the text contents for the file
    */
  case class TextFile(relativePath: String, content: String) extends FileToWrite

  /** Creates a tar archive at the given path, containing the provided files. */
  def writeTarArchive(path: Path, files: Seq[FileToWrite]): Unit = {
    Using(new FileOutputStream(path.toFile)) { outputStream =>
      Using(new BufferedOutputStream(outputStream)) { bufferedStream =>
        Using(new GzipCompressorOutputStream(bufferedStream)) { gzipStream =>
          Using(new TarArchiveOutputStream(gzipStream)) { archive =>
            for (file <- files) {
              file match {
                case TextFile(relativePath, content) =>
                  val entry = new TarArchiveEntry(relativePath)
                  archive.putArchiveEntry(entry)
                  archive.write(content.getBytes)
                  archive.closeArchiveEntry()
              }
            }
          }
        }
      }
    }
  }
}
