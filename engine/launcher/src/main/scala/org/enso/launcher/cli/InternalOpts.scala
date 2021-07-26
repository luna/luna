package org.enso.launcher.cli

import java.io.IOException
import java.nio.file.{Files, NoSuchFileException, Path}
import cats.implicits._
import nl.gn0s1s.bump.SemVer
import org.enso.cli.OS
import org.enso.cli.arguments.Opts
import org.enso.cli.arguments.Opts.implicits._
import org.enso.distribution.FileSystem
import org.enso.runtimeversionmanager.CurrentVersion
import org.enso.distribution.FileSystem.PathSyntax
import org.enso.runtimeversionmanager.cli.Arguments._
import org.enso.launcher.distribution.LauncherEnvironment
import org.enso.launcher.upgrade.LauncherUpgrader
import org.enso.launcher.releases.LauncherRepository

/** Implements internal options that the launcher may use when running another
  * instance of itself.
  *
  * These options are used primarily to implement workarounds for
  * Windows-specific filesystem limitations. They should not be used by the
  * users directly, so they are not displayed in the help text.
  *
  * The implemented features are following:
  *
  * 1. Remove Old Executable
  *    On Windows, if an executable is running, its file is locked, so it is
  *    impossible to remove a file that is running. Thus it is not possible for
  *    the launcher to directly remove its old executable when it finishes the
  *    installation. Instead it launches the newly installed launcher, which
  *    tries to remove it, and terminates itself. Thus the executable lock is
  *    soon freed and the new launcher is able to remove the old one. The new
  *    launcher attempts several retries, because it may take some time for the
  *    executable to be unlocked (especially as on Windows software like an
  *    antivirus may block it for some more time after terminating).
  * 2. Finish Uninstall
  *    After uninstalling the distribution, we need to remove the executable,
  *    but for the same reasons as above, we need a workaround. The uninstaller
  *    creates a copy of itself in a temporary directory (which will be removed
  *    when the system removes temporary files) and uses it to remove the
  *    original binary. It then can also remove the (now empty) installation
  *    directory if necessary.
  * 3. Continue Upgrade
  *    This one is not a Windows-specific workaround but a way for the launcher
  *    to perform multi-step upgrade. If the launcher cannot upgrade directly to
  *    a newer version (because, for example, it requires some additional
  *    upgrade logic), it will first download some version 'in the middle' that
  *    is old enough that it can upgrade to it directly, but new enough that it
  *    has some new upgrade logic. It will extract it and run this temporary
  *    launcher executable, telling it to continue the upgrade. This can be
  *    repeated multiple times if multiple steps are required to reach the
  *    target version. InternalOpts are used to run the new executable with all
  *    the necessary options and to handle the upgrade continuation request.
  * 4. Version and Repository Emulation
  *    To be able to test the upgrade mechanism, we need to run it as built
  *    native executables. But we do not want to do any network requests inside
  *    of the tests because of their instability. Instead, we add internal
  *    options that allow to override the default, network-backed repository
  *    with a fake repository backed by the filesystem. Moreover, to avoid
  *    building multiple fat launcher executables, we provide an internal option
  *    to override its version (and executable location). This option is used by
  *    thin wrappers written in Rust which run the original launcher but
  *    overriding its version. This mechanism is used for testing the multi-step
  *    upgrade. These emulation options are only enabled in development builds.
  */
object InternalOpts {
  private val REMOVE_OLD_EXECUTABLE   = "internal-remove-old-executable"
  private val FINISH_UNINSTALL        = "internal-finish-uninstall"
  private val FINISH_UNINSTALL_PARENT = "internal-finish-uninstall-parent"
  private val CONTINUE_UPGRADE        = "internal-continue-upgrade"
  private val UPGRADE_ORIGINAL_PATH   = "internal-upgrade-original-path"

  private val EMULATE_VERSION         = "internal-emulate-version"
  private val EMULATE_LOCATION        = "internal-emulate-location"
  private val EMULATE_REPOSITORY      = "internal-emulate-repository"
  private val EMULATE_REPOSITORY_WAIT = "internal-emulate-repository-wait"

  private var inheritEmulateRepository: Option[Path] = None
  private var inheritShouldWaitForAssets: Boolean    = false

  /** Removes internal testing options that should not be preserved in the called executable.
    *
    * In release mode, this is an identity function, since these internal options are not permitted anyway.
    */
  def removeInternalTestOptions(args: Seq[String]): Seq[String] =
    if (buildinfo.Info.isRelease) args
    else {
      (removeOption(EMULATE_VERSION) andThen removeOption(EMULATE_LOCATION))(
        args
      )
    }

  private def removeOption(name: String): Seq[String] => Seq[String] = {
    args: Seq[String] =>
      val indexedArgs = args.zipWithIndex
      def dropArguments(fromIdx: Int, howMany: Int = 1): Seq[String] =
        args.take(fromIdx) ++ args.drop(fromIdx + howMany)
      indexedArgs.find(_._1.startsWith(s"--$name=")) match {
        case Some((_, idx)) =>
          dropArguments(idx)
        case None =>
          indexedArgs.find(_._1 == s"--$name") match {
            case Some((_, idx)) =>
              dropArguments(idx, howMany = 2)
            case None =>
              args
          }
      }
  }

