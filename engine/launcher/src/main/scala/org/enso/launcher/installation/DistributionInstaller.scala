package org.enso.launcher.installation

import java.nio.file.{Files, Path}
import com.typesafe.scalalogging.Logger
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
import org.enso.launcher.cli.{GlobalCLIOptions, InternalOpts, Main}
import org.enso.launcher.distribution.DefaultManagers
import org.enso.launcher.installation.DistributionInstaller.{
  BundleAction,
  IgnoreBundles,
  MoveBundles
}

import scala.util.control.NonFatal

/** Allows to locally [[install]] a portable distribution.
  *
  * @param manager a distribution manager instance which defines locations for
  *                the source portable distribution and the installation
  *                location
  * @param autoConfirm if set to true, the installer will use defaults instead
  *                    of asking questions
  * @param removeOldLauncher if `autoConfirm` is set to true, specifies whether
  *                          the old launcher should be removed after successful
  *                          installation
  * @param bundleActionOption defines how bundled components are added, if
  *                           [[autoConfirm]] is set, defaults to a move,
  *                           otherwise explicitly asks the user
  */
class DistributionInstaller(
  manager: PortableDistributionManager,
  resourceManager: ResourceManager,
  autoConfirm: Boolean,
  removeOldLauncher: Boolean,
  bundleActionOption: Option[DistributionInstaller.BundleAction]
) {
  private val logger          = Logger[DistributionInstaller]
  final private val installed = manager.LocallyInstalledDirectories
  private val env             = manager.env

  /** Names of additional files that are not essential to running the
    * distribution, but should be copied over to the data root if possible.
    *
    * These files are assumed to be located at the data root.
    */
  private val nonEssentialFiles       = Seq("README.md")
  private val nonEssentialDirectories = Seq("THIRD-PARTY")

  private val enginesDirectory =
    installed.dataDirectory / DistributionManager.ENGINES_DIRECTORY
  private val runtimesDirectory =
    installed.dataDirectory / DistributionManager.RUNTIMES_DIRECTORY

  /** Installs the distribution under configured location.
    *
    * Unless [[autoConfirm]] is true, asks the user to confirm the action after
    * printing where it plans to install itself.
    */
  def install(): Unit = {
    try {
      val settings = prepare()
      resourceManager.acquireExclusiveMainLock(waitAction = () => {
        logger.warn(
          "No other Enso processes associated with this distribution can be " +
          "running during the installation. The installer will wait until " +
          "other Enso processes are terminated."
        )
      })
      installBinary()
      createDirectoryStructure()
      installBundles(settings.bundleAction)
      InfoLogger.info("Installation succeeded.")

      if (settings.removeInstaller) {
        removeInstaller()
      }
    } catch {
      case NonFatal(e) =>
        val message = s"Installation failed with error: $e."
        logger.error(message, e)
        CLIOutput.println(message)
        Main.exit(1)
    }
  }

  private case class InstallationSettings(
    bundleAction: BundleAction,
    removeInstaller: Boolean
  )

  private val currentLauncherPath   = env.getPathToRunningExecutable
  private val installedLauncherPath = installed.binaryExecutable

  /** Prepares for the installation.
    *
    * Finds and reports possible conflicts, and asks the user if they want to
    * proceed (unless [[autoConfirm]] is set, in which case it only reports
    * conflicts).
    */
  private def prepare(): InstallationSettings = {
    if (installedLauncherPath == currentLauncherPath) {
      throw InstallationError(
        "The installation source and destination are the same. Nothing to " +
        "install."
      )
    }

    if (Files.exists(installed.dataDirectory)) {
      throw InstallationError(
        s"${installed.dataDirectory} already exists. " +
        s"Please uninstall the already existing distribution. " +
        s"If no distribution is installed, please remove the directory " +
        s"${installed.dataDirectory} before installing."
      )
    }

    if (
      Files.exists(installed.configDirectory) &&
      installed.configDirectory.toFile.exists()
    ) {
      logger.warn(s"${installed.configDirectory} already exists.")
      if (!Files.isDirectory(installed.configDirectory)) {
        throw InstallationError(
          s"${installed.configDirectory} already exists but is not a " +
          s"directory. Please remove it or change the installation " +
          s"location by setting `${manager.ENSO_CONFIG_DIRECTORY}`."
        )
      }
    }

    for (file <- nonEssentialFiles) {
      val f = manager.paths.dataRoot / file
      if (!Files.exists(f)) {
        logger.warn(s"$f does not exist, it will be skipped.")
      }
    }

    for (dir <- nonEssentialDirectories) {
      val f = manager.paths.dataRoot / dir
      if (!Files.isDirectory(f)) {
        logger.warn(s"$f does not exist, it will be skipped.")
      }
    }

    val message =
      s"""The installer will create the following directories:
         |data directory = `${installed.dataDirectory}`,
         |config directory = `${installed.configDirectory}`
         |and put the launcher binary in `${installed.binDirectory}`.""".stripMargin

    val pathMessage =
      if (isBinOnSystemPath) None
      else
        Some(
          s"`${installed.binDirectory}` is not on system PATH. You may have to " +
          s"add it to the PATH to be able to launch `enso` from anywhere."
        )

    val binMessage =
      if (Files.exists(installed.binaryExecutable))
        Some(
          s"`${installed.binaryExecutable}` already exists and will be " +
          s"overwritten."
        )
      else None

    val additionalMessages = pathMessage.toSeq ++ binMessage.toSeq

    InfoLogger.info(message)
    additionalMessages.foreach(msg => logger.warn(msg))

    val removeInstaller = decideIfInstallerShouldBeRemoved()
    val bundleAction    = decideBundleAction()

    if (!autoConfirm) {
      CLIOutput.println(message + additionalMessages.map("\n" + _).mkString)
      val proceed = CLIOutput.askConfirmation(
        "Do you want to proceed with the installation?",
        yesDefault = binMessage.isEmpty
      )
      if (!proceed) {
        CLIOutput.println("Installation has been cancelled.")
        Main.exit(1)
      }
    }

    InstallationSettings(bundleAction, removeInstaller)
  }

  /** Checks if system PATH includes the directory that the binary will be
    * installed into.
    */
  private def isBinOnSystemPath: Boolean = {
    val paths = env.getSystemPath
    paths.contains(installed.binDirectory)
  }

  /** Copies the binary into the destination directory and ensures that it is
    * executable.
    */
  private def installBinary(): Unit = {
    Files.createDirectories(installed.binDirectory)
    FileSystem.copyFile(
      env.getPathToRunningExecutable,
      installed.binaryExecutable
    )
    FileSystem.ensureIsExecutable(installed.binaryExecutable)
    logger.debug("Binary installed.")
  }

  /** Creates the basic directory structure and copies documentation files if
    * present.
    */
  private def createDirectoryStructure(): Unit = {
    Files.createDirectories(installed.dataDirectory)
    Files.createDirectories(runtimesDirectory)
    Files.createDirectories(enginesDirectory)
    Files.createDirectories(installed.configDirectory)

    if (installed.dataDirectory != manager.paths.dataRoot) {
      copyNonEssentialFiles()

      val configName = GlobalConfigurationManager.globalConfigName
      if (Files.exists(manager.paths.config / configName)) {
        FileSystem.copyFile(
          manager.paths.config / configName,
          installed.configDirectory / configName
        )
      }
    }
  }

  /** Copies non-essential files like README etc.
    *
    * Failure to find/copy one of these is reported as a warning, but does not
    * stop the installation.
    */
  private def copyNonEssentialFiles(): Unit = {
    for (file <- nonEssentialFiles) {
      try {
        logger.trace(s"Copying `$file`.")
        FileSystem.copyFile(
          manager.paths.dataRoot / file,
          installed.dataDirectory / file
        )
      } catch {
        case NonFatal(e) =>
          logger.warn(
            s"An exception $e prevented some non-essential " +
            s"documentation files from being copied."
          )
      }
    }

    for (dir <- nonEssentialDirectories) {
      try {
        FileSystem.copyDirectory(
          manager.paths.dataRoot / dir,
          installed.dataDirectory / dir
        )
      } catch {
        case NonFatal(e) =>
          logger.warn(
            s"An exception $e prevented some non-essential directories from " +
            s"being copied."
          )
      }
    }
  }

  /** Finds bundles included in the portable package.
    *
    * @return a tuple containing sequences of runtime and engine bundles
    */
  private def findBundles(): (Seq[Path], Seq[Path]) = {
    val runtimes =
      if (runtimesDirectory != manager.paths.runtimes)
        FileSystem.listDirectory(manager.paths.runtimes)
      else Seq()
    val engines =
      if (enginesDirectory != manager.paths.engines)
        FileSystem.listDirectory(manager.paths.engines)
      else Seq()

    (runtimes, engines)
  }

  /** Checks if any bundles are available and depending on selected settings,
    * decides how to proceed with the bundles.
    *
    * May ask the user interactively, unless this is prohibited.
    */
  private def decideBundleAction(): BundleAction =
    if (manager.isRunningPortable) {
      val (runtimes, engines) = findBundles()
      if (runtimes.length + engines.length > 0)
        bundleActionOption.getOrElse {
          if (autoConfirm) MoveBundles
          else
            CLIOutput.askQuestion(
              "Found engine/runtime components bundled with the installation. " +
              "How do you want to proceed?",
              Seq(
                DistributionInstaller.MoveBundles,
                DistributionInstaller.CopyBundles,
                DistributionInstaller.IgnoreBundles
              )
            )
        }
      else IgnoreBundles
    } else {
      bundleActionOption match {
        case Some(value) if value != DistributionInstaller.IgnoreBundles =>
          logger.warn(
            s"Installer was asked to ${value.description}, but it seems to " +
            s"not be running from a bundle package."
          )
        case _ =>
      }

      IgnoreBundles
    }

  /** Copies (and possibly removes the originals) bundled engine and runtime
    * components.
    */
  private def installBundles(bundleAction: BundleAction): Unit = {
    if (manager.isRunningPortable) {
      val (runtimes, engines) = findBundles()

      if (runtimes.length + engines.length > 0) {
        if (bundleAction.copy) {
          for (engine <- engines) {
            InfoLogger.info(
              s"Copying bundled Enso engine ${engine.getFileName}."
            )
            FileSystem.copyDirectory(
              engine,
              enginesDirectory / engine.getFileName
            )
          }
          for (runtime <- runtimes) {
            InfoLogger.info(s"Copying bundled runtime ${runtime.getFileName}.")
            FileSystem.copyDirectory(
              runtime,
              runtimesDirectory / runtime.getFileName
            )
          }
        }

        if (bundleAction.delete) {
          engines.foreach(FileSystem.removeDirectory)
          runtimes.foreach(FileSystem.removeDirectory)

          InfoLogger.info(
            "Cleaned bundled files from the original distribution packages."
          )
        }
      }
    } else if (bundleAction != IgnoreBundles) {
      throw new IllegalStateException(
        s"Internal error: The launcher is not run in portable mode, " +
        s"but the final bundle action was not Ignore, but $bundleAction."
      )
    }
  }

  /** Decides if the installer should be removed if the installation succeeds.
    */
  private def decideIfInstallerShouldBeRemoved(): Boolean = {
    def askForRemoval(): Boolean =
      CLIOutput.askConfirmation(
        s"Do you want to remove the original launcher after " +
        s"the installation? (It will not be needed anymore, as the launcher " +
        s"will be copied to `${installed.binaryExecutable}`)",
        yesDefault = true
      )

    if (installedLauncherPath != currentLauncherPath) {
      if (autoConfirm) removeOldLauncher
      else askForRemoval()
    } else false
  }

  /** If the user wants to, removes the installer.
    */
  private def removeInstaller(): Nothing = {
    if (OS.isWindows) {
      InternalOpts
        .runWithNewLauncher(installedLauncherPath)
        .removeOldExecutableAndExit(currentLauncherPath)
    } else {
      Files.delete(currentLauncherPath)
    }
    Main.exit(0)
  }

}

