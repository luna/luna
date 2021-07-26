package org.enso.launcher

import org.enso.cli.OS
import org.enso.runtimeversionmanager.test.NativeTestHelper
import org.enso.testkit.process.RunResult
import org.scalatest.concurrent.{Signaler, TimeLimitedTests}
import org.scalatest.matchers.should.Matchers
import org.scalatest.matchers.{MatchResult, Matcher}
import org.scalatest.time.Span
import org.scalatest.time.SpanSugar._
import org.scalatest.wordspec.AnyWordSpec

import java.nio.file.{Files, Path}

/** Contains helper methods for creating tests that need to run the native
  * launcher binary.
  */
trait NativeTest
    extends AnyWordSpec
    with Matchers
    with TimeLimitedTests
    with NativeTestHelper {

  override val timeLimit: Span               = 60.seconds
  override val defaultTestSignaler: Signaler = _.interrupt()

  object RunSuccessMatcher extends Matcher[RunResult] {
    override def apply(left: RunResult): MatchResult =
      MatchResult(
        left.exitCode == 0,
        s"Run did not exit with success but exit with code ${left.exitCode}.\n" +
        s"Its stderr was: ```${left.stderr}```.\n" +
        s"And stdout was: ```${left.stdout}```.",
        s"Run did not fail as expected. It printed ```${left.stdout}```."
      )
  }

  /** A scalatest Matcher that allows to write expressions like
    * `run should returnSuccess`.
    */
  def returnSuccess: RunSuccessMatcher.type = RunSuccessMatcher

  /** Specifies if the current OS is Windows.
    */
  val isWindows: Boolean =
    System.getProperty("os.name").toLowerCase().contains("windows")

  /** Runs the native launcher binary with the specified arguments and captures
    * its output.
    *
    * @param args arguments to forward to the launcher
    * @param extraEnv environment variables to override for the launched
    *                 program, do not use these to override PATH
    */
  def runLauncher(
    args: Seq[String],
    extraEnv: Map[String, String] = Map.empty
  ): RunResult = {
    if (extraEnv.contains("PATH")) {
      throw new IllegalArgumentException(
        "To override path, use [[runLauncherWithPath]]."
      )
    }

    runCommand(
      Seq(baseLauncherLocation.toAbsolutePath.toString) ++ args,
      extraEnv.toSeq
    )
  }

  /** Runs the native launcher binary located at `pathToLauncher` with the
    * specified arguments and captures its output.
    *
    * @param pathToLauncher path to the launcher executable to run
    * @param args arguments to forward to the launcher
    * @param extraEnv environment variables to override for the launched
    *                 program, do not use these to override PATH
    */
  def runLauncherAt(
    pathToLauncher: Path,
    args: Seq[String],
    extraEnv: Map[String, String] = Map.empty
  ): RunResult = {
    if (extraEnv.contains("PATH")) {
      throw new IllegalArgumentException(
        "To override path, use [[runLauncherWithPath]]."
      )
    }

    runCommand(
      Seq(pathToLauncher.toAbsolutePath.toString) ++ args,
      extraEnv.toSeq
    )
  }

  /** Returns the expected location of the launcher binary compiled by the
    * Native Image. This binary can be copied into various places to test its
    * functionality.
    */
  def baseLauncherLocation: Path =
    Path.of(".").resolve(OS.executableName("enso"))

  /** Creates a copy of the tested launcher binary at the specified location.
    *
    * It waits a 100ms delay after creating the copy to ensure that the copy can
    * be called right away after calling this function. It is not absolutely
    * certain that this is helpful, but from time to time, the tests fail
    * because the filesystem does not allow to access the executable as
    * 'not-ready'. This delay is an attempt to make the tests more stable.
    */
  def copyLauncherTo(path: Path): Unit = {
    val parent = path.getParent
    Files.createDirectories(parent)
    Files.copy(baseLauncherLocation, path)
    if (!Files.isExecutable(path)) {
      throw new RuntimeException("Failed to make it executable...")
    }
    Thread.sleep(100)
  }

  /** Runs the native launcher binary with the specified arguments and captures
    * its output.
    *
    * @param args arguments to forward to the launcher
    * @param pathOverride the system PATH that should be set for the launched
    *                     program
    */
  def runLauncherWithPath(
    args: Seq[String],
    pathOverride: String
  ): RunResult = {
    val pathName = if (isWindows) "Path" else "PATH" // Note [Windows Path]
    runCommand(
      Seq(baseLauncherLocation.toAbsolutePath.toString) ++ args,
      Seq(pathName -> pathOverride)
    )
  }
}

/* Note [Windows Path]
 * ~~~~~~~~~~~~~~~~~~~
 * On Windows, the environment variables are usually treated as case-insensitive
 * but not all the time. When launching a process with an added environment
 * variable called `PATH`, that process actually has two variables in its
 * environment - the original `Path` and the overriden `PATH`. This can be seen
 * when querying `System.getenv()` - the returned map contains both `Path` and
 * `PATH` with their respective values. However, `System.getenv("PATH")` uses
 * some platform specific logic, and even if both variables are present in the
 * environment, it actually returns the value corresponding to `Path`. This is
 * likely the expected behaviour on Windows. So to successfully override the
 * system path on Windows, we need to override `Path`, not `PATH` like on
 * Unix-based systems.
 */
