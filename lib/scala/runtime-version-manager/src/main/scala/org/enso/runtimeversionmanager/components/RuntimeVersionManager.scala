package org.enso.runtimeversionmanager.components

import java.nio.file.{Files, Path, StandardOpenOption}
import com.typesafe.scalalogging.Logger
import nl.gn0s1s.bump.SemVer
import org.enso.cli.OS
import org.enso.distribution.{
  DistributionManager,
  FileSystem,
  TemporaryDirectoryManager
}
import org.enso.distribution.locking.{LockType, ResourceManager}
import org.enso.runtimeversionmanager.CurrentVersion
import org.enso.distribution.FileSystem.PathSyntax
import org.enso.logger.masking.MaskedPath
import org.enso.downloader.archive.Archive
import org.enso.runtimeversionmanager.locking.Resources
import org.enso.runtimeversionmanager.releases.ReleaseProvider
import org.enso.runtimeversionmanager.releases.engine.EngineRelease
import org.enso.runtimeversionmanager.releases.graalvm.GraalVMRuntimeReleaseProvider

import scala.util.control.NonFatal
import scala.util.{Failure, Success, Try, Using}

/** Manages runtime and engine components.
  *
  * Allows to find, list, install and uninstall components.
  *
  * See Note [RuntimeVersionManager Concurrency Model]
  *
  * @param userInterface a [[RuntimeVersionManagementUserInterface]] instance
  *                      that specifies how to handle user interactions
  *                      (displaying progress and handling corner cases)
  * @param distributionManager the [[DistributionManager]] to use
  * @param engineReleaseProvider the provider of engine releases
  * @param runtimeReleaseProvider the provider of runtime releases
  * @param componentConfig the runtime component configuration
  * @param componentUpdaterFactory the runtime component updater factory
  */
