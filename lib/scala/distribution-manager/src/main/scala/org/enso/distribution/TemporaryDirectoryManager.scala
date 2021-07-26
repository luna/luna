package org.enso.distribution

import com.typesafe.scalalogging.Logger
import org.enso.distribution.locking.ResourceManager

import java.nio.file.{FileAlreadyExistsException, Files, Path}
import scala.util.Random

/** Manages safe access to the local temporary directory.
  *
  * The temporary directory is created on demand and automatically removed if it
  * is empty. Temporary files from previous runs are removed when the temporary
  * directory is first accessed. Locking mechanism is used to ensure that the
  * old files are no longer used by any other instances running in parallel.
  *
  * The local temporary directory is located inside of ENSO_DATA_ROOT, which
  * means it should be on the same disk partition as directories keeping
  * engines, runtimes and cached libraries.
  *
  * This directory is used as a destination for extracting component packages,
  * so that they can be moved all at once at the last step of the installation.
  */
class TemporaryDirectoryManager(
  unsafeRoot: Path,
  resourceManager: ResourceManager
) {
  private val logger = Logger[TemporaryDirectoryManager]
  private val random = new Random()

  /** Creates a unique temporary subdirectory. */
  def temporarySubdirectory(prefix: String = ""): Path = {
    val paddedPrefix = if (prefix != "") prefix + "-" else prefix
    val randomSuffix = random.nextInt().toString.stripPrefix("-")
    val path =
      safeTemporaryDirectory.resolve(paddedPrefix + randomSuffix)
    if (Files.exists(path))
      temporarySubdirectory(prefix)
    else {
      try {
        Files.createDirectory(path)
      } catch {
        case _: FileAlreadyExistsException =>
          temporarySubdirectory(prefix)
      }
    }
  }

  /** Returns path to a directory for storing temporary files that is located on
    * the same filesystem as `runtimes` and `engines`.
    *
    * It is used during installation to decrease the possibility of getting a
    * broken installation if the installation process has been abruptly
    * terminated. The directory is created on demand (when its path is requested
    * for the first time) and is removed if the application exits normally (as
    * long as it is empty, but normal termination of the installation process
    * should ensure that). If that fails, it is also cleaned before any future
    * accesses.
    */
  private lazy val safeTemporaryDirectory: Path = {
    resourceManager.startUsingTemporaryDirectory()
    Files.createDirectories(unsafeRoot)
    unsafeRoot
  }

  /** Tries to clean the temporary files directory.
    *
    * It should be run at startup whenever the program wants to run clean-up.
    * Currently it is run when installation-related operations are taking place.
    * It may not proceed if another process is using it. It has to be run before
    * the first access to the temporaryDirectory, as after that the directory is
    * marked as in-use and will not be cleaned.
    */
  def tryCleaningTemporaryDirectory(): Unit = {
    if (Files.exists(unsafeRoot)) {
      resourceManager.tryWithExclusiveTemporaryDirectory {
        if (!FileSystem.isDirectoryEmpty(unsafeRoot)) {
          logger.info(
            "Cleaning up temporary files from a previous installation."
          )
        }
        FileSystem.removeDirectory(unsafeRoot)
        Files.createDirectories(unsafeRoot)
        FileSystem.removeEmptyDirectoryOnExit(unsafeRoot)
      }
    }
  }
}

object TemporaryDirectoryManager {

  /** A helper constructor that creates a [[TemporaryDirectoryManager]] using
    * the temporary directory inside of the distribution managed by the provided
    * [[DistributionManager]].
    */
  def apply(
    distribution: DistributionManager,
    resourceManager: ResourceManager
  ): TemporaryDirectoryManager = new TemporaryDirectoryManager(
    distribution.paths.unsafeTemporaryDirectory,
    resourceManager
  )
}