  /** Additional top level options that are internal to the launcher and should
    * not be used by users directly.
    *
    * They are used to implement workarounds for install / upgrade on Windows.
    */
  def topLevelOptions: Opts[GlobalCLIOptions => Unit] = {
    val removeOldExecutableOpt = Opts
      .optionalParameter[Path](
        REMOVE_OLD_EXECUTABLE,
        "PATH",
        "Removes the old executable file after the installation. " +
        "Used as a workaround for file locking on Windows."
      )
      .hidden

    val finishUninstallOpt = Opts
      .optionalParameter[Path](
        FINISH_UNINSTALL,
        "PATH",
        "Removes the old executable."
      )
      .hidden
    val finishUninstallParentOpt = Opts
      .optionalParameter[Path](
        FINISH_UNINSTALL_PARENT,
        "PATH",
        s"Removes the possible parent directories of the old executable. " +
        s"To be used only in conjunction with $FINISH_UNINSTALL. Has no " +
        s"effect if used without that option."
      )
      .hidden

    val continueUpgrade = Opts
      .optionalParameter[SemVer](
        CONTINUE_UPGRADE,
        "VERSION",
        "Executes next step of the upgrade that should finally result in " +
        "upgrading to the provided VERSION."
      )
      .hidden

    val originalPath =
      Opts.optionalParameter[Path](UPGRADE_ORIGINAL_PATH, "PATH", "").hidden

    (
      testingOptions,
      removeOldExecutableOpt,
      finishUninstallOpt,
      finishUninstallParentOpt,
      continueUpgrade,
      originalPath
    ) mapN {
      (
        _,
        removeOldExecutableOpt,
        finishUninstallOpt,
        finishUninstallParentOpt,
        continueUpgrade,
        originalPath
      ) => (config: GlobalCLIOptions) =>
        removeOldExecutableOpt.foreach { oldExecutablePath =>
          removeOldExecutable(oldExecutablePath)
          sys.exit(0)
        }

        finishUninstallOpt.foreach { executablePath =>
          finishUninstall(executablePath, finishUninstallParentOpt)
          sys.exit(0)
        }

        continueUpgrade.foreach { version =>
          LauncherUpgrader
            .default(config, originalExecutablePath = originalPath)
            .internalContinueUpgrade(version)
          Main.exit(0)
        }
    }
  }

  /** Internal options used for testing.
    *
    * Disabled in release mode.
    */
  private def testingOptions: Opts[Unit] =
    if (buildinfo.Info.isRelease) Opts.pure(())
    else {
      val emulateVersion =
        Opts.optionalParameter[SemVer](EMULATE_VERSION, "VERSION", "").hidden
      val emulateLocation =
        Opts.optionalParameter[Path](EMULATE_LOCATION, "PATH", "").hidden
      val emulateRepository =
        Opts.optionalParameter[Path](EMULATE_REPOSITORY, "PATH", "").hidden
      val waitForAssets =
        Opts.flag(EMULATE_REPOSITORY_WAIT, "", showInUsage = false).hidden

      (emulateVersion, emulateLocation, emulateRepository, waitForAssets) mapN {
        (emulateVersion, emulateLocation, emulateRepository, waitForAssets) =>
          emulateVersion.foreach { version =>
            CurrentVersion.internalOverrideVersion(version)
          }

          emulateLocation.foreach { location =>
            LauncherEnvironment.internalOverrideExecutableLocation(location)
          }

          if (waitForAssets) {
            inheritShouldWaitForAssets = true
          }

          emulateRepository.foreach { repositoryPath =>
            inheritEmulateRepository = Some(repositoryPath)
            LauncherRepository.internalUseFakeRepository(
              repositoryPath,
              waitForAssets
            )
          }
      }

    }

  /** Specifies options that are inherited by the process that is launched when
    * continuing the upgrade.
    */
  private def optionsToInherit: Seq[String] = {
    val repositoryPath = inheritEmulateRepository
      .map { path =>
        Seq(s"--$EMULATE_REPOSITORY", path.toAbsolutePath.toString)
      }
      .getOrElse(Seq())
    val waitForAssets =
      if (inheritShouldWaitForAssets) Seq(s"--$EMULATE_REPOSITORY_WAIT")
      else Seq()
    repositoryPath ++ waitForAssets
  }

  /** Returns a helper class that allows to run the launcher located at the
    * provided path invoking the internal options.
    */
  def runWithNewLauncher(pathToNewLauncher: Path): Runner =
    new Runner(pathToNewLauncher)

  /** A helper class used for running the workarounds using another launcher
    * executable.
    */
  class Runner private[InternalOpts] (pathToNewLauncher: Path) {

