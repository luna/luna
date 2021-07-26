package org.enso.runtimeversionmanager.runner

import akka.http.scaladsl.model.Uri
import com.typesafe.scalalogging.Logger
import nl.gn0s1s.bump.SemVer
import org.enso.distribution.{DistributionManager, EditionManager, Environment}
import org.enso.editions.{DefaultEnsoVersion, SemVerEnsoVersion}
import org.enso.logger.masking.MaskedString
import org.enso.loggingservice.LogLevel
import org.enso.runtimeversionmanager.components.Manifest.JVMOptionsContext
import org.enso.runtimeversionmanager.components.{
  Engine,
  GraalRuntime,
  RuntimeVersionManager
}
import org.enso.runtimeversionmanager.config.GlobalConfigurationManager

import java.nio.file.Path
import scala.concurrent.duration.DurationInt
import scala.concurrent.{Await, Future, TimeoutException}
import scala.util.Try

/** A helper class that prepares settings for running Enso components and
  * converts these settings to actual commands that launch the component inside
  * of a JVM.
  */
class Runner(
  runtimeVersionManager: RuntimeVersionManager,
  distributionManager: DistributionManager,
  globalConfigurationManager: GlobalConfigurationManager,
  editionManager: EditionManager,
  environment: Environment,
  loggerConnection: Future[Option[Uri]]
) {

  /** The current working directory that is a starting point when checking if
    * the command is launched inside of a project.
    *
    * Can be overridden in tests.
    */
  protected val currentWorkingDirectory: Path = Path.of(".")

  def newProject(
    path: Path,
    name: String,
    engineVersion: SemVer,
    authorName: Option[String],
    authorEmail: Option[String],
    additionalArguments: Seq[String]
  ): Try[RunSettings] =
    Try {
      val authorNameOption =
        authorName.map(Seq("--new-project-author-name", _)).getOrElse(Seq())
      val authorEmailOption =
        authorEmail.map(Seq("--new-project-author-email", _)).getOrElse(Seq())
      val arguments =
        Seq(
          "--new",
          path.toAbsolutePath.normalize.toString,
          "--new-project-name",
          name
        ) ++ authorNameOption ++ authorEmailOption ++ additionalArguments
      // TODO [RW] reporting warnings to the IDE (#1710)
      if (Engine.isNightly(engineVersion)) {
        Logger[Runner].warn(
          "Creating a new project using a nightly build [{}]. " +
          "Nightly builds may disappear after a while, so you may need to " +
          "upgrade. Consider using a stable version.",
          engineVersion
        )
      }
      RunSettings(engineVersion, arguments, connectLoggerIfAvailable = false)
    }

  /** Creates [[RunSettings]] for launching the Language Server. */
  def startLanguageServer(
    options: LanguageServerOptions,
    project: Project,
    versionOverride: Option[SemVer],
    logLevel: LogLevel,
    logMasking: Boolean,
    additionalArguments: Seq[String]
  ): Try[RunSettings] = {
    val version     = resolveVersion(versionOverride, Some(project))
    val projectPath = project.path.toAbsolutePath.normalize.toString
    startLanguageServer(
      options,
      projectPath,
      version,
      logLevel,
      logMasking,
      additionalArguments
    )
  }

  /** Creates [[RunSettings]] for launching the Language Server. */
  def startLanguageServer(
    options: LanguageServerOptions,
    projectPath: String,
    version: SemVer,
    logLevel: LogLevel,
    logMasking: Boolean,
    additionalArguments: Seq[String]
  ): Try[RunSettings] =
    Try {
      val arguments = Seq(
        "--server",
        "--root-id",
        options.rootId.toString,
        "--path",
        projectPath,
        "--interface",
        options.interface,
        "--rpc-port",
        options.rpcPort.toString,
        "--data-port",
        options.dataPort.toString,
        "--log-level",
        logLevel.name
      ) ++
        Option.unless(logMasking)("--no-log-masking")
      RunSettings(
        version,
        arguments ++ additionalArguments,
        connectLoggerIfAvailable = true
      )
    }

  final private val JVM_OPTIONS_ENV_VAR = "ENSO_JVM_OPTS"

  /** Runs an action giving it a command that can be used to launch the
    * component.
    *
    * While the action is executed, it is guaranteed that the component
    * referenced by the command is available. The command is considered invalid
    * after the `action` exits.
    *
    * Combines the [[RunSettings]] for the runner with the [[JVMSettings]] for
    * the underlying JVM to get the full command for launching the component.
    */
  def withCommand[R](runSettings: RunSettings, jvmSettings: JVMSettings)(
    action: Command => R
  ): R = {
    def prepareAndRunCommand(engine: Engine, javaCommand: JavaCommand): R = {
      val jvmOptsFromEnvironment = environment.getEnvVar(JVM_OPTIONS_ENV_VAR)
      jvmOptsFromEnvironment.foreach { opts =>
        Logger[Runner].debug(
          "Picking up additional JVM options [{}] from the " +
          "[{}] environment variable.",
          MaskedString(opts),
          JVM_OPTIONS_ENV_VAR
        )
      }

      val runnerJar = engine.runnerPath.toAbsolutePath.normalize.toString

      def translateJVMOption(option: (String, String)): String = {
        val name  = option._1
        val value = option._2
        s"-D$name=$value"
      }

      val context = JVMOptionsContext(enginePackagePath = engine.path)

      val manifestOptions =
        engine.defaultJVMOptions.filter(_.isRelevant).map(_.substitute(context))
      val environmentOptions =
        jvmOptsFromEnvironment.map(_.split(' ').toIndexedSeq).getOrElse(Seq())
      val commandLineOptions = jvmSettings.jvmOptions.map(translateJVMOption)

      val jvmArguments =
        manifestOptions ++ environmentOptions ++ commandLineOptions ++
        Seq("-jar", runnerJar)

      val loggingConnectionArguments =
        if (runSettings.connectLoggerIfAvailable)
          forceLoggerConnectionArguments()
        else Seq()

      val command = Seq(javaCommand.executableName) ++
        jvmArguments ++ loggingConnectionArguments ++ runSettings.runnerArguments

      val distributionSettings =
        distributionManager.getEnvironmentToInheritSettings
      val extraEnvironmentOverrides =
        javaCommand.javaHomeOverride
          .map("JAVA_HOME" -> _)
          .toSeq ++ distributionSettings.toSeq

      action(Command(command, extraEnvironmentOverrides))
    }

    val engineVersion = runSettings.engineVersion
    jvmSettings.javaCommandOverride match {
      case Some(overriddenCommand) =>
        runtimeVersionManager.withEngine(engineVersion) { engine =>
          prepareAndRunCommand(engine, overriddenCommand)
        }
      case None =>
        runtimeVersionManager.withEngineAndRuntime(engineVersion) {
          (engine, runtime) =>
            prepareAndRunCommand(engine, JavaCommand.forRuntime(runtime))
        }
    }
  }

  /** Returns arguments that should be added to a launched component to connect
    * it to launcher's logging service.
    *
    * It waits until the logging service has been set up and should be called as
    * late as possible (for example after installing any required components) to
    * avoid blocking other actions by the logging service setup.
    *
    * If the logging service is not available in 3 seconds after calling this
    * method, it assumes that it failed to boot and returns arguments that will
    * cause the launched component to use its own local logging.
    */
  private def forceLoggerConnectionArguments(): Seq[String] = {
    val connectionSetting = {
      try {
        Await.result(loggerConnection, 3.seconds)
      } catch {
        case exception: TimeoutException =>
          Logger[GraalRuntime].warn(
            "The logger has not been set up within the 3 second time limit, " +
            "the launched component will be started but it will not be " +
            "connected to the logging service.",
            exception
          )
          None
      }
    }
    connectionSetting match {
      case Some(uri) => Seq("--logger-connect", uri.toString)
      case None      => Seq()
    }
  }

  /** Resolves the engine version that should be used for the run.
    *
    * If `versionOverride` is defined it has the highest priority and its
    * version is returned. Otherwise if the project is defined, the version from
    * the project configuration (which may also fall back to default) is chosen.
    * Otherwise, the default version is selected.
    */
  protected def resolveVersion(
    versionOverride: Option[SemVer],
    project: Option[Project]
  ): SemVer = versionOverride.getOrElse {
    project match {
      case Some(project) =>
        // TODO [RW] properly get the default edition, see #1864
        val version = project.edition
          .map(edition => editionManager.resolveEngineVersion(edition).get)
          .map(SemVerEnsoVersion)
          .getOrElse(DefaultEnsoVersion)
        version match {
          case DefaultEnsoVersion =>
            globalConfigurationManager.defaultVersion
          case SemVerEnsoVersion(version) => version
        }
      case None =>
        globalConfigurationManager.defaultVersion
    }
  }
}
