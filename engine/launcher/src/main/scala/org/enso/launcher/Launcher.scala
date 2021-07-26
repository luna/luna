package org.enso.launcher

import java.nio.file.Path
import com.typesafe.scalalogging.Logger
import io.circe.Json
import nl.gn0s1s.bump.SemVer
import org.enso.distribution.EditionManager
import org.enso.runtimeversionmanager.CurrentVersion
import org.enso.runtimeversionmanager.config.{
  DefaultVersion,
  GlobalConfigurationManager
}
import org.enso.runtimeversionmanager.runner.{
  JVMSettings,
  LanguageServerOptions,
  WhichEngine
}
import org.enso.launcher.cli.{GlobalCLIOptions, LauncherLogging, Main}
import org.enso.launcher.components.LauncherRunner
import org.enso.launcher.distribution.DefaultManagers._
import org.enso.launcher.distribution.LauncherEnvironment
import org.enso.launcher.installation.DistributionInstaller.BundleAction
import org.enso.launcher.installation.{
  DistributionInstaller,
  DistributionUninstaller
}
import org.enso.launcher.project.ProjectManager
import org.enso.launcher.upgrade.LauncherUpgrader
import org.enso.loggingservice.LogLevel
import org.enso.version.{VersionDescription, VersionDescriptionParameter}

/** Implements launcher commands that are run from CLI and can be affected by
  * the global CLI options.
  *
  * @param cliOptions the global CLI options to use for the commands
  */
