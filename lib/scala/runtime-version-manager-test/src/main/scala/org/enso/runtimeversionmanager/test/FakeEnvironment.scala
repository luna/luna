package org.enso.runtimeversionmanager.test

import org.enso.distribution.{Environment, FileSystem}
import org.enso.testkit.HasTestDirectory

import java.nio.file.{Files, Path}

/** A test-suite mixin that adds helper functions that create a fake environment
  * which points to an Enso installation inside the temporary directory
  * generated for the test.
  */
trait FakeEnvironment { self: HasTestDirectory =>

  /** Returns a fake path to the Enso executable that is inside the temporary
    * directory for the test.
    *
    * @param portable specifies whether the distribution should be marked as
    *                 portable
    */
  def fakeExecutablePath(portable: Boolean = false): Path = {
    val fakeBin = getTestDirectory.resolve("bin")
    Files.createDirectories(fakeBin)
    if (portable) {
      FileSystem.writeTextFile(
        getTestDirectory.resolve(".enso.portable"),
        "mark"
      )
    }
    fakeBin.resolve("enso")
  }

  /** Returns an [[Environment]] instance that overrides the `ENSO_*`
    * directories to be inside the temporary directory for the test.
    *
    * Additionall environment overrides may be passed that will also be added to
    * the environment. Note, however, that the `ENSO_*` directories that are
    * defined in this function take precedence over whatever is passed to
    * `extraOverrides`.
    */
  def fakeInstalledEnvironment(
    extraOverrides: Map[String, String] = Map.empty
  ): Environment = {
    val executable = fakeExecutablePath()
    val dataDir    = getTestDirectory.resolve("test_data")
    val configDir  = getTestDirectory.resolve("test_config")
    val binDir     = getTestDirectory.resolve("test_bin")
    val runDir     = getTestDirectory.resolve("test_run")
    val homeDir    = getTestDirectory.resolve("test_home")
    val env = extraOverrides
      .updated("ENSO_DATA_DIRECTORY", dataDir.toString)
      .updated("ENSO_CONFIG_DIRECTORY", configDir.toString)
      .updated("ENSO_BIN_DIRECTORY", binDir.toString)
      .updated("ENSO_RUNTIME_DIRECTORY", runDir.toString)
      .updated("ENSO_HOME", homeDir.toString)
    val fakeEnvironment = new Environment {
      override def getPathToRunningExecutable: Path = executable

      override def getEnvVar(key: String): Option[String] =
        env.orElse(Function.unlift(super.getEnvVar)).lift(key)
    }

    fakeEnvironment
  }

}