class RuntimeVersionManager(
  userInterface: RuntimeVersionManagementUserInterface,
  distributionManager: DistributionManager,
  temporaryDirectoryManager: TemporaryDirectoryManager,
  resourceManager: ResourceManager,
  engineReleaseProvider: ReleaseProvider[EngineRelease],
  runtimeReleaseProvider: GraalVMRuntimeReleaseProvider,
  componentConfig: RuntimeComponentConfiguration,
  componentUpdaterFactory: RuntimeComponentUpdaterFactory,
  implicit private val installerKind: InstallerKind
) {
  private val logger = Logger[RuntimeVersionManager]
  private val os     = OS.operatingSystem

  /** Tries to find a GraalVM runtime for the provided engine.
    *
    * Returns None if the runtime is missing.
    */
  def findGraalRuntime(engine: Engine): Option[GraalRuntime] =
    findGraalRuntime(engine.manifest.runtimeVersion)

  /** Finds an installed GraalVM runtime with the given `version`.
    *
    * Returns None if that version is not installed.
    */
  def findGraalRuntime(version: GraalVMVersion): Option[GraalRuntime] = {
    val name = graalRuntimeNameForVersion(version)
    val graalRuntimeOpt =
      firstExisting(distributionManager.paths.runtimeSearchPaths.map(_ / name))
        .map { path =>
          // TODO [RW] for now an exception is thrown if the installation is
          //  corrupted, in #1052 offer to repair the broken installation
          loadGraalRuntime(path).recoverWith { case e: Exception =>
            Failure(
              UnrecognizedComponentError(
                s"The runtime $version is already installed, but cannot be " +
                s"loaded due to $e. Until the launcher gets an auto-repair " +
                s"feature, please try reinstalling the runtime by " +
                s"uninstalling all engines that use it and installing them " +
                s"again, or manually removing `$path`.",
                e
              )
            )
          }.get
        }
    graalRuntimeOpt match {
      case Some(graalRuntime) =>
        logger.info("Found GraalVM runtime [{}].", graalRuntime)
      case None =>
        logger.info("GraalVM runtime [{}] not found.", version)
    }
    graalRuntimeOpt
  }

  /** Executes the provided action with a requested engine version.
    *
    * The engine is locked with a shared lock, so it is guaranteed that it will
    * not be uninstalled while the action is being executed.
    *
    * The engine will be installed if needed.
    */
  def withEngine[R](engineVersion: SemVer)(
    action: Engine => R
  ): R = {
    val engine = findOrInstallEngine(version = engineVersion)
    resourceManager.withResources(
      userInterface,
      Resources.Engine(engineVersion) -> LockType.Shared
    ) {
      engine
        .ensureValid()
        .recoverWith { error =>
          Failure(
            ComponentMissingError(
              "The engine has been removed before the command could be " +
              "started.",
              error
            )
          )
        }
        .get

      action(engine)
    }
  }

  /** Executes the provided action with a requested engine version and its
    * corresponding runtime.
    *
    * The components are locked with a shared lock, so it is guaranteed that
    * they will not be uninstalled while the action is being executed.
    *
    * The components will be installed if needed.
    */
  def withEngineAndRuntime[R](engineVersion: SemVer)(
    action: (Engine, GraalRuntime) => R
  ): R = {
    val engine  = findOrInstallEngine(version = engineVersion)
    val runtime = findOrInstallGraalRuntime(engine)
    resourceManager.withResources(
      userInterface,
      Resources.Engine(engineVersion)    -> LockType.Shared,
      Resources.Runtime(runtime.version) -> LockType.Shared
    ) {
      engine
        .ensureValid()
        .recoverWith { error =>
          Failure(
            ComponentMissingError(
              "The engine has been removed before the command could be " +
              "started.",
              error
            )
          )
        }
        .get

      runtime
        .ensureValid()
        .recoverWith { error =>
          Failure(
            ComponentMissingError(
              "The runtime has been removed before the command could be " +
              "started.",
              error
            )
          )
        }
        .get

      action(engine, runtime)
    }
  }

  /** Returns the runtime needed for the given engine, trying to install it if
    * it is missing.
    *
    * @param engine the engine for which the runtime is requested
    */
  def findOrInstallGraalRuntime(engine: Engine): GraalRuntime =
    findGraalRuntime(engine) match {
      case Some(found) => found
      case None =>
        val version = engine.manifest.runtimeVersion
        if (userInterface.shouldInstallMissingRuntime(version)) {
          resourceManager.withResources(
            userInterface,
            Resources.AddOrRemoveComponents -> LockType.Shared,
            Resources.Runtime(version)      -> LockType.Exclusive
          ) {
            installGraalRuntime(version)
          }
        } else {
          throw ComponentMissingError(
            s"No runtime for engine $engine. Cannot continue."
          )
        }
    }

  /** Returns the first path from the sequence that exists on the file system,
    * or None if no path from the sequence exists.
    */
  private def firstExisting(paths: Seq[Path]): Option[Path] =
    paths.find(Files.exists(_))

  /** Finds an installed engine with the given `version` and reports any errors.
    */
  private def getEngine(version: SemVer): Try[Engine] = {
    val name = engineNameForVersion(version)
    firstExisting(distributionManager.paths.engineSearchPaths.map(_ / name))
      .map(loadEngine)
      .getOrElse {
        Failure(ComponentMissingError(s"Engine $version is not installed."))
      }
  }

  /** Finds an engine with the given `version` or returns None if it is not
    * installed.
    *
    * Any other errors regarding loading the engine are thrown.
    * If the engine is marked as broken, a warning is reported.
    */
  def findEngine(version: SemVer): Option[Engine] =
    getEngine(version)
      .map { engine =>
        if (engine.isMarkedBroken) {
          logger.warn(
            "Running an engine release [{}] that is marked as broken. " +
            "Please consider upgrading to a stable release.",
            version
          )
        }

        Some(engine)
      }
      .recoverWith {
        case _: ComponentMissingError => Success(None)
        case e: UpgradeRequiredError  => Failure(e)
        case e: Exception =>
          Failure(
            UnrecognizedComponentError(
              s"The engine $version is already installed, but cannot be " +
              s"loaded due to $e " +
              s"Please try reinstalling by running " +
              s"`enso uninstall engine $version` followed by " +
              s"`enso install engine $version`.",
              e
            )
          )
      }
      .get

  /** Returns the engine needed with the given version, trying to install it if
    * it is missing.
    *
    * @param version the requested engine version
    */
  def findOrInstallEngine(version: SemVer): Engine =
    findEngine(version) match {
      case Some(found) =>
        logger.info("The engine [{}] found.", version)
        found
      case None =>
        if (userInterface.shouldInstallMissingEngine(version)) {
          resourceManager.withResources(
            userInterface,
            Resources.AddOrRemoveComponents -> LockType.Shared,
            Resources.Engine(version)       -> LockType.Exclusive
          ) {
            findEngine(version) match {
              case Some(engine) =>
                val message =
                  s"The engine $version has already been installed by a " +
                  s"different process."
                logger.info(message)
                userInterface.logInfo(message)
                engine
              case None =>
                logger.info("The engine [{}] not found.", version)
                installEngine(version)
            }
          }
        } else {
          throw ComponentMissingError(s"No engine $version. Cannot continue.")
        }
    }

  /** Finds installed engines that use the given `runtime`. */
  def findEnginesUsingRuntime(runtime: GraalRuntime): Seq[Engine] =
    listInstalledEngines().filter(_.manifest.runtimeVersion == runtime.version)

  /** Lists all installed GrallVM runtimes. */
  def listInstalledGraalRuntimes(): Seq[GraalRuntime] =
    findComponents(distributionManager.paths.runtimeSearchPaths)
      .map(path => (path, loadGraalRuntime(path)))
      .flatMap(handleErrorsAsWarnings[GraalRuntime]("A runtime"))

  /** Lists all installed engines. */
  def listInstalledEngines(): Seq[Engine] = {
    findComponents(distributionManager.paths.engineSearchPaths)
      .map(path => (path, loadEngine(path)))
      .flatMap(handleErrorsAsWarnings[Engine]("An engine"))
  }

  /** Returns components found in `searchPaths`.
    *
    * If there are duplicate components in multiple paths, the one from the
    * earliest search path is kept.
    */
  private def findComponents(searchPaths: Seq[Path]): Seq[Path] =
    searchPaths
      .foldLeft(Map.empty[String, Path]) { case (map, searchPath) =>
        val componentsHere =
          FileSystem.listDirectory(searchPath).filter(isNotIgnoredDirectory)
        componentsHere.foldLeft(map) { case (map, componentPath) =>
          val componentName = componentPath.getFileName.toString
          map.updatedWith(componentName) {
            case Some(alreadyPresent) => Some(alreadyPresent)
            case None                 => Some(componentPath)
          }
        }
      }
      .values
      .toSeq

  private def isNotIgnoredDirectory(path: Path): Boolean = {
    val fileName  = path.getFileName.toString
    val isIgnored = FileSystem.ignoredFileNames.contains(fileName)
    !isIgnored
  }

  /** A helper function that is used when listing components.
    *
    * A component error is non-fatal in context of listing, so it is issued as a
    * warning and the component is treated as non-existent in the list.
    */
  private def handleErrorsAsWarnings[A](name: String)(
    result: (Path, Try[A])
  ): Seq[A] =
    result match {
      case (path, Failure(exception)) =>
        logger.warn(
          "{} at [{}] has been skipped due to the following error: {}",
          name,
          path,
          exception
        )
        Seq()
      case (_, Success(value)) => Seq(value)
    }

  /** Finds the latest released version of the engine, by asking the
    * [[engineReleaseProvider]].
    */
  def fetchLatestEngineVersion(): SemVer =
    engineReleaseProvider.findLatestVersion().get

  /** Uninstalls the engine with the provided `version` (if it was installed).
    */
  def uninstallEngine(version: SemVer): Unit =
    resourceManager.withResources(
      userInterface,
      Resources.AddOrRemoveComponents -> LockType.Exclusive,
      Resources.Engine(version)       -> LockType.Exclusive
    ) {
      val engine = getEngine(version).getOrElse {
        logger.warn("Enso Engine [{}] is not installed.", version)
        throw ComponentMissingError(s"Enso Engine $version is not installed.")
      }

      if (!Files.isWritable(engine.path)) {
        val message =
          s"$engine cannot be uninstalled because it is placed in a " +
          s"read-only location (bundled versions cannot be uninstalled)."
        logger.error(message)
        throw UninstallationError(message)
      }
      safelyRemoveComponent(engine.path)
      userInterface.logInfo(s"Uninstalled $engine.")
      internalCleanupGraalRuntimes()
    }

  /** Removes runtimes that are not used by any installed engines.
    *
    * Runtimes are automatically cleaned after installation, so currently this
    * function is only useful for tests.
    */
  def cleanupRuntimes(): Unit = {
    resourceManager.withResources(
      userInterface,
      Resources.AddOrRemoveComponents -> LockType.Exclusive
    ) {
      internalCleanupGraalRuntimes()
    }
  }

  /** Checks if the component version specified in the release's manifest is
    * compatible with the current installer version.
    */
  private def isEngineVersionCompatibleWithThisInstaller(
    manifest: Manifest
  ): Boolean =
    CurrentVersion.version >= manifest.minimumRequiredVersion

  /** Installs the engine with the provided version.
    *
    * Used internally by [[findOrInstallEngine]]. Does not check if the engine
    * is already installed.
    *
    * The installation tries as much as possible to be robust - the downloaded
    * package is extracted to a temporary directory next to the `engines`
    * directory (to ensure that they are on the same filesystem) and is moved to
    * the actual directory after doing simple sanity checks.
    *
    * The caller should hold a shared lock on [[Resources.AddOrRemoveComponents]]
    * and an exclusive lock on [[Resources.Engine]]. The function itself acquires
    * [[Resources.Runtime]], but it is released before it returns.
    */
  private def installEngine(version: SemVer): Engine = {
    logger.info("Installing the engine [{}].", version)
    val engineRelease = engineReleaseProvider.fetchRelease(version).get
    val isCompatible = isEngineVersionCompatibleWithThisInstaller(
      engineRelease.manifest
    )
    if (!isCompatible) {
      throw UpgradeRequiredError(engineRelease.manifest.minimumRequiredVersion)
    }
    if (engineRelease.isBroken) {
      val continue = userInterface.shouldInstallBrokenEngine(version)
      if (!continue) {
        throw BrokenComponentError(
          "Installation has been cancelled by the user because the " +
          "requested engine release is marked as broken."
        )
      }
    }
    FileSystem.withTemporaryDirectory("enso-install") { globalTmpDirectory =>
      logger.debug("Downloading packages to [{}].", globalTmpDirectory)
      val enginePackage = globalTmpDirectory / engineRelease.packageFileName
      val downloadTask  = engineRelease.downloadPackage(enginePackage)
      userInterface.trackProgress(
        s"Downloading ${enginePackage.getFileName}.",
        downloadTask
      )
      downloadTask.force()

      val engineDirectoryName =
        engineDirectoryNameForVersion(engineRelease.version)

      val localTmpDirectory =
        temporaryDirectoryManager.temporarySubdirectory(s"engine-$version")

      val extractionTask = Archive
        .extractArchive(
          enginePackage,
          localTmpDirectory,
          Some(engineDirectoryName)
        )
      userInterface.trackProgress("Extracting the engine.", extractionTask)
      extractionTask.force()

      val engineTemporaryPath = localTmpDirectory / engineDirectoryName
      def undoTemporaryEngine(): Unit = {
        if (Files.exists(engineTemporaryPath)) {
          FileSystem.removeDirectory(engineTemporaryPath)
        }
      }

      if (engineRelease.isBroken) {
        try {
          Using(
            Files.newBufferedWriter(
              engineTemporaryPath / Manifest.DEFAULT_MANIFEST_NAME,
              StandardOpenOption.WRITE,
              StandardOpenOption.APPEND
            )
          ) { writer =>
            writer.newLine()
            writer.write(s"${Manifest.Fields.brokenMark}: true\n")
          }.get
        } catch {
          case ex: Exception =>
            undoTemporaryEngine()
            throw InstallationError(
              "Cannot add the broken mark to the installed engine's " +
              "manifest. The installation has failed.",
              ex
            )
        }
      }

      val temporaryEngine = loadEngine(engineTemporaryPath).getOrElse {
        undoTemporaryEngine()
        throw InstallationError(
          "Cannot load downloaded engine. Installation reverted."
        )
      }

      try {
        val patchedReleaseManifest =
          engineRelease.manifest.copy(brokenMark = engineRelease.isBroken)
        if (temporaryEngine.manifest != patchedReleaseManifest) {
          undoTemporaryEngine()
          throw InstallationError(
            "Manifest of installed engine does not match the published " +
            "manifest. This may lead to version inconsistencies; the package " +
            "may possibly be corrupted. Reverting installation."
          )
        }

        /** Finalizes the installation.
          *
          * Has to be called with an acquired lock for the runtime. If
          * `wasJustInstalled` is true, the lock must be exclusive and it the
          * runtime may be removed if the installation fails.
          */
        def finishInstallation(
          runtime: GraalRuntime,
          wasJustInstalled: Boolean
        ): Engine = {
          val enginePath =
            distributionManager.paths.engines / engineDirectoryName
          FileSystem.atomicMove(engineTemporaryPath, enginePath)
          val engine = getEngine(version).getOrElse {
            logger.error(
              "fatal: Could not load the installed engine." +
              "Reverting the installation."
            )
            FileSystem.removeDirectory(enginePath)
            if (wasJustInstalled && findEnginesUsingRuntime(runtime).isEmpty) {
              safelyRemoveComponent(runtime.path)
            }

            throw InstallationError(
              "fatal: Could not load the installed engine"
            )
          }

          userInterface.logInfo(s"Installed $engine.")
          engine
        }

        val runtimeVersion = temporaryEngine.graalRuntimeVersion

        /** Tries to finalize the installation assuming that the runtime was
          * installed and without acquiring an unnecessary exclusive lock.
          */
        def getEngineIfRuntimeIsInstalled: Option[Engine] =
          resourceManager.withResource(
            userInterface,
            Resources.Runtime(runtimeVersion),
            LockType.Shared
          ) {
            findGraalRuntime(runtimeVersion).map { runtime =>
              finishInstallation(runtime, wasJustInstalled = false)
            }
          }

        /** Finalizes the installation, installing the runtime if necessary.
          * This variant acquires an exclusive lock on the runtime (but it
          * should generally be called only if the runtime was not installed).
          */
        def getEngineOtherwise: Engine =
          resourceManager.withResource(
            userInterface,
            Resources.Runtime(runtimeVersion),
            LockType.Exclusive
          ) {
            val (runtime, wasJustInstalled) = findGraalRuntime(runtimeVersion)
              .map((_, false))
              .getOrElse((installGraalRuntime(runtimeVersion), true))

            finishInstallation(runtime, wasJustInstalled = wasJustInstalled)
          }

        getEngineIfRuntimeIsInstalled.getOrElse(getEngineOtherwise)
      } catch {
        case e: Exception =>
          undoTemporaryEngine()
          throw e
      }
    }
  }

  /** Returns name of the directory containing the engine of that version.
    */
  private def engineNameForVersion(version: SemVer): String =
    version.toString

  /** Returns name of the directory containing the runtime of that version.
    */
  private def graalRuntimeNameForVersion(version: GraalVMVersion): String =
    s"graalvm-ce-java${version.java}-${version.graalVersion}"

  /** Loads the GraalVM runtime definition.
    */
  private def loadGraalRuntime(path: Path): Try[GraalRuntime] = {
    logger.debug("Loading Graal runtime [{}].", path)
    val name = path.getFileName.toString
    for {
      version <- parseGraalRuntimeVersionString(name)
        .toRight(
          UnrecognizedComponentError(s"Invalid runtime component name `$name`.")
        )
        .toTry
      runtime = GraalRuntime(version, path)
      _ <- runtime.ensureValid()
      _ <- installRequiredRuntimeComponents(runtime).recover {
        case NonFatal(error) =>
          logger.warn(
            "Failed to install required components on the existing [{}]. " +
            "Some language features may be unavailable. {}",
            runtime,
            error.getMessage
          )
      }
    } yield runtime
  }

  /** Gets the runtime version from its name.
    */
  private def parseGraalRuntimeVersionString(
    name: String
  ): Option[GraalVMVersion] = {
    val regex = """graalvm-ce-java(\d+)-(.+)""".r
    name match {
      case regex(javaVersionString, graalVersionString) =>
        Some(GraalVMVersion(graalVersionString, javaVersionString))
      case _ =>
        logger.warn(
          s"Unrecognized runtime name `$name`."
        )
        None
    }
  }

  /** Loads the engine definition.
    */
  private def loadEngine(path: Path): Try[Engine] =
    for {
      version  <- parseEngineVersion(path)
      manifest <- loadAndCheckEngineManifest(path)
      engine = Engine(version, path, manifest)
      _ <- engine.ensureValid()
    } yield engine

  /** Gets the engine version from its path.
    */
  private def parseEngineVersion(path: Path): Try[SemVer] = {
    val name = path.getFileName.toString
    SemVer(name)
      .toRight(
        UnrecognizedComponentError(
          s"Invalid engine component version `$name`."
        )
      )
      .toTry
  }

  /** Loads the engine manifest, checking if that release is compatible with the
    * currently running launcher.
    */
  private def loadAndCheckEngineManifest(
    path: Path
  ): Try[Manifest] = {
    Manifest.load(path / Manifest.DEFAULT_MANIFEST_NAME).flatMap { manifest =>
      if (!isEngineVersionCompatibleWithThisInstaller(manifest)) {
        Failure(UpgradeRequiredError(manifest.minimumRequiredVersion))
      } else Success(manifest)
    }
  }

  /** Installs the runtime with the provided version.
    *
    * Does not check if the runtime is already installed.
    *
    * The installation tries as much as possible to be robust - the downloaded
    * package is extracted to a temporary directory next to the `runtimes`
    * directory (to ensure that they are on the same filesystem) and is moved to
    * the actual directory after doing simple sanity checks.
    *
    * The caller should hold a shared lock for
    * [[Resources.AddOrRemoveComponents]] and an exclusive lock for
    * [[Resources.Runtime]].
    */
  private def installGraalRuntime(
    runtimeVersion: GraalVMVersion
  ): GraalRuntime =
    FileSystem.withTemporaryDirectory("enso-install-runtime") { directory =>
      logger.info("Installing GraalVM runtime [{}].", runtimeVersion)
      val runtimePackage =
        directory / runtimeReleaseProvider.packageFileName(runtimeVersion)
      val downloadTask =
        runtimeReleaseProvider.downloadPackage(runtimeVersion, runtimePackage)
      logger.debug("Downloading [{}].", runtimePackage.getFileName)
      userInterface.trackProgress(
        s"Downloading ${runtimePackage.getFileName}.",
        downloadTask
      )
      downloadTask.force()

      val runtimeDirectoryName = graalDirectoryForVersion(runtimeVersion)
      val localTmpDirectory =
        temporaryDirectoryManager.temporarySubdirectory(
          s"runtime-${runtimeVersion.graalVersion}-java${runtimeVersion.java}"
        )

      val extractionTask = Archive.extractArchive(
        runtimePackage,
        localTmpDirectory,
        Some(runtimeDirectoryName)
      )
      logger.debug("Extracting [{}].", runtimePackage)
      userInterface.trackProgress("Extracting the runtime.", extractionTask)
      extractionTask.force()

      val runtimeTemporaryPath = localTmpDirectory / runtimeDirectoryName

      def undoTemporaryRuntime(): Unit = {
        if (Files.exists(runtimeTemporaryPath)) {
          FileSystem.removeDirectory(runtimeTemporaryPath)
        }
      }

      try {
        logger.debug("Loading temporary runtime [{}].", runtimeTemporaryPath)
        val temporaryRuntime =
          loadGraalRuntime(runtimeTemporaryPath).recoverWith { error =>
            Failure(
              InstallationError(
                "Cannot load the installed runtime. The package may have " +
                "been corrupted. Reverting installation.",
                error
              )
            )
          }.get
        logger.debug("Installing GraalVM components to [{}].", temporaryRuntime)
        installRequiredRuntimeComponents(temporaryRuntime).recoverWith {
          error =>
            Failure(
              InstallationError(
                "fatal: Cannot install the required runtime components.",
                error
              )
            )
        }.get

        val runtimePath =
          distributionManager.paths.runtimes / runtimeDirectoryName
        logger.debug(
          "Moving [{}] to [{}].",
          runtimeTemporaryPath,
          runtimePath
        )
        FileSystem.atomicMove(runtimeTemporaryPath, runtimePath)
        val runtime = loadGraalRuntime(runtimePath).recoverWith { error =>
          FileSystem.removeDirectory(runtimePath)
          Failure(
            InstallationError(
              "fatal: Cannot load the installed runtime.",
              error
            )
          )
        }.get
        logger.debug("Installed [{}].", runtime)
        userInterface.logInfo(s"Installed $runtime.")

        runtime
      } catch {
        case NonFatal(e) =>
          undoTemporaryRuntime()
          throw e
      }
    }

  /** Install components required for the specified runtime on the specified OS.
    *
    * @param runtime the GraalVM runtime
    */
  private def installRequiredRuntimeComponents(
    runtime: GraalRuntime
  ): Try[Unit] = {
    logger.debug("Installing GraalVM components [{}, {}].", runtime, os)
    val cu = componentUpdaterFactory.build(runtime)
    val requiredComponents =
      componentConfig.getRequiredComponents(runtime.version, os)

    if (requiredComponents.isEmpty) Success(())
    else {
      for {
        installedComponents <- cu.list
        _ = logger.debug(
          "Available GraalVM components: [{}].",
          installedComponents
        )
        missingComponents = requiredComponents.diff(installedComponents)
        _ <- cu.install(missingComponents)
      } yield ()
    }
  }

  private def engineDirectoryNameForVersion(version: SemVer): Path =
    Path.of(version.toString())

  private def graalDirectoryForVersion(version: GraalVMVersion): Path =
    Path.of(s"graalvm-ce-java${version.java}-${version.graalVersion}")

  /** Removes runtimes that are not used by any installed engines.
    *
    * The caller must hold [[Resources.AddOrRemoveComponents]] exclusively.
    */
  private def internalCleanupGraalRuntimes(): Unit = {
    for (runtime <- listInstalledGraalRuntimes()) {
      if (findEnginesUsingRuntime(runtime).isEmpty) {
        userInterface.logInfo(
          s"Removing $runtime, because it is not used by any installed Enso " +
          s"versions."
        )
        if (Files.isWritable(runtime.path)) {
          safelyRemoveComponent(runtime.path)
        } else {
          logger.warn(
            "{} cannot be uninstalled because it is placed in a " +
            "read-only location.",
            runtime
          )
        }
      }
    }
  }

  /** Tries to remove a component in a safe way.
    *
    * The component is moved (hopefully atomically) to temporary directory next
    * to the actual components directories and only then it is removed from
    * there. As the move should be executed as a single operation, there is no
    * risk that the system may leave the component corrupted if the deletion
    * were abruptly terminated. The component may be corrupted while being
    * removed, but it will already be in the temporary directory, so it will be
    * unreachable. The temporary directory is cleaned when doing
    * installation-related operations.
    *
    * The caller should hold an exclusive lock for
    * [[Resources.AddOrRemoveComponents]] or otherwise guarantee that this
    * component can be safely removed. The latter is an exception that only
    * happens when uninstalling a just-installed runtime when an engine
    * installation failed. In that case the installer has an exclusive lock on
    * that runtime and it was installed by it, so nobody else could be using it.
    * In all other situations the lock is strictly required.
    */
  private def safelyRemoveComponent(path: Path): Unit = {
    val temporaryPath =
      temporaryDirectoryManager.temporarySubdirectory(path.getFileName.toString)
    FileSystem.atomicMove(path, temporaryPath / "tmp")
    FileSystem.removeDirectory(temporaryPath)
  }

  /** Logs on trace level all installed engines and runtimes.
    *
    * Useful for debugging.
    */
  def logAvailableComponentsForDebugging(): Unit = logger.whenTraceEnabled {
    logger.trace("Discovering available components...")
    val engines = for (engine <- listInstalledEngines()) yield {
      val runtime = findGraalRuntime(engine)
      val runtimeName = runtime
        .map(_.toString)
        .getOrElse("no runtime found")
      val broken = if (engine.isMarkedBroken) " (broken)" else ""
      s" - Enso ${engine.version}$broken [runtime: $runtimeName] " +
      s"[location: ${MaskedPath(engine.path).applyMasking()}]"
    }

    val runtimes =
      for (runtime <- listInstalledGraalRuntimes())
        yield s" - $runtime [location: " +
        s"${MaskedPath(runtime.path).applyMasking()}]"

    logger.trace(
      s"Installed engines (${engines.length}):\n${engines.mkString("\n")}\n\n" +
      s"Installed runtimes (${runtimes.length}):\n${runtimes.mkString("\n")}"
    )
  }
}