    /** Tells the installed launcher to try to remove the old launcher
      * executable.
      *
      * It retries for a few seconds to give the process running the old
      * launcher to terminate and release the lock on its file. It overrides the
      * ENSO_RUNTIME_DIRECTORY for the launched executable to the temporary
      * directory it resides in, so that its main lock does not block removing
      * the original directory.
      */
    def removeOldExecutableAndExit(oldExecutablePath: Path): Nothing = {
      val command = Seq(
        pathToNewLauncher.toAbsolutePath.toString,
        s"--$REMOVE_OLD_EXECUTABLE",
        oldExecutablePath.toAbsolutePath.toString
      )
      val temporaryRuntimeDirectory =
        pathToNewLauncher.getParent.toAbsolutePath.normalize
      runDetachedAndExit(
        command,
        "ENSO_RUNTIME_DIRECTORY" -> temporaryRuntimeDirectory.toString
      )
    }

    /** Tells the temporary launcher to remove the original launcher executable
      * and possibly its parent directory.
      *
      * The parent directory is removed if it is empty or only contains an empty
      * `bin` directory. This is used for cases when the executable is inside of
      * the data directory and the data directory cannot be fully removed before
      * the executable has been removed.
      *
      * @param executablePath path to the old executable
      * @param parentToRemove path to the parent directory
      */
    def finishUninstall(
      executablePath: Path,
      parentToRemove: Option[Path]
    ): Nothing = {
      val parentParam = parentToRemove.map(parent =>
        Seq(s"--$FINISH_UNINSTALL_PARENT", parent.toAbsolutePath.toString)
      )
      val command = Seq(
        pathToNewLauncher.toAbsolutePath.toString,
        s"--$FINISH_UNINSTALL",
        executablePath.toAbsolutePath.toString
      ) ++ parentParam.getOrElse(Seq())
      runDetachedAndExit(command)
    }

    /** Tells the launcher to continue a multi-step upgrade.
      *
      * Creates an instance of [[LauncherUpgrader]] and invokes
      * [[LauncherUpgrader.internalContinueUpgrade]].
      *
      * @param targetVersion target version to upgrade to
      * @param originalPath path to the original launcher executable that should
      *                     be replaced with the final executable
      * @param globalCLIOptions cli options that should be inherited
      * @return exit code of the child process
      */
    def continueUpgrade(
      targetVersion: SemVer,
      originalPath: Path,
      globalCLIOptions: GlobalCLIOptions
    ): Int = {
      val inheritOpts =
        GlobalCLIOptions.toOptions(globalCLIOptions) ++ optionsToInherit
      val command = Seq(
        pathToNewLauncher.toAbsolutePath.toString,
        s"--$CONTINUE_UPGRADE",
        targetVersion.toString,
        s"--$UPGRADE_ORIGINAL_PATH",
        originalPath.toAbsolutePath.normalize.toString
      ) ++ inheritOpts
      runAndWaitForResult(command)
    }
  }

  private val retryBaseAmount = 30

  /** Tries to remove the file at `oldExecutablePath`, retrying several times if
    * needed.
    *
    * On failure retries every 0.5s for 15s in total. That retry mechanism is in
    * place, because a running executable cannot be removed and it may take some
    * time for the process to fully terminate (theoretically this time can be
    * extended indefinitely, for example if anti-virus software blocks the
    * executable for scanning, so this may still fail).
    */
  @scala.annotation.tailrec
  private def tryDeleting(oldExecutablePath: Path, retries: Int = 30): Unit = {
    try {
      Files.delete(oldExecutablePath)
    } catch {
      case _: NoSuchFileException =>
      case e: IOException =>
        def firstTime = retries == retryBaseAmount
        if (firstTime) {
          System.err.println(
            s"Could not remove $oldExecutablePath, will retry several " +
            s"times for 15s..."
          )
        }

        if (retries > 0) {
          Thread.sleep(500)
          tryDeleting(oldExecutablePath, retries - 1)
        } else {
          e.printStackTrace()
          System.err.println(
            s"Cannot delete old executable at $oldExecutablePath after " +
            s"multiple retries. Please try removing it manually."
          )
        }
    }
  }

  private def removeOldExecutable(oldExecutablePath: Path): Unit =
    tryDeleting(oldExecutablePath)

  private def finishUninstall(
    executablePath: Path,
    parentToRemove: Option[Path]
  ): Unit = {
    tryDeleting(executablePath)
    parentToRemove.foreach { parent =>
      val bin = parent / "bin"
      if (Files.exists(bin))
        FileSystem.removeDirectoryIfEmpty(bin)
      FileSystem.removeDirectoryIfEmpty(parent)
    }
  }

  private def runAndWaitForResult(command: Seq[String]): Int = {
    val pb = new java.lang.ProcessBuilder(command: _*)
    pb.inheritIO()
    pb.start().waitFor()
  }

  private def runDetachedAndExit(
    command: Seq[String],
    extraEnv: (String, String)*
  ): Nothing = {
    if (!OS.isWindows) {
      throw new IllegalStateException(
        "Internal error: Detached process workarounds are only available on " +
        "Windows."
      )
    }

    val pb = new java.lang.ProcessBuilder(command: _*)
    extraEnv.foreach(envPair => pb.environment().put(envPair._1, envPair._2))
    pb.inheritIO()
    pb.start()
    sys.exit()
  }
}
