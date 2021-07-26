package org.enso.downloader.archive

import org.apache.commons.compress.archivers.tar.{
  TarArchiveEntry,
  TarArchiveOutputStream
}
import org.apache.commons.compress.compressors.gzip.GzipCompressorOutputStream
import org.enso.cli.task.{TaskProgress, TaskProgressImplementation}

import java.io.{BufferedOutputStream, FileOutputStream}
import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Path}
import scala.util.{Try, Using}

/** A helper class for writing TAR archives compressed with gzip. */
class TarGzWriter private (archive: TarArchiveOutputStream) {

  /** Adds a text file to the archive.
    *
    * @param relativePath path of the file in the archive
    * @param content the text content to put in the file
    */
  def writeTextFile(relativePath: String, content: String): Unit = {
    val bytes = content.getBytes(StandardCharsets.UTF_8)
    val entry = new TarArchiveEntry(relativePath)
    entry.setSize(bytes.size.toLong)
    archive.putArchiveEntry(entry)
    archive.write(bytes)
    archive.closeArchiveEntry()
  }

  /** Adds a file from the filesystem to the archive.
    *
    * @param relativePath path of the file in the archive
    * @param filePath a path to a file on the filesystem that will be read and
    *                 put into the archive
    * @return returns the number of bytes that were transferrred from the input
    *         file
    */
  def writeFile(relativePath: String, filePath: Path): Long = {
    val entry = new TarArchiveEntry(filePath.toFile, relativePath)
    archive.putArchiveEntry(entry)
    val bytesTransferred = Files.copy(filePath, archive)
    archive.closeArchiveEntry()
    bytesTransferred
  }
}

object TarGzWriter {

  /** Creates a .tar.gz archive at the specified destination.
    *
    * It calls the `actions` callback with a [[TarGzWriter]] instance which can
    * be used to add files to the archive. The [[TarGzWriter]] instance is only
    * valid during the call of this callback, it should not be leaked anywhere
    * else as it will then be invalid.
    */
  def createArchive(
    destination: Path
  )(actions: TarGzWriter => Unit): Try[Unit] =
    Using(new FileOutputStream(destination.toFile)) { outputStream =>
      Using(new BufferedOutputStream(outputStream)) { bufferedStream =>
        Using(new GzipCompressorOutputStream(bufferedStream)) { gzipStream =>
          Using(new TarArchiveOutputStream(gzipStream)) { archive =>
            val writer = new TarGzWriter(archive)
            actions(writer)
          }.get
        }.get
      }.get
    }

  /** Creates a .tar.gz archive from a list of files.
    *
    * @param archiveDestination path specifying where to put the archive
    * @param files list of paths to files that should be compressed; these
    *              should be regular files, the behaviour is undefined if one of
    *              these paths is a directory
    * @param basePath the base path to compute the relative paths of the
    *                 compressed files; all `files` should be inside of the
    *                 directory denoted by `basePath`
    */
  def compress(
    archiveDestination: Path,
    files: Seq[Path],
    basePath: Path
  ): TaskProgress[Unit] = {
    val normalizedBase = basePath.toAbsolutePath.normalize
    def relativePath(file: Path): String = {
      val normalized = file.toAbsolutePath.normalize
      if (!normalized.startsWith(normalizedBase)) {
        throw new IllegalArgumentException(
          "TarGzWriter precondition failure: " +
          "Files should all be inside of the provided basePath."
        )
      }
      normalizedBase.relativize(normalized).toString
    }

    val taskProgress = new TaskProgressImplementation[Unit]()

    def runCompresion(): Unit = {
      val sumSize = files.map(Files.size).sum

      val result = TarGzWriter.createArchive(archiveDestination) { writer =>
        var totalBytesWritten: Long = 0
        def update(): Unit =
          taskProgress.reportProgress(totalBytesWritten, Some(sumSize))
        update()
        for (file <- files) {
          // TODO [RW] Ideally we could report progress for each chunk, offering
          //  more granular feedback for big data files.
          val bytesWritten = writer.writeFile(relativePath(file), file)
          totalBytesWritten += bytesWritten
          update()
        }
      }

      taskProgress.setComplete(result)
    }

    val thread = new Thread(() => runCompresion(), "Writing-Archive")
    thread.start()

    taskProgress
  }
}