/* Note [RuntimeVersionManager Concurrency Model]
 * ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
 * The [[RuntimeVersionManager]] itself is not synchronized as it should be used in
 * the launcher from a single thread. However, multiple launcher instances may
 * run at the same time and their operations have to be synchronized to some
 * extent, especially to avoid two processes modifying the same engine version.
 *
 * The concurrency is managed as follows:
 *
 * 1. `withEngine` and `withEngineAndRuntime` acquire shared locks for the
 *    engine (and in the second case also for the runtime) and can be used to
 *    safely run a process using the engine ensuring that the engine will not be
 *    uninstalled while that process is running. As the locks are shared,
 *    multiple processes can use the same engine or runtime for running. These
 *    functions may install the missing engine or runtime as described below.
 *    The installation acquires an exclusive lock for the component, but to
 *    avoid keeping that lock when launching a possibly long-running process, it
 *    is released and a shared lock is re-acquired, so that other processes can
 *    start using the newly installed engine in parallel. As the underlying
 *    mechanism does not support lock downgrade, the lock is released for a very
 *    short time. Theoretically, it is possible for an uninstall command to
 *    start in this short time-window and remove the just-installed component.
 *    To avoid errors, after re-acquiring the shared lock, the engine and
 *    runtime components are re-checked to ensure that they have not been
 *    uninstalled in the meantime. In the unlikely case that one of them was
 *    removed, an error is reported and execution is terminated.
 * 2. `uninstallEngine` acquires an exclusive lock on the affected engine (so no
 *    other process can be using it) and removes it. Additionally it runs a
 *    runtime cleanup, removing any runtimes that are not used anymore. For that
 *    cleanup to be safe, add-remove-components lock is acquired which
 *    guarantees that no other installations are run in parallel.
 * 2. `findOrInstallEngine` and `findOrInstallGraalRuntime` do not normally use
 *    locking, so their result is immediately invalidated - if the caller wants
 *    to have any guarantees they have to acquire a lock *after* getting the
 *    result and re-check its validity, as described in (1). In case the
 *    requested component is missing, an exclusive lock for it is acquired along
 *    with a shared lock for add-remove-components for the time of the
 *    installation, but all locks are released before returning from this
 *    function.
 * 3. `installGraalRuntime` does not acquire any locks, it relies on its caller
 *    to acquire an exclusive lock for add-remove-components and the affected
 *    runtime.
 * 4. `installEngine` relies on the caller to acquire an exclusive lock for
 *    add-remove-components and the affected engine, but it may itself acquire a
 *    a shared or exclusive lock for the runtime (the exclusive lock is acquired
 *    if the runtime is missing and has to be installed).
 * 5. Other functions do not acquire or require any locks. Their results are
 *    immediately invalidated, so they should only be used for non-safety
 *    critical operations, like listing available engines.
 *
 * To summarize, components management (install and uninstall) is synchronized
 * for safety. Other operations are mostly unsynchronized. If a guarantee is
 * expected that the engine (and possibly its runtime) are not modified when
 * running a command, `withEngine` or `withEngineAndRuntime` should be used.
 */
