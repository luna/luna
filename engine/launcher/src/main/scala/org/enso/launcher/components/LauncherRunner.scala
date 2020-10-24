package org.enso.launcher.components

import java.nio.file.{Files, Path}

import akka.http.scaladsl.model.Uri
import nl.gn0s1s.bump.SemVer
import org.enso.componentmanager.Environment
import org.enso.componentmanager.components.ComponentManager
import org.enso.componentmanager.config.GlobalConfigurationManager
import org.enso.componentmanager.runner.{
  LanguageServerOptions,
  RunSettings,
  Runner,
  RunnerError,
  WhichEngine
}
import org.enso.launcher.project.ProjectManager
import org.enso.loggingservice.LogLevel

import scala.concurrent.Future
import scala.util.Try

class LauncherRunner(
  projectManager: ProjectManager,
  configurationManager: GlobalConfigurationManager,
  componentsManager: ComponentManager,
  environment: Environment,
  loggerConnection: Future[Option[Uri]]
) extends Runner(
      componentsManager,
      environment,
      loggerConnection
    ) {

  /** Creates [[RunSettings]] for launching the REPL.
    *
    * See [[org.enso.launcher.Launcher.runRepl]] for more details.
    */
  def repl(
    projectPath: Option[Path],
    versionOverride: Option[SemVer],
    logLevel: LogLevel,
    additionalArguments: Seq[String]
  ): Try[RunSettings] =
    Try {
      val inProject = projectPath match {
        case Some(value) =>
          Some(projectManager.loadProject(value).get)
        case None =>
          projectManager.findProject(currentWorkingDirectory).get
      }

      val version =
        versionOverride.getOrElse {
          inProject
            .map(_.version)
            .getOrElse(configurationManager.defaultVersion)
        }
      val arguments = inProject match {
        case Some(project) =>
          val projectPackagePath =
            project.path.toAbsolutePath.normalize.toString
          Seq("--repl", "--in-project", projectPackagePath)
        case None =>
          Seq("--repl")
      }
      RunSettings(
        version,
        arguments ++ Seq("--log-level", logLevel.toString)
        ++ additionalArguments,
        connectLoggerIfAvailable = true
      )
    }

  /** Creates [[RunSettings]] for running Enso projects or scripts.
    *
    * See [[org.enso.launcher.Launcher.runRun]] for more details.
    */
  def run(
    path: Option[Path],
    versionOverride: Option[SemVer],
    logLevel: LogLevel,
    additionalArguments: Seq[String]
  ): Try[RunSettings] =
    Try {
      val actualPath = path
        .getOrElse {
          projectManager
            .findProject(currentWorkingDirectory)
            .get
            .getOrElse {
              throw RunnerError(
                "The current directory is not inside any project. `enso run` " +
                "should either get a path to a project or script to run, or " +
                "be run inside of a project to run that project."
              )
            }
            .path
        }
        .toAbsolutePath
        .normalize()
      if (!Files.exists(actualPath)) {
        throw RunnerError(s"$actualPath does not exist")
      }
      val projectMode = Files.isDirectory(actualPath)
      val project =
        if (projectMode) Some(projectManager.loadProject(actualPath).get)
        else projectManager.findProject(actualPath).get
      val version = versionOverride
        .orElse(project.map(_.version))
        .getOrElse(configurationManager.defaultVersion)

      val arguments =
        if (projectMode) Seq("--run", actualPath.toString)
        else
          project match {
            case Some(project) =>
              Seq(
                "--run",
                actualPath.toString,
                "--in-project",
                project.path.toAbsolutePath.normalize().toString
              )
            case None =>
              Seq("--run", actualPath.toString)
          }
      RunSettings(
        version,
        arguments ++ Seq("--log-level", logLevel.toString)
        ++ additionalArguments,
        connectLoggerIfAvailable = true
      )
    }

  /** Creates [[RunSettings]] for launching the Language Server.
    *
    * See [[org.enso.launcher.Launcher.runLanguageServer]] for more details.
    */
  def languageServer(
    options: LanguageServerOptions,
    contentRootPath: Path,
    versionOverride: Option[SemVer],
    logLevel: LogLevel,
    additionalArguments: Seq[String]
  ): Try[RunSettings] =
    for {
      project <- projectManager.loadProject(contentRootPath)
      runSettings <- startLanguageServer(
        options,
        project,
        versionOverride,
        logLevel,
        additionalArguments
      )
    } yield runSettings

  /** Creates [[RunSettings]] for querying the currently selected engine
    * version.
    *
    * If the current working directory is inside of a project, the engine
    * associated with the project is queried, otherwise the default engine is
    * queried.
    *
    * @param useJSON if set to true, the returned [[RunSettings]] will request
    *                the version in JSON format, otherwise human readable text
    *                format will be used
    * @return the [[RunSettings]] and a [[WhichEngine]] indicating if the used
    *         engine was from a project (true) or the default one (false)
    */
  def version(useJSON: Boolean): Try[(RunSettings, WhichEngine)] = {
    for {
      project <- projectManager.findProject(currentWorkingDirectory)
    } yield {
      val version =
        project.map(_.version).getOrElse(configurationManager.defaultVersion)
      val arguments =
        Seq("--version") ++ (if (useJSON) Seq("--json") else Seq())

      val whichEngine =
        project match {
          case Some(value) => WhichEngine.FromProject(value.name)
          case None        => WhichEngine.Default
        }

      (
        RunSettings(version, arguments, connectLoggerIfAvailable = false),
        whichEngine
      )
    }
  }
}
