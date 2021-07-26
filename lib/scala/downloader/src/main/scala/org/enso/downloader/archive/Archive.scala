package org.enso.downloader.archive

import com.typesafe.scalalogging.Logger
import org.apache.commons.compress.archivers.tar.{
  TarArchiveEntry,
  TarArchiveInputStream
}
import org.apache.commons.compress.archivers.zip.{
  ZipArchiveEntry,
  ZipArchiveInputStream
}
import org.apache.commons.compress.archivers.{
  ArchiveInputStream,
  ArchiveEntry => ApacheArchiveEntry
}
import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream
import org.apache.commons.io.IOUtils
import org.enso.cli.OS
import org.enso.cli.task.{
  ProgressUnit,
  TaskProgress,
  TaskProgressImplementation
}
import org.enso.downloader.archive.internal.{
  ArchiveIterator,
  BaseRenamer,
  ReadProgress
}

import java.io.BufferedInputStream
import java.nio.file.{Files, Path}
import scala.util.{Try, Using}

/** Contains utilities related to the extraction of various archive file
  * formats.
  *
  * Currently it supports extracting ZIP files and gzipped TAR archives.
  */
object Archive {

  /** An archive entry representing a symbolic link. */
  sealed trait Symlink
  object Symlink {

    /** The TAR archive entry representing a symbolic link.
      *
      * @param target the symlink target
      */
    case class TarArchiveSymlink(target: Path) extends Symlink

    /** The ZIP archive entry representing a symbolic lint.
      * The entry's contents contains the symlink target.
      */
    case object ZipArchiveSymlink extends Symlink
  }

  private val logger = Logger[Archive.type]

  /** Extracts the archive at `archivePath` to `destinationDirectory`.
    *
    * Behaves in the same way as the other [[extractArchive]] overload, but it
    * tries to determine the archive format automatically based on the filename.
    *
    * @param archivePath path to the archive file
    * @param destinationDirectory directory into which the archive contents
    *                             will be extracted
    * @param renameRootFolder if not None, the root folder of the archive will
    *                         be renamed to the provided name. In such case the
    *                         archive must contain only one root folder.
    * @return an instance indicating the progress of extraction
    */
  def extractArchive(
    archivePath: Path,
    destinationDirectory: Path,
    renameRootFolder: Option[Path]
  ): TaskProgress[Unit] = {
    val format = ArchiveFormat.detect(archivePath)
    format match {
      case Some(archiveFormat) =>
        extractArchive(
          archivePath,
          archiveFormat,
          destinationDirectory,
          renameRootFolder
        )
      case None =>
        TaskProgress.immediateFailure(
          new IllegalArgumentException(
            s"Could not detect archive format for " +
            s"${archivePath.getFileName}"
          )
        )
    }
  }

  /** Extracts the archive at `archivePath` to `destinationDirectory`.
    *
    * The extraction is run in a background thread, the function returns
    * immediately with a [[TaskProgress]] instance that can be used to track
    * extraction progress (for example by displaying a progress bar or just
    * waiting for it to complete).
    *
    * If `renameRootFolder` is provided, the root folder of the archive is
    * renamed to the provided value. It is an error to request root folder
    * rename for an archive that has more than one file in the root.
    *
    * @param archivePath path to the archive file
    * @param format format of the archive
    * @param destinationDirectory directory into which the archive contents
    *                             will be extracted
    * @param renameRootFolder if not None, the root folder of the archive will
    *                         be renamed to the provided name. In such case the
    *                         archive must contain only one root folder.
    * @return an instance indicating the progress of extraction
    */
  def extractArchive(
    archivePath: Path,
    format: ArchiveFormat,
    destinationDirectory: Path,
    renameRootFolder: Option[Path]
  ): TaskProgress[Unit] = {
    val rewritePath: Path => Path = renameRootFolder match {
      case Some(value) => new BaseRenamer(value)
      case None        => identity[Path]
    }

    iterateArchive(archivePath, format) { entry =>
      val destinationPath =
        destinationDirectory.resolve(rewritePath(entry.relativePath))
      entry.extractTo(destinationPath)
      true
    }
  }

  /** Iterates over entries of the archive at `archivePath`.
    *
    * The iteration is run in a background thread, the function returns
    * immediately with a [[TaskProgress]] instance that can be used to track
    * iteration progress (for example by displaying a progress bar or just
    * waiting for it to complete).
    *
    * Tries to detect the archive format automatically.
    *
    * @param archivePath path to the archive file
    * @return an instance indicating the progress of iteration
    */
  def iterateArchive(archivePath: Path)(
    callback: ArchiveEntry => Boolean
  ): TaskProgress[Unit] = {
    val format = ArchiveFormat.detect(archivePath)
    format match {
      case Some(archiveFormat) =>
        iterateArchive(archivePath, archiveFormat)(callback)
      case None =>
        TaskProgress.immediateFailure(
          new IllegalArgumentException(
            s"Could not detect archive format for " +
            s"${archivePath.getFileName}"
          )
        )
    }
  }

