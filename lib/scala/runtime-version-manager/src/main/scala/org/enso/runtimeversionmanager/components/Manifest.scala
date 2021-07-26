package org.enso.runtimeversionmanager.components

import java.io.FileReader
import java.nio.file.Path
import cats.Show
import io.circe.{yaml, Decoder}
import nl.gn0s1s.bump.SemVer
import org.enso.cli.OS
import org.enso.editions.SemVerJson._
import org.enso.runtimeversionmanager.components.Manifest.{
  JVMOption,
  RequiredInstallerVersions
}
import org.enso.runtimeversionmanager.components

import scala.util.{Failure, Try, Using}

/** Contains release metadata read from the manifest file that is attached to
  * each release.
  *
  * @param requiredInstallerVersions The minimum required version of the
  *                                  installer that ensures its runtime version
  *                                  management logic is compatible with this
  *                                  particular engine release. Earlier
  *                                  installer versions should not be able to
  *                                  download this release (as the logic for
  *                                  installing it has probably changed).
  *                                  Instead they should print a message that
  *                                  the installer should be upgraded to use
  *                                  that version.
  * @param graalVMVersion the version of the GraalVM runtime that has to be
  *                       used with this engine
  * @param graalJavaVersion the java version of that GraalVM runtime
  * @param jvmOptions a list of JVM options that should be added when running
  *                   this engine
  */
case class Manifest(
  requiredInstallerVersions: RequiredInstallerVersions,
  graalVMVersion: String,
  graalJavaVersion: String,
  jvmOptions: Seq[JVMOption],
  brokenMark: Boolean
) {

  /** Returns a [[GraalVMVersion]] which encapsulates all version information
    * needed to find the runtime required for this release.
    */
  def runtimeVersion: GraalVMVersion =
    components.GraalVMVersion(graalVMVersion, graalJavaVersion)

  /** Returns the minimum required version of the installer that is required for
    * using this particular engine release.
    */
  def minimumRequiredVersion(implicit installerKind: InstallerKind): SemVer =
    installerKind match {
      case InstallerKind.Launcher =>
        requiredInstallerVersions.launcher
      case InstallerKind.ProjectManager =>
        requiredInstallerVersions.projectManager
    }
}

object Manifest {

  /** Stores minimum required versions of each installer kind described in the
    * [[Manifest]].
    */
  case class RequiredInstallerVersions(launcher: SemVer, projectManager: SemVer)

  /** Defines the name under which the manifest is included in the releases.
    */
  val DEFAULT_MANIFEST_NAME = "manifest.yaml"

  /** Context used to substitute context-dependent variables in an JVM option.
    *
    * The context depends on what engine is being run on the JVM.
    *
    * @param enginePackagePath absolute path to the engine that is being
    *                          launched
    */
  case class JVMOptionsContext(enginePackagePath: Path)

  /** Represents an option that is added to the JVM running an engine.
    *
    * @param value option value, possibly containing variables that will be
    *              substituted
    * @param osRestriction the option is added only on the specified operating
    *                      system, if it is None, it applies to all systems
    */
  case class JVMOption(value: String, osRestriction: Option[OS]) {

    /** Checks if the option applies on the operating system that is currently
      * running.
      */
    def isRelevant: Boolean =
      osRestriction.isEmpty || osRestriction.contains(OS.operatingSystem)

    /** Substitutes any variables based on the provided `context`.
      */
    def substitute(context: JVMOptionsContext): String =
      value.replace(
        "$enginePackagePath",
        context.enginePackagePath.toAbsolutePath.normalize.toString
      )
  }

  object JVMOption {
    private object Fields {
      val os    = "os"
      val value = "value"
    }

    /** [[Decoder]] instance that allows to parse the [[JVMOption]] from the
      * YAML manifest.
      */
    implicit val decoder: Decoder[JVMOption] = { json =>
      val hasOSKey = json.keys.exists { keyList: Iterable[String] =>
        keyList.toSeq.contains(Fields.os)
      }

      for {
        value <- json.get[String](Fields.value)
        osRestriction <-
          if (hasOSKey) json.get[OS](Fields.os).map(Some(_)) else Right(None)
      } yield JVMOption(value, osRestriction)
    }
  }

  /** Tries to load the manifest at the given path.
    *
    * Returns None if the manifest could not be opened or could not be parsed.
    */
  def load(path: Path): Try[Manifest] =
    Using(new FileReader(path.toFile)) { reader =>
      yaml.parser
        .parse(reader)
        .flatMap(_.as[Manifest])
        .toTry
    }.flatten.recoverWith { error =>
      Failure(ManifestLoadingError.fromThrowable(error))
    }

  /** Parses the manifest from a string containing a YAML definition.
    *
    * Returns None if the definition cannot be parsed.
    */
  def fromYaml(yamlString: String): Try[Manifest] = {
    yaml.parser
      .parse(yamlString)
      .flatMap(_.as[Manifest])
      .toTry
      .recoverWith { error =>
        Failure(ManifestLoadingError.fromThrowable(error))
      }
  }

  /** Indicates an error that prevented loading the engine manifest.
    */
  case class ManifestLoadingError(message: String, cause: Throwable)
      extends RuntimeException(message, cause) {

    /** @inheritdoc
      */
    override def toString: String = message
  }

  object ManifestLoadingError {

    /** Creates a [[ManifestLoadingError]] by wrapping another [[Throwable]].
      *
      * Special logic is used for [[io.circe.Error]] to display the error
      * summary in a human-readable way.
      */
    def fromThrowable(throwable: Throwable): ManifestLoadingError =
      throwable match {
        case decodingError: io.circe.Error =>
          val errorMessage =
            implicitly[Show[io.circe.Error]].show(decodingError)
          ManifestLoadingError(
            s"Could not parse the manifest: $errorMessage",
            decodingError
          )
        case other =>
          ManifestLoadingError(s"Could not load the manifest: $other", other)
      }
  }

  object Fields {
    val minimumLauncherVersion       = "minimum-launcher-version"
    val minimumProjectManagerVersion = "minimum-project-manager-version"
    val jvmOptions                   = "jvm-options"
    val graalVMVersion               = "graal-vm-version"
    val graalJavaVersion             = "graal-java-version"
    val brokenMark                   = "broken"
  }

  implicit private val decoder: Decoder[Manifest] = { json =>
    for {
      minimumLauncherVersion <- json.get[SemVer](Fields.minimumLauncherVersion)
      minimumProjectManagerVersion <- json.get[SemVer](
        Fields.minimumProjectManagerVersion
      )
      graalVMVersion <- json.get[String](Fields.graalVMVersion)
      graalJavaVersion <-
        json
          .get[String](Fields.graalJavaVersion)
          .orElse(json.get[Int](Fields.graalJavaVersion).map(_.toString))
      jvmOptions <- json.getOrElse[Seq[JVMOption]](Fields.jvmOptions)(Seq())
      broken     <- json.getOrElse[Boolean](Fields.brokenMark)(false)
    } yield Manifest(
      requiredInstallerVersions = RequiredInstallerVersions(
        launcher       = minimumLauncherVersion,
        projectManager = minimumProjectManagerVersion
      ),
      graalVMVersion   = graalVMVersion,
      graalJavaVersion = graalJavaVersion,
      jvmOptions       = jvmOptions,
      brokenMark       = broken
    )
  }
}
