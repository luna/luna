package org.enso.distribution

import java.nio.file.Path

/** A helper class that provides paths for bundled components inside of an
  * engine distribution from which the runtime is being run.
  */
case class LanguageHome(languageHome: Path) {
  private val rootPath = languageHome.getParent.toAbsolutePath.normalize

  /** The path to editions bundled with the engine. */
  def editions: Path =
    rootPath.resolve(DistributionManager.EDITIONS_DIRECTORY)

  /** The path to libraries bundled with the engine.
    *
    * The libraries there comply to the directory structure of an auxiliary
    * library cache.
    */
  def libraries: Path =
    rootPath.resolve(DistributionManager.LIBRARIES_DIRECTORY)
}

object LanguageHome {

  /** Finds the [[LanguageHome]] based on the path of the runner JAR.
    *
    * Only guaranteed to work properly if used in a component that is started by the `engine-runner`.
    */
  def detectFromExecutableLocation(environment: Environment): LanguageHome = {
    val homePath = environment.getPathToRunningExecutable.getParent
    LanguageHome(homePath)
  }
}
