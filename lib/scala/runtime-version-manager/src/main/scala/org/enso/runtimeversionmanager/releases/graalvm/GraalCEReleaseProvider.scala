package org.enso.runtimeversionmanager.releases.graalvm

import org.enso.cli.OS

import java.nio.file.Path
import org.enso.cli.task.TaskProgress
import org.enso.runtimeversionmanager.components.GraalVMVersion
import org.enso.runtimeversionmanager.releases.github.GithubReleaseProvider
import org.enso.runtimeversionmanager.releases.{
  ReleaseProviderException,
  SimpleReleaseProvider
}

import scala.util.{Failure, Success}

/** [[GraalVMRuntimeReleaseProvider]] implementation providing Graal Community
  * Edition releases from the given [[SimpleReleaseProvider]].
  */
class GraalCEReleaseProvider(releaseProvider: SimpleReleaseProvider)
    extends GraalVMRuntimeReleaseProvider {

  /** @inheritdoc */
  override def packageFileName(version: GraalVMVersion): String =
    GraalCEReleaseProvider.packageFileNameForCurrentOS(version)

  /** @inheritdoc */
  override def downloadPackage(
    version: GraalVMVersion,
    destination: Path
  ): TaskProgress[Unit] = {
    val tagName     = s"vm-${version.graalVersion}"
    val packageName = packageFileName(version)
    val release     = releaseProvider.releaseForTag(tagName)
    release match {
      case Failure(exception) =>
        TaskProgress.immediateFailure(exception)
      case Success(release) =>
        release.assets
          .find(_.fileName == packageName)
          .map(_.downloadTo(destination))
          .getOrElse {
            TaskProgress.immediateFailure(
              ReleaseProviderException(
                s"Cannot find package `$packageName` in the release."
              )
            )
          }
    }
  }
}

object GraalCEReleaseProvider {
  private val githubRepository =
    new GithubReleaseProvider("graalvm", "graalvm-ce-builds")

  /** Default [[GraalVMRuntimeReleaseProvider]] that provides Graal CE releases
    * using the GitHub Release API.
    */
  val default = new GraalCEReleaseProvider(githubRepository)

  /** Generates the name of the package for the currently running OS and a
    * specified release version.
    */
  def packageFileNameForCurrentOS(version: GraalVMVersion): String = {
    val os = OS.operatingSystem match {
      case OS.Linux   => "linux"
      case OS.MacOS   => "darwin"
      case OS.Windows => "windows"
    }
    val arch = OS.architecture
    val extension = OS.operatingSystem match {
      case OS.Linux   => ".tar.gz"
      case OS.MacOS   => ".tar.gz"
      case OS.Windows => ".zip"
    }
    s"graalvm-ce-" +
    s"java${version.java}-$os-$arch-${version.graalVersion}$extension"
  }
}
