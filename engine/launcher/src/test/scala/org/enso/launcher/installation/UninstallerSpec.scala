package org.enso.launcher.installation

import org.enso.distribution.FileSystem

import java.nio.file.{Files, Path}
import FileSystem.PathSyntax
import org.enso.cli.OS
import org.enso.launcher.NativeTest
import org.enso.testkit.WithTemporaryDirectory

class UninstallerSpec extends NativeTest with WithTemporaryDirectory {
  def installedRoot: Path = getTestDirectory / "installed"

  /** Prepares an installed distribution for the purposes of testing
    * uninstallation.
    *
    * @param everythingInsideData if true, config and binary directory are put
    *                             inside the data root
    * @return returns the path to the created launcher and a mapping of
    *         environment overrides that need to be used for it to use the
    *         correct installation directory
    */
  def prepareInstalledDistribution(
    everythingInsideData: Boolean = false
  ): (Path, Map[String, String]) = {
    val binDirectory =
      if (everythingInsideData) installedRoot / "bin" else getTestDirectory
    val configDirectory =
      if (everythingInsideData) installedRoot / "config"
      else getTestDirectory / "enso-config"
    val dataDirectory    = installedRoot
    val runDirectory     = installedRoot
    val logDirectory     = installedRoot / "log"
    val portableLauncher = binDirectory / OS.executableName("enso")
    copyLauncherTo(portableLauncher)
    Files.createDirectories(dataDirectory / "dist")
    Files.createDirectories(configDirectory)
    FileSystem.writeTextFile(
      configDirectory / "global-config.yaml",
      "what: ever"
    )
    FileSystem.writeTextFile(dataDirectory / "README.md", "content")
    Files.createDirectories(dataDirectory / "tmp")

    val env = Map(
      "ENSO_DATA_DIRECTORY"    -> dataDirectory.toString,
      "ENSO_BIN_DIRECTORY"     -> binDirectory.toString,
      "ENSO_CONFIG_DIRECTORY"  -> configDirectory.toString,
      "ENSO_RUNTIME_DIRECTORY" -> runDirectory.toString,
      "ENSO_LOG_DIRECTORY"     -> logDirectory.toString
    )
    (portableLauncher, env)
  }

  "enso uninstall distribution" should {
    "uninstall a simple distribution" in {
      val (launcher, env) = prepareInstalledDistribution()

      runLauncherAt(
        launcher,
        Seq("--auto-confirm", "uninstall", "distribution"),
        env
      ) should returnSuccess

      assert(Files.notExists(installedRoot), "Should remove the data root.")
      assert(
        Files.notExists(getTestDirectory / "enso-config"),
        "Should remove the configuration directory."
      )
      assert(Files.notExists(launcher), "Should remove the executable.")
    }

    "uninstall a distribution with config and bin inside of data" in {
      val (launcher, env) =
        prepareInstalledDistribution(everythingInsideData = true)

      runLauncherAt(
        launcher,
        Seq("--auto-confirm", "uninstall", "distribution"),
        env
      ) should returnSuccess

      assert(Files.notExists(installedRoot), "Should remove the data root.")
    }

    "not remove unknown files by default when uninstalling" in {
      val (launcher, env) = prepareInstalledDistribution()
      val configFile      = getTestDirectory / "enso-config" / "unknown-file"
      FileSystem.writeTextFile(configFile, "mark")
      val dataFile = installedRoot / "unknown-file2"
      FileSystem.writeTextFile(dataFile, "mark")

      runLauncherAt(
        launcher,
        Seq("--auto-confirm", "uninstall", "distribution"),
        env
      ) should returnSuccess

      assert(
        Files.exists(installedRoot),
        "Should not remove the data root with extra files."
      )
      assert(
        Files.exists(getTestDirectory / "enso-config"),
        "Should not remove the configuration root with extra files."
      )
      assert(Files.exists(dataFile), "Should not remove unknown files.")
      assert(Files.exists(configFile), "Should not remove unknown files.")
      assert(
        Files.notExists(
          getTestDirectory / "enso-config" / "global-config.yaml"
        ),
        "But the known ones should be removed."
      )
      assert(
        Files.notExists(installedRoot / "dist"),
        "But the known ones should be removed."
      )
      assert(Files.notExists(launcher), "Should remove the executable.")
    }
  }
}