  /** Iterates over entries of the archive at `archivePath`.
    *
    * The iteration is run in a background thread, the function returns
    * immediately with a [[TaskProgress]] instance that can be used to track
    * iteration progress (for example by displaying a progress bar or just
    * waiting for it to complete).
    *
    * The callback is called for each encountered archive entry. If the callback
    * returns false, iteration is stopped.
    *
    * @param archivePath path to the archive file
    * @param format format of the archive
    * @param callback callback to call for each archive entry; if it returns
    *                 false, iteration is stopped
    * @return an instance indicating the progress of iteration
    */
  private def iterateArchive(archivePath: Path, format: ArchiveFormat)(
    callback: ArchiveEntry => Boolean
  ): TaskProgress[Unit] = {
    val taskProgress = new TaskProgressImplementation[Unit](ProgressUnit.Bytes)

    def runExtraction(): Unit = {
      logger.debug("Opening [{}].", archivePath)
      var missingPermissions: Int = 0

      val result = withOpenArchive(archivePath, format) { (archive, progress) =>
        def processEntry(entry: ApacheArchiveEntry): Boolean = {
          val continue = if (!archive.canReadEntryData(entry)) {
            throw new RuntimeException(
              s"Cannot read ${entry.getName} from $archivePath. " +
              s"The archive may be corrupted."
            )
          } else {
            val path = parseArchiveEntryName(entry.getName)
            val decoratedEntry = new ArchiveEntry {
              override def isDirectory: Boolean = entry.isDirectory
              override def relativePath: Path   = path
              override def extractTo(destinationPath: Path): Unit = {
                if (entry.isDirectory) {
                  Files.createDirectories(destinationPath)
                } else {
                  val parent = destinationPath.getParent
                  Files.createDirectories(parent)
                  Using(Files.newOutputStream(destinationPath)) { out =>
                    IOUtils.copy(archive, out)
                  }.get
                }

                if (OS.isUNIX) {
                  getMode(entry) match {
                    case Some(mode) =>
                      val permissions = POSIXPermissions.decode(mode)
                      Files.setPosixFilePermissions(
                        destinationPath,
                        permissions
                      )
                    case None =>
                      missingPermissions += 1
                  }
                  getSymlink(entry).foreach {
                    case Archive.Symlink.TarArchiveSymlink(target) =>
                      Files.deleteIfExists(destinationPath)
                      Files.createSymbolicLink(destinationPath, target)
                    case Archive.Symlink.ZipArchiveSymlink =>
                      val target =
                        Path.of(Files.readString(destinationPath).trim)
                      Files.deleteIfExists(destinationPath)
                      Files.createSymbolicLink(destinationPath, target)
                  }
                }
              }
            }

            callback(decoratedEntry)
          }

          taskProgress.reportProgress(
            progress.alreadyRead(),
            progress.total()
          )

          continue
        }

        val iterator = ArchiveIterator(archive)
        val _        = iterator.takeWhile(processEntry).length

        ()
      }

      if (missingPermissions > 0) {
        logger.warn(
          "Could not find permissions for [{}] files in " +
          "archive [{}], some files may not have been marked as " +
          "executable.",
          missingPermissions,
          archivePath
        )
      }

      taskProgress.setComplete(result)
    }
    val thread = new Thread(() => runExtraction(), "Reading-Archive")
    thread.start()
    taskProgress
  }

  /** Tries to get the POSIX file permissions associated with that `entry`.
    */
  private def getMode(entry: ApacheArchiveEntry): Option[Int] =
    entry match {
      case entry: TarArchiveEntry =>
        Some(entry.getMode)
      case entry: ZipArchiveEntry =>
        if (entry.getPlatform == ZipArchiveEntry.PLATFORM_UNIX)
          Some(entry.getUnixMode)
        else None
      case _ =>
        None
    }

  /** Get the symlink information associated with that `entry`. */
  private def getSymlink(entry: ApacheArchiveEntry): Option[Symlink] = {
    entry match {
      case entry: TarArchiveEntry if entry.isSymbolicLink =>
        val target = Path.of(entry.getLinkName)
        Some(Symlink.TarArchiveSymlink(target))
      case entry: ZipArchiveEntry if entry.isUnixSymlink =>
        Some(Symlink.ZipArchiveSymlink)
      case _ =>
        None
    }
  }

  /** Opens the archive at `path` and executes the provided action.
    *
    * The action is given an [[ArchiveInputStream]] that can be used to read
    * from the archive and a [[ReadProgress]] instance which indicates how many
    * bytes have already been read at a given moment.
    *
    * The archive and the internal streams are closed when this function exits.
    * The `action` can throw an exception, in which case a failure is returned.
    */
  private def withOpenArchive[R](path: Path, format: ArchiveFormat)(
    action: (ArchiveInputStream, ReadProgress) => R
  ): Try[R] = {
    Using(FileProgressInputStream(path)) { progressInputStream =>
      Using(new BufferedInputStream(progressInputStream)) { buffered =>
        format match {
          case ArchiveFormat.ZIP =>
            Using(new ZipArchiveInputStream(buffered))(
              action(_, progressInputStream.progress)
            ).get
          case ArchiveFormat.TarGz =>
            Using(new GzipCompressorInputStream(buffered)) {
              compressorInputStream =>
                Using(new TarArchiveInputStream(compressorInputStream))(
                  action(_, progressInputStream.progress)
                ).get
            }.get
        }
      }.get
    }
  }

  /** Parses the name of the entry inside of the archive into a relative path.
    *
    * The specification does not restrict entry names to valid paths, but
    * empirical testing shows that paths in the archives we need to support are
    * usually separated by '/' which is correctly parsed into a system-dependent
    * path both on UNIX and Windows platforms.
    */
  private def parseArchiveEntryName(name: String): Path =
    Path.of(name)

}
