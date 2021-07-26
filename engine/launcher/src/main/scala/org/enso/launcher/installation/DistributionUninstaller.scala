package org.enso.launcher.installation

import java.nio.file.{Files, Path}
import com.typesafe.scalalogging.Logger
import org.apache.commons.io.FileUtils
import org.enso.cli.{CLIOutput, OS}
import org.enso.distribution.{
  DistributionManager,
  FileSystem,
  PortableDistributionManager
}
import org.enso.distribution.locking.ResourceManager
import org.enso.distribution.FileSystem.PathSyntax
import org.enso.runtimeversionmanager.config.GlobalConfigurationManager
import org.enso.launcher.InfoLogger
import org.enso.launcher.cli.{
  GlobalCLIOptions,
  InternalOpts,
  LauncherLogging,
  Main
}
import org.enso.launcher.distribution.DefaultManagers

import scala.util.control.NonFatal

/** Allows to [[uninstall]] an installed distribution.
  *
  * @param manager a distribution manager instance which defines locations for
  *                the distribution that will be uninstalled
  */
class DistributionUninstaller(
  manager: PortableDistributionManager,
  resourceManager: ResourceManager,
  globalCLIOptions: GlobalCLIOptions
) {
  private val autoConfirm = globalCLIOptions.autoConfirm
  private val logger      = Logger[DistributionUninstaller]

  /** Uninstalls a locally installed (non-portable) distribution.
    *
    * Removes the launcher executable and the ENSO_DATA_DIRECTORY and
    * ENSO_CONFIG_DIRECTORY directories (unless they contain unexpected files).
    * If unexpected files are encountered and [[autoConfirm]] is set, the files
    * are preserved and the directories are not removed (but the expected
    * contents are cleaned anyway), otherwise the program asks if the unexpected
    * files should be removed.
    */
  def uninstall(): Unit = {
    checkPortable()
    askConfirmation()
    resourceManager.acquireExclusiveMainLock(waitAction = () => {
      logger.warn(
        "Please ensure that no other Enso processes are using this " +
        "distribution before uninstalling. The uninstaller will resume once " +
        "all related Enso processes exit."
      )
    })
    if (OS.isWindows) uninstallWindows()
    else uninstallUNIX()
  }

  /** Uninstall strategy for OSes that can remove running executables.
    *
    * Simply removes each component, starting with the ones that can potentially
    * be nested (as config and the binary can be inside of the data directory
    * if the user wishes so).
    */
  private def uninstallUNIX(): Unit = {
    uninstallConfig()
    uninstallExecutableUNIX()
    uninstallDataContents(deferDataRootRemoval = false)
    InfoLogger.info("Successfully uninstalled the distribution.")
  }

  /** Uninstall strategy for Windows, where it is not possible to remove a
    * running executable.
    *
    * The executable has to be removed last as the program must terminate to do
    * so. If the executable is inside of the data directory, the directory is
    * cleaned, but its removal is deferred and it will be removed just after the
    * executable at the end.
    */
  private def uninstallWindows(): Unit = {
    val deferRootRemoval = isBinaryInsideData
    uninstallConfig()
    val newPath = partiallyUninstallExecutableWindows()
    uninstallDataContents(deferRootRemoval)
    InfoLogger.info(
      "Successfully uninstalled the distribution but for the launcher " +
      "executable. It will be removed in a moment after this program " +
      "terminates."
    )
    finishUninstallExecutableWindows(
      newPath,
      if (deferRootRemoval) Some(manager.paths.dataRoot) else None
    )
  }

  /** Checks if the launcher is running in portable mode and terminates
    * execution if it does.
    *
    * It prints an explanation that uninstall can only be used with non-portable
    * mode and a tip on how the portable distribution can be easily removed
    * manually.
    */
  private def checkPortable(): Unit = {
    if (manager.isRunningPortable) {
      logger.warn(
        "The Enso distribution you are currently running is in portable " +
        "mode, so it cannot be uninstalled."
      )
      InfoLogger.info(
        s"If you still want to remove it, you can just remove the " +
        s"`${manager.paths.dataRoot}` directory."
      )
      Main.exit(1)
    }
  }

  /** Prints an explanation of what will be uninstalled and which directories
    * will be removed and asks the user if they want to proceed.
    */
  private def askConfirmation(): Unit = {
    InfoLogger.info(
      s"Uninstalling this distribution will remove the launcher located at " +
      s"`${manager.env.getPathToRunningExecutable}`, all engine and runtime " +
      s"components and configuration managed by this distribution."
    )
    InfoLogger.info(
      s"ENSO_DATA_DIRECTORY (${manager.paths.dataRoot}) and " +
      s"ENSO_CONFIG_DIRECTORY (${manager.paths.config}) will be removed " +
      s"unless they contain unexpected files."
    )
    if (!autoConfirm) {
      val proceed =
        CLIOutput.askConfirmation("Do you want to proceed?", yesDefault = true)
      if (!proceed) {
        logger.warn("Uninstallation has been cancelled on user request.")
        Main.exit(1)
      }
    }
  }

  /** True if the currently running executable is inside of the data root.
    *
    * This is checked, because on Windows this will make removing data root more
    * complicated.
    */
  private def isBinaryInsideData: Boolean = {
    val binaryPath =
      manager.env.getPathToRunningExecutable.toAbsolutePath.normalize
    val dataPath = manager.paths.dataRoot.toAbsolutePath.normalize
    binaryPath.startsWith(dataPath)
  }

  /** Removes the configuration file and the ENSO_CONFIG_DIRECTORY if it does
    * not contain any other files (or if the user agreed to remove them too).
    */
  private def uninstallConfig(): Unit = {
    FileSystem.removeFileIfExists(
      manager.paths.config / GlobalConfigurationManager.globalConfigName
    )
    val remaining =
      FileSystem.listDirectory(manager.paths.config).map(_.getFileName.toString)
    handleRemainingFiles(
      manager.ENSO_CONFIG_DIRECTORY,
      manager.paths.config,
      remaining
    )
    FileSystem.removeDirectoryIfEmpty(manager.paths.config)
  }

  /** Files that are expected to be inside of the data root.
    */
  private val knownDataFiles = Seq("README.md", "NOTICE")

  /** Directories that are expected to be inside of the data root, except for
    * the locks directory which is handled separately.
    */
  private val knownDataDirectories =
    Set.from(
      manager.LocallyInstalledDirectories.possibleDirectoriesInsideDataDirectory
    ) - DistributionManager.LOCK_DIRECTORY

  /** Removes all files contained in the ENSO_DATA_DIRECTORY and possibly the
    * directory itself.
    *
    * If `deferDataRootRemoval` is set, the directory itself is not removed
    * because removing the `bin` directory may block this action. Other files
    * and directories are removed nonetheless,so only the `bin` directory and
    * the root itself are removed at the end.
    */
  private def uninstallDataContents(deferDataRootRemoval: Boolean): Unit = {
    FileSystem.removeDirectory(manager.paths.engines)
    FileSystem.removeDirectory(manager.paths.runtimes)

    val dataRoot = manager.paths.dataRoot

    val logsInsideData = manager.paths.logs.toAbsolutePath.normalize.startsWith(
      dataRoot.toAbsolutePath.normalize
    )
    if (logsInsideData) {
      LauncherLogging.prepareForUninstall(globalCLIOptions.colorMode)
    }

    for (dirName <- knownDataDirectories) {
      FileSystem.removeDirectoryIfExists(dataRoot / dirName)
    }
    for (fileName <- knownDataFiles) {
      FileSystem.removeFileIfExists(dataRoot / fileName)
    }

    resourceManager.unlockTemporaryDirectory()
    resourceManager.releaseMainLock()

    val lockDirectory = dataRoot / DistributionManager.LOCK_DIRECTORY
    if (Files.isDirectory(lockDirectory)) {
      for (lock <- FileSystem.listDirectory(lockDirectory)) {
        try {
          Files.delete(lock)
        } catch {
          case NonFatal(exception) =>
            logger.error(
              s"Cannot remove lockfile ${lock.getFileName}.",
              exception
            )
            throw exception
        }
      }
      FileSystem.removeDirectory(lockDirectory)
    }

    if (!deferDataRootRemoval) {
      val nestedBinDirectory = dataRoot / "bin"
      if (Files.exists(nestedBinDirectory))
        FileSystem.removeDirectoryIfEmpty(nestedBinDirectory)
    }

    val ignoredFiles = if (deferDataRootRemoval) Set("bin") else Set()
    val remainingFiles = FileSystem
      .listDirectory(dataRoot)
      .map(_.getFileName.toString)
      .toSet -- ignoredFiles
    if (remainingFiles.nonEmpty) {
      handleRemainingFiles(
        manager.ENSO_DATA_DIRECTORY,
        dataRoot.toAbsolutePath.normalize,
        remainingFiles.toSeq
      )
    }

    if (!deferDataRootRemoval) {
      FileSystem.removeDirectoryIfEmpty(dataRoot)
    }
  }

  /** Common logic for handling unexpected files in ENSO_DATA_DIRECTORY and
    * ENSO_CONFIG_DIRECTORY.
    *
    * It asks the user if they want to remove these files unless `auto-confirm`
    * is set, in which case it just prints a warning.
    *
    * @param directoryName name of the directory
    * @param path path to the directory
    * @param remainingFiles sequence of filenames that are present in that
    *                       directory but are not expected
    */
  private def handleRemainingFiles(
    directoryName: String,
    path: Path,
    remainingFiles: Seq[String]
  ): Unit =
    if (remainingFiles.nonEmpty) {
      def remainingFilesList =
        remainingFiles.map(fileName => s"`$fileName`").mkString(", ")
      if (autoConfirm) {
        logger.warn(
          s"$directoryName ($path) contains unexpected files: " +
          s"$remainingFilesList, so it will not be removed."
        )
      } else {
        logger.warn(
          s"$directoryName ($path) contains unexpected files: " +
          s"$remainingFilesList."
        )
        def confirmation =
          CLIOutput.askConfirmation(
            s"Do you want to remove the $directoryName containing these files?"
          )
        if (confirmation) {
          for (fileName <- remainingFiles) {
            FileUtils.forceDelete((path / fileName).toFile)
          }
        }
      }
    }

  /** Uninstalls the executable on platforms that allow for removing running
    * files.
    *
    * Simply removes the file.
    */
  private def uninstallExecutableUNIX(): Unit = {
    FileSystem.removeFileIfExists(manager.env.getPathToRunningExecutable)
  }

  /** Moves the current launcher executable, so other processes cannot start it
    * while uninstallation is in progress.
    *
    * It will be removed at the last stage of the uninstallation.
    *
    * @return new path of the executable
    */
  private def partiallyUninstallExecutableWindows(): Path = {
    val currentPath = manager.env.getPathToRunningExecutable

    val newPath = currentPath.getParent.resolve(OS.executableName("enso.old"))
    Files.move(currentPath, newPath)
    newPath
  }

  /** Uninstalls the executable on Windows where it is impossible to remove an
    * executable that is running.
    *
    * Uses a workaround implemented in [[InternalOpts]]. Has to be run at the
    * very end as it has to terminate the current executable.
    *
    * @param myNewPath path to the current (possibly moved) executable
    * @param parentToRemove optional path to the parent directory that should be
    *                       removed alongside the executable
    */
  private def finishUninstallExecutableWindows(
    myNewPath: Path,
    parentToRemove: Option[Path]
  ): Nothing = {
    val temporaryLauncher =
      Files.createTempDirectory("enso-uninstall") / OS.executableName("enso")
    val oldLauncher = myNewPath
    Files.copy(oldLauncher, temporaryLauncher)
    InternalOpts
      .runWithNewLauncher(temporaryLauncher)
      .finishUninstall(oldLauncher, parentToRemove)
  }
}

object DistributionUninstaller {

  /** Creates a default [[DistributionUninstaller]] using the default managers
    * and the provided CLI options.
    */
  def default(globalCLIOptions: GlobalCLIOptions): DistributionUninstaller =
    new DistributionUninstaller(
      DefaultManagers.distributionManager,
      DefaultManagers.defaultResourceManager,
      globalCLIOptions
    )
}