object DistributionInstaller {

  /** Creates a [[DistributionInstaller]] using the default managers.
    */
  def default(
    globalCLIOptions: GlobalCLIOptions,
    removeOldLauncher: Boolean,
    bundleActionOption: Option[BundleAction]
  ): DistributionInstaller =
    new DistributionInstaller(
      DefaultManagers.distributionManager,
      DefaultManagers.defaultResourceManager,
      globalCLIOptions.autoConfirm,
      removeOldLauncher  = removeOldLauncher,
      bundleActionOption = bundleActionOption
    )

  /** Defines the set of possible actions to take when installing the bundled
    * components.
    */
  trait BundleAction extends CLIOutput.Answer {

    /** Specifies whether this action requires copying the bundles to the
      * installed location.
      */
    def copy: Boolean

    /** Specifies whether this action requires to remove the original bundle
      * files afterwards.
      */
    def delete: Boolean
  }

  /** The bundle action that will copy the bundles and keep the ones in the
    * original location too.
    */
  case object CopyBundles extends BundleAction {

    /** @inheritdoc
      */
    override def key: String = "c"

    /** @inheritdoc
      */
    override def description: String = "copy bundles"

    /** @inheritdoc
      */
    def copy: Boolean = true

    /** @inheritdoc
      */
    def delete: Boolean = false
  }

  /** The bundle action that will copy the bundles and remove the ones at the
    * original location on success.
    */
  case object MoveBundles extends BundleAction {

    /** @inheritdoc
      */
    override def key: String = "m"

    /** @inheritdoc
      */
    override def description: String = "move bundles"

    /** @inheritdoc
      */
    def copy: Boolean = true

    /** @inheritdoc
      */
    def delete: Boolean = true
  }

  /** The bundle action that ignores the bundles.
    */
  case object IgnoreBundles extends BundleAction {

    /** @inheritdoc
      */
    override def key: String = "i"

    /** @inheritdoc
      */
    override def description: String = "ignore bundles"

    /** @inheritdoc
      */
    def copy: Boolean = false

    /** @inheritdoc
      */
    def delete: Boolean = false
  }
}