case class Launcher(cliOptions: GlobalCLIOptions) {

  private val logger = Logger[Launcher]

  private lazy val componentsManager = {
    val manager =
      runtimeVersionManager(cliOptions, alwaysInstallMissing = false)
    manager.logAvailableComponentsForDebugging()
    manager
  }
  private lazy val configurationManager =
    new GlobalConfigurationManager(componentsManager, distributionManager)
  private lazy val editionManager = EditionManager(distributionManager)
  private lazy val projectManager = new ProjectManager
  private lazy val runner =
    new LauncherRunner(
      projectManager,
      distributionManager,
      configurationManager,
      componentsManager,
      editionManager,
      LauncherEnvironment,
      LauncherLogging.loggingServiceEndpoint()
    )
  private lazy val upgrader = LauncherUpgrader.default(cliOptions)
  upgrader.runCleanup(isStartup = true)

  /** Creates a new project with the given `name` in the given `path`.
    *
    * If `path` is not set, the project is created in a directory called `name`
    * in the current directory.
    *
    * If `author.name` or `author.email` are set in the global config, their
    * values are used to set a default author and maintainer for the created
    * project.
    */
  def newProject(
    name: String,
    path: Option[Path],
    versionOverride: Option[SemVer],
    useSystemJVM: Boolean,
    jvmOpts: Seq[(String, String)],
    additionalArguments: Seq[String]
  ): Int = {
    val actualPath = path.getOrElse(Launcher.workingDirectory.resolve(name))
    val version =
      versionOverride.getOrElse(configurationManager.defaultVersion)
    val globalConfig = configurationManager.getConfig

    val exitCode = runner
      .withCommand(
        runner
          .newProject(
            path                = actualPath,
            name                = name,
            engineVersion       = version,
            authorName          = globalConfig.authorName,
            authorEmail         = globalConfig.authorEmail,
            additionalArguments = additionalArguments
          )
          .get,
        JVMSettings(useSystemJVM, jvmOpts)
      ) { command =>
        command.run().get
      }

    if (exitCode == 0) {
      InfoLogger.info(
        s"Project created in `$actualPath` using version $version."
      )
    } else {
      logger.error("Project creation failed.")
    }

    exitCode
  }

  /** Prints a list of installed engines.
    */
  def listEngines(): Int = {
    for (engine <- componentsManager.listInstalledEngines()) {
      val broken = if (engine.isMarkedBroken) " (broken)" else ""
      println(engine.version.toString + broken)
    }
    0
  }

  /** Prints a list of installed runtimes.
    */
  def listRuntimes(): Int = {
    for (runtime <- componentsManager.listInstalledGraalRuntimes()) {
      val engines = componentsManager.findEnginesUsingRuntime(runtime)
      val usedBy = {
        val plural =
          if (engines.length != 1) "s"
          else ""
        s"(used by ${engines.length} Enso installation$plural)"
      }
      println(s"$runtime $usedBy")
    }
    0
  }

  /** Prints a summary of installed components and their dependencies.
    */
  def listSummary(): Int = {
    for (engine <- componentsManager.listInstalledEngines()) {
      val runtime = componentsManager.findGraalRuntime(engine)
      val runtimeName = runtime
        .map(_.toString)
        .getOrElse("no runtime found for this distribution")
      val broken = if (engine.isMarkedBroken) " (broken)" else ""
      println(s"Enso ${engine.version}$broken -> $runtimeName")
    }
    0
  }

  /** Installs the specified engine `version`.
    *
    * Also installs the required runtime if it wasn't already installed.
    */
  def installEngine(version: SemVer): Int = {
    val installingComponentManager =
      runtimeVersionManager(cliOptions, alwaysInstallMissing = true)
    val existing = installingComponentManager.findEngine(version)
    if (existing.isDefined) {
      InfoLogger.info(s"Engine $version is already installed.")
    } else {
      installingComponentManager.findOrInstallEngine(version)
    }
    0
  }

  /** Installs the latest available version of the engine.
    *
    * Also installs the required runtime if it wasn't already installed.
    */
  def installLatestEngine(): Int = {
    val latest = componentsManager.fetchLatestEngineVersion()
    InfoLogger.info(s"Installing Enso engine $latest.")
    installEngine(latest)
  }

  /** Uninstalls the specified engine `version`.
    *
    * If a runtime is not used by any engines anymore, it is also removed.
    */
  def uninstallEngine(version: SemVer): Int = {
    componentsManager.uninstallEngine(version)
    distributionManager.tryCleaningUnusedLockfiles()
    0
  }

  /** Runs the Enso REPL.
    *
    * If ran outside of a project, uses the default configured version. If run
    * inside a project or provided with an explicit projectPath, the Enso
    * version associated with the project is run.
    *
    * @param projectPath if provided, the REPL is run in context of that project
    * @param versionOverride if provided, overrides the default engine version
    *                        that would have been used
    * @param logLevel log level for the engine
    * @param useSystemJVM if set, forces to use the default configured JVM,
    *                     instead of the JVM associated with the engine version
    * @param jvmOpts additional options to pass to the launched JVM
    * @param additionalArguments additional arguments to pass to the runner
    * @return exit code of the launched program
    */
  def runRepl(
    projectPath: Option[Path],
    versionOverride: Option[SemVer],
    logLevel: LogLevel,
    useSystemJVM: Boolean,
    jvmOpts: Seq[(String, String)],
    additionalArguments: Seq[String]
  ): Int = {
    val exitCode = runner
      .withCommand(
        runner
          .repl(
            projectPath,
            versionOverride,
            logLevel,
            cliOptions.internalOptions.logMasking,
            additionalArguments
          )
          .get,
        JVMSettings(useSystemJVM, jvmOpts)
      ) { command =>
        command.run().get
      }
    exitCode
  }

  /** Runs an Enso script or project.
    *
    * If ran inside a project without a path, or with a path pointing to a
    * project, runs that project. If the provided path points to a file, that
    * file is executed as an Enso script. If the file is located inside of a
    * project, it is executed in the context of that project. Otherwise it is
    * run as a standalone script and the default engine version is used.
    *
    * @param path specifies what to run
    * @param versionOverride if provided, overrides the default engine version
    *                        that would have been used
    * @param logLevel log level for the engine
    * @param useSystemJVM if set, forces to use the default configured JVM,
    *                     instead of the JVM associated with the engine version
    * @param jvmOpts additional options to pass to the launched JVM
    * @param additionalArguments additional arguments to pass to the runner
    * @return exit code of the launched program
    */
  def runRun(
    path: Option[Path],
    versionOverride: Option[SemVer],
    logLevel: LogLevel,
    useSystemJVM: Boolean,
    jvmOpts: Seq[(String, String)],
    additionalArguments: Seq[String]
  ): Int = {
    val exitCode = runner
      .withCommand(
        runner
          .run(
            path,
            versionOverride,
            logLevel,
            cliOptions.internalOptions.logMasking,
            additionalArguments
          )
          .get,
        JVMSettings(useSystemJVM, jvmOpts)
      ) { command =>
        command.run().get
      }
    exitCode
  }

  /** Runs the Language Server.
    *
    * Unless overridden, uses the Enso version associated with the project
    * located at `options.path`.
    *
    * @param options configuration required by the language server
    * @param versionOverride if provided, overrides the default engine version
    *                        that would have been used
    * @param logLevel log level for the language server
    * @param useSystemJVM if set, forces to use the default configured JVM,
    *                     instead of the JVM associated with the engine version
    * @param jvmOpts additional options to pass to the launched JVM
    * @param additionalArguments additional arguments to pass to the runner
    * @return exit code of the launched program
    */
  def runLanguageServer(
    options: LanguageServerOptions,
    contentRoot: Path,
    versionOverride: Option[SemVer],
    logLevel: LogLevel,
    logMasking: Boolean,
    useSystemJVM: Boolean,
    jvmOpts: Seq[(String, String)],
    additionalArguments: Seq[String]
  ): Int = {
    val exitCode = runner
      .withCommand(
        runner
          .languageServer(
            options,
            contentRoot,
            versionOverride,
            logLevel,
            logMasking,
            additionalArguments
          )
          .get,
        JVMSettings(useSystemJVM, jvmOpts)
      ) { command =>
        command.run().get
      }
    exitCode
  }

  /** Updates the global configuration.
    *
    * If `value` is an empty string, the `key` is removed from the configuration
    * (if it exists). If `value` is non-empty the key is added or updated in the
    * config. Any updates that set a known key to an invalid value which would
    * prevent from loading the config are cancelled.
    */
  def updateConfig(key: String, value: String): Int = {
    if (value.isEmpty) {
      configurationManager.removeFromConfig(key)
      InfoLogger.info(
        s"""Key `$key` removed from the global configuration file """ +
        s"(${configurationManager.configLocation.toAbsolutePath})."
      )
    } else {
      configurationManager.updateConfigRaw(key, Json.fromString(value))
      InfoLogger.info(
        s"""Key `$key` set to "$value" in the global configuration file """ +
        s"(${configurationManager.configLocation.toAbsolutePath})."
      )
    }
    0
  }

  /** Prints the value of `key` from the global configuration.
    *
    * If the `key` is not set in the config, sets exit code to 1 and prints a
    * warning.
    */
  def printConfig(key: String): Int = {
    configurationManager.getConfig.original.apply(key) match {
      case Some(value) =>
        println(value)
        0
      case None =>
        logger.warn(s"Key $key is not set in the global config.")
        1
    }
  }

  /** Sets the default Enso version.
    */
  def setDefaultVersion(defaultVersion: DefaultVersion): Int = {
    configurationManager.updateConfig { config =>
      config.copy(defaultVersion = defaultVersion)
    }

    defaultVersion match {
      case DefaultVersion.LatestInstalled =>
        InfoLogger.info(
          s"Default Enso version set to the latest installed version, " +
          s"currently ${configurationManager.defaultVersion}."
        )
      case DefaultVersion.Exact(version) =>
        InfoLogger.info(s"Default Enso version set to $version.")
    }

    0
  }

  /** Prints the default Enso version.
    */
  def printDefaultVersion(): Int = {
    println(configurationManager.defaultVersion)
    0
  }

  /** Installs the Enso distribution.
    */
  def installDistribution(
    doNotRemoveOldLauncher: Boolean,
    bundleAction: Option[BundleAction]
  ): Int = {
    if (!distributionManager.isRunningPortable) {
      Logger[Launcher].error(
        "install distribution can only be used from within a portable " +
        "distribution. It appears that you are not running a portable " +
        "distribution."
      )
      1
    } else {
      DistributionInstaller
        .default(
          globalCLIOptions   = cliOptions,
          removeOldLauncher  = !doNotRemoveOldLauncher,
          bundleActionOption = bundleAction
        )
        .install()
      0
    }
  }

  /** Uninstalls the Enso distribution.
    */
  def uninstallDistribution(): Int = {
    DistributionUninstaller.default(cliOptions).uninstall()
    0
  }

  /** Displays the version string of the launcher.
    *
    * @param hideEngineVersion if set, does not look for installed engines to
    *                          display the current version; this can be used to
    *                          avoid making network requests
    */
  def displayVersion(
    hideEngineVersion: Boolean = false
  ): Int = {
    val useJSON = cliOptions.useJSON
    val runtimeVersionParameter =
      if (hideEngineVersion) None else Some(getEngineVersion(useJSON))

    val versionDescription = VersionDescription.make(
      "Enso Launcher",
      includeRuntimeJVMInfo         = false,
      enableNativeImageOSWorkaround = true,
      additionalParameters          = runtimeVersionParameter.toSeq,
      customVersion                 = Some(CurrentVersion.version.toString)
    )

    println(versionDescription.asString(useJSON))
    0
  }

  private def getEngineVersion(
    useJSON: Boolean
  ): VersionDescriptionParameter = {
    val (runtimeVersionRunSettings, whichEngine) = runner.version(useJSON).get

    val isEngineInstalled =
      componentsManager
        .findEngine(runtimeVersionRunSettings.engineVersion)
        .isDefined
    val runtimeVersionString = if (isEngineInstalled) {
      val output = runner.withCommand(
        runtimeVersionRunSettings,
        JVMSettings(useSystemJVM = false, jvmOptions = Seq.empty)
      ) { runtimeVersionCommand =>
        runtimeVersionCommand.captureOutput().get
      }

      if (useJSON) output else "\n" + output.stripTrailing()
    } else {
      if (useJSON) "null"
      else "Not installed."
    }

    VersionDescriptionParameter(
      humanReadableName = whichEngine match {
        case WhichEngine.FromProject(name) =>
          s"Enso engine from project $name"
        case WhichEngine.Default => "Current default Enso engine"
      },
      jsonName = "runtime",
      value    = runtimeVersionString
    )
  }

  /** Performs a self-upgrade.
    *
    * If a `version` is specified, installs that version. If the version is
    * older than the current one, a downgrade is performed. If no `version` is
    * specified, the latest available version is chosen, unless it is older than
    * the current one.
    */
  def upgrade(version: Option[SemVer]): Int = {
    val targetVersion       = version.getOrElse(upgrader.latestVersion().get)
    val isManuallyRequested = version.isDefined
    if (targetVersion == CurrentVersion.version) {
      InfoLogger.info("Already up-to-date.")
      0
    } else if (targetVersion < CurrentVersion.version && !isManuallyRequested) {
      logger.warn(
        s"The latest available version is $targetVersion, but you are " +
        s"running ${CurrentVersion.version} which is more recent."
      )
      InfoLogger.info(
        s"If you really want to downgrade, please run " +
        s"`enso upgrade $targetVersion`."
      )
      1
    } else {
      upgrader.upgrade(targetVersion)
      0
    }
  }
}

/** Gathers launcher commands which do not depend on the global CLI options.
  */
object Launcher {
  private val workingDirectory: Path = Path.of(".")

  /** Checks if the launcher is running in portable mode and exits if it is not.
    */
  def ensurePortable(): Unit = {
    if (!distributionManager.isRunningPortable) {
      Logger[Launcher].error(
        "`--ensure-portable` is set, but the launcher is not running in " +
        "portable mode. Terminating."
      )
      Main.exit(1)
    }
  }
}
