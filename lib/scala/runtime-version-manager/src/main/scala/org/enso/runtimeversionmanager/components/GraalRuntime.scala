package org.enso.runtimeversionmanager.components

import org.enso.cli.OS

import java.nio.file.{Files, Path}
import org.enso.distribution.FileSystem.PathSyntax

import scala.util.{Failure, Success, Try}

/** Represents a runtime component.
  *
  * @param version version of the component
  * @param path path to the component
  */
case class GraalRuntime(version: GraalVMVersion, path: Path) {

  /** The list of executable file extensions of the GraalVM distribution. */
  private val extensions: Seq[String] =
    OS.operatingSystem match {
      case OS.Linux   => Seq()
      case OS.MacOS   => Seq()
      case OS.Windows => Seq("exe", "cmd")
    }

  /** @inheritdoc */
  override def toString: String =
    s"GraalVM ${version.graalVersion}-java${version.java}"

  /** The path to the JAVA_HOME directory associated with this runtime.
    */
  def javaHome: Path =
    OS.operatingSystem match {
      case OS.Linux   => path
      case OS.MacOS   => path / "Contents" / "Home"
      case OS.Windows => path
    }

  /** The path to the `java` executable associated with this runtime. */
  def javaExecutable: Path = {
    val executableName = if (OS.isWindows) "java.exe" else "java"
    javaHome / "bin" / executableName
  }

  /** The path to the executables. */
  def binaryPath: Path = javaHome / "bin"

  /** Find the executable by name.
    *
    * @param name the executable name.
    * @return the path to the executable.
    */
  def findExecutable(name: String): Path = {
    val possibleExecutableNames =
      if (extensions.isEmpty) Seq(name)
      else extensions.map(ext => s"$name.$ext")
    possibleExecutableNames
      .map(binaryPath / _)
      .find(Files.isExecutable)
      .getOrElse(throw ExecutableNotFoundError(binaryPath, name))
  }

  /** Checks if the installation is not corrupted and reports any issues as
    * failures.
    */
  def ensureValid(): Try[Unit] =
    if (!Files.exists(javaExecutable))
      Failure(
        CorruptedComponentError(
          s"Runtime's java executable (expected at " +
          s"`${javaExecutable.toAbsolutePath.normalize}`) is missing."
        )
      )
    else if (!Files.isExecutable(javaExecutable))
      Failure(
        CorruptedComponentError(
          "Runtime's java executable is not marked as executable."
        )
      )
    else Success(())
}
