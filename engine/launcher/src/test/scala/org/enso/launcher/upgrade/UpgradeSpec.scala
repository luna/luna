package org.enso.launcher.upgrade

import java.nio.file.{Files, Path, StandardCopyOption}
import io.circe.parser
import nl.gn0s1s.bump.SemVer
import org.enso.distribution.FileSystem
import org.enso.distribution.locking.{FileLockManager, LockType}
import FileSystem.PathSyntax
import org.enso.cli.OS
import org.enso.launcher._
import org.enso.testkit.{RetrySpec, WithTemporaryDirectory}
import org.enso.testkit.process.{RunResult, WrappedProcess}
import org.scalatest.exceptions.TestFailedException
import org.scalatest.{BeforeAndAfterAll, OptionValues}

import scala.concurrent.TimeoutException

class UpgradeSpec
    extends NativeTest
    with WithTemporaryDirectory
    with BeforeAndAfterAll
    with OptionValues
    with RetrySpec {

  /** Location of the fake releases root.
    */
  private val fakeReleaseRoot = FakeLauncherReleases.path

  /** Location of built Rust artifacts.
    */
  private val rustBuildRoot = Path.of("./target/rust/debug/")

  /** Location of the actual launcher executable that is wrapped by the shims.
    */
  private val realLauncherLocation =
    Path.of(".").resolve(OS.executableName("enso")).toAbsolutePath.normalize

  /** Path to a launcher shim that pretends to be `version`.
    */
  private def builtLauncherBinary(version: SemVer): Path = {
    val simplifiedVersion = version.toString.replaceAll("[.-]", "")
    rustBuildRoot / OS.executableName(s"launcher_$simplifiedVersion")
  }

  /** Copies a launcher shim into the fake release directory.
    */
  private def prepareLauncherBinary(version: SemVer): Unit = {
    val os          = OS.operatingSystem.configName
    val arch        = OS.architecture
    val ext         = if (OS.isWindows) "zip" else "tar.gz"
    val packageName = s"enso-launcher-$version-$os-$arch.$ext"
    val destinationDirectory =
      fakeReleaseRoot / s"enso-$version" / packageName / "enso" / "bin"
    Files.createDirectories(destinationDirectory)
    Files.copy(
      builtLauncherBinary(version),
      destinationDirectory / OS.executableName("enso"),
      StandardCopyOption.REPLACE_EXISTING
    )
  }

  override def beforeAll(): Unit = {
    super.beforeAll()
    prepareLauncherBinary(SemVer(0, 0, 0))
    prepareLauncherBinary(SemVer(0, 0, 1))
    prepareLauncherBinary(SemVer(0, 0, 2))
    prepareLauncherBinary(SemVer(0, 0, 3))
    prepareLauncherBinary(SemVer(0, 0, 4))
  }

  /** Prepares a launcher distribution in the temporary test location.
    *
    * If `launcherVersion` is not provided, the default one is used.
    *
    * It waits a 250ms delay after creating the launcher copy to ensure that the
    * copy can be called right away after calling this function. It is not
    * absolutely certain that this is helpful, but from time to time, the tests
    * fail because the filesystem does not allow to access the executable as
    * 'not-ready'. This delay is an attempt to make the tests more stable.
    */
  private def prepareDistribution(
    portable: Boolean,
    launcherVersion: Option[SemVer] = None
  ): Unit = {
    val sourceLauncherLocation =
      launcherVersion.map(builtLauncherBinary).getOrElse(baseLauncherLocation)
    Files.createDirectories(launcherPath.getParent)
    Files.copy(
      sourceLauncherLocation,
      launcherPath,
      StandardCopyOption.REPLACE_EXISTING
    )
    if (portable) {
      val root = launcherPath.getParent.getParent
      FileSystem.writeTextFile(root / ".enso.portable", "mark")
    }
    Thread.sleep(1000)
  }

  /** Path to the launcher executable in the temporary distribution.
    */
  private def launcherPath =
    getTestDirectory / "enso" / "bin" / OS.executableName("enso")

  /** Runs `enso version` to inspect the version reported by the launcher.
    * @return the reported version
    */
  private def checkVersion(): SemVer = {
    val result = run(
      Seq("version", "--json", "--only-launcher")
    )
    result should returnSuccess
    val version = parser.parse(result.stdout).getOrElse {
      throw new TestFailedException(
        s"Version should be a valid JSON string, got '${result.stdout}' " +
        s"instead.",
        1
      )
    }
    SemVer(version.asObject.value.apply("version").value.asString.value).value
  }

  /** Runs the launcher in the temporary distribution.
    *
    * @param args arguments for the launcher
    * @param extraEnv environment variable overrides
    * @return result of the run
    */
  def run(
    args: Seq[String],
    extraEnv: Map[String, String] = Map.empty
  ): RunResult = startLauncher(args, extraEnv).join(timeoutSeconds = 20)

  /** Starts the launcher in the temporary distribution.
    *
    * @param args arguments for the launcher
    * @param extraEnv environment variable overrides
    * @return wrapped process
    */
  def startLauncher(
    args: Seq[String],
    extraEnv: Map[String, String] = Map.empty
  ): WrappedProcess = {
    val testArgs = Seq(
      "--internal-emulate-repository",
      fakeReleaseRoot.toAbsolutePath.toString,
      "--auto-confirm",
      "--hide-progress"
    )
    val env =
      extraEnv.updated("ENSO_LAUNCHER_LOCATION", realLauncherLocation.toString)
    start(
      Seq(launcherPath.toAbsolutePath.toString) ++ testArgs ++ args,
      env.toSeq
    )
  }

  "upgrade" should {
    "upgrade to latest version (excluding broken)" taggedAs Retry in {
      prepareDistribution(
        portable        = true,
        launcherVersion = Some(SemVer(0, 0, 2))
      )
      run(Seq("upgrade")) should returnSuccess

      checkVersion() shouldEqual SemVer(0, 0, 4)
    }

    "not downgrade without being explicitly asked to do so" taggedAs Retry in {
      // precondition for the test to make sense
      SemVer(buildinfo.Info.ensoVersion).value should be > SemVer(0, 0, 4)

      prepareDistribution(
        portable = true
      )
      run(Seq("upgrade")).exitCode shouldEqual 1
    }

    "upgrade/downgrade to a specific version " +
    "(and update necessary files)" taggedAs Retry in {
      // precondition for the test to make sense
      SemVer(buildinfo.Info.ensoVersion).value should be > SemVer(0, 0, 4)

      prepareDistribution(
        portable = true
      )
      val root = launcherPath.getParent.getParent
      FileSystem.writeTextFile(root / "README.md", "Old readme")
      run(Seq("upgrade", "0.0.1")) should returnSuccess
      checkVersion() shouldEqual SemVer(0, 0, 1)
      TestHelpers.readFileContent(root / "README.md").trim shouldEqual "Content"
      TestHelpers
        .readFileContent(root / "THIRD-PARTY" / "test-license.txt")
        .trim shouldEqual "Test license"
    }

    "upgrade also in installed mode" taggedAs Retry in {
      prepareDistribution(
        portable        = false,
        launcherVersion = Some(SemVer(0, 0, 0))
      )
      val dataRoot   = getTestDirectory / "data"
      val configRoot = getTestDirectory / "config"
      checkVersion() shouldEqual SemVer(0, 0, 0)
      val env = Map(
        "ENSO_DATA_DIRECTORY"    -> dataRoot.toString,
        "ENSO_CONFIG_DIRECTORY"  -> configRoot.toString,
        "ENSO_RUNTIME_DIRECTORY" -> (getTestDirectory / "run").toString
      )

      run(Seq("upgrade", "0.0.1"), extraEnv = env) should returnSuccess
      checkVersion() shouldEqual SemVer(0, 0, 1)
      TestHelpers
        .readFileContent(dataRoot / "README.md")
        .trim shouldEqual "Content"
      TestHelpers
        .readFileContent(dataRoot / "THIRD-PARTY" / "test-license.txt")
        .trim shouldEqual "Test license"
    }

    "perform a multi-step upgrade if necessary" taggedAs Retry in {
      // 0.0.3 can only be upgraded from 0.0.2 which can only be upgraded from
      // 0.0.1, so the upgrade path should be following:
      // 0.0.0 -> 0.0.1 -> 0.0.2 -> 0.0.3
      prepareDistribution(
        portable        = true,
        launcherVersion = Some(SemVer(0, 0, 0))
      )

      checkVersion() shouldEqual SemVer(0, 0, 0)
      val process = startLauncher(Seq("upgrade", "0.0.3"))
      try {
        process.join(timeoutSeconds = 30) should returnSuccess

        checkVersion() shouldEqual SemVer(0, 0, 3)

        val launchedVersions = Seq(
          "0.0.0",
          "0.0.0",
          "0.0.1",
          "0.0.2",
          "0.0.3"
        )

        val reportedLaunchLog = TestHelpers
          .readFileContent(launcherPath.getParent / ".launcher_version_log")
          .trim
          .linesIterator
          .toSeq

        reportedLaunchLog shouldEqual launchedVersions

        withClue(
          "After the update we run the version check, running the launcher " +
          "after the update should ensure no leftover temporary executables " +
          "are left in the bin directory."
        ) {
          val binDirectory = launcherPath.getParent
          val leftOverExecutables = FileSystem
            .listDirectory(binDirectory)
            .map(_.getFileName.toString)
            .filter(_.startsWith("enso"))
          leftOverExecutables shouldEqual Seq(OS.executableName("enso"))
        }
      } finally {
        if (process.isAlive) {
          // ensure that the child process frees resources if retrying the test
          process.kill()
          Thread.sleep(500)
        }
      }
    }

    "automatically trigger if an action requires a newer version and re-run " +
    "that action with the upgraded launcher" ignore {
      prepareDistribution(
        portable        = true,
        launcherVersion = Some(SemVer(0, 0, 2))
      )
      val enginesPath = getTestDirectory / "enso" / "dist"
      Files.createDirectories(enginesPath)

      // TODO [RW] re-enable this test when #1046 or #1273 is done and the
      //  engine distribution can be used in the test
//      FileSystem.copyDirectory(
//        Path.of("target/distribution/"),
//        enginesPath / "0.1.0"
//      )
      val script  = getTestDirectory / "script.enso"
      val message = "Hello from test"
      val content =
        s"""from Standard.Builtins import all
           |main = IO.println "$message"
           |""".stripMargin
      FileSystem.writeTextFile(script, content)

      // TODO [RW] make sure the right `java` is used to run the engine
      //  (this should be dealt with in #1046)
      val result = run(
        Seq(
          "run",
          script.toAbsolutePath.toString,
          "--use-system-jvm",
          "--use-enso-version",
          "0.1.0"
        )
      )
      result should returnSuccess
      result.stdout should include(message)
    }

    "fail if another upgrade is running in parallel" taggedAs Retry in {
      prepareDistribution(
        portable        = true,
        launcherVersion = Some(SemVer(0, 0, 1))
      )

      val syncLocker = new FileLockManager(getTestDirectory / "enso" / "lock")

      val launcherManifestAssetName = "launcher-manifest.yaml"
      // The fake release tries to acquire a shared lock on each accessed file,
      // so acquiring this exclusive lock will stall access to that file until
      // the exclusive lock is released
      val lock = syncLocker.acquireLock(
        "testasset-" + launcherManifestAssetName,
        LockType.Exclusive
      )

      val firstSuspended = startLauncher(
        Seq(
          "upgrade",
          "0.0.2",
          "--internal-emulate-repository-wait",
          "--launcher-log-level=trace"
        )
      )
      try {
        firstSuspended.waitForMessageOnErrorStream(
          "INTERNAL-TEST-ACQUIRING-LOCK",
          timeoutSeconds = 30
        )

        val secondFailed = run(Seq("upgrade", "0.0.0"))

        secondFailed.stderr should include("Another upgrade is in progress")
        secondFailed.exitCode shouldEqual 1
      } catch {
        case e: TimeoutException =>
          System.err.println(
            "Waiting for the lock timed out, " +
            "the process had the following output:"
          )
          firstSuspended.printIO()
          firstSuspended.kill()
          throw e
      } finally {
        lock.release()
      }

      firstSuspended.join(timeoutSeconds = 20) should returnSuccess
      checkVersion() shouldEqual SemVer(0, 0, 2)
    }
  }
}
