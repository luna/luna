package org.enso.cli

import com.typesafe.scalalogging.Logger
import io.circe.{Decoder, DecodingFailure}

/** Represents one of the supported platforms (operating systems).
  */
sealed trait OS {

  /** Name of this operating system as included in the configuration.
    */
  def configName: String

  /** Checks if the provided `os.name` matches this operating system.
    */
  def matches(osName: String): Boolean = osName.toLowerCase.contains(configName)
}

/** Gathers helper functions useful for dealing with platform-specific
  * behaviour.
  */
object OS {

  private val logger = Logger[OS.type]

  /** Represents the Linux operating system.
    */
  case object Linux extends OS {

    /** @inheritdoc
      */
    def configName: String = "linux"
  }

  /** Represents the macOS operating system.
    */
  case object MacOS extends OS {

    /** @inheritdoc
      */
    def configName: String = "macos"

    /** @inheritdoc
      */
    override def matches(osName: String): Boolean =
      osName.toLowerCase.contains("mac")
  }

  /** Represents the Windows operating system.
    */
  case object Windows extends OS {

    /** @inheritdoc
      */
    def configName: String = "windows"
  }

  /** Checks if the application is being run on Windows.
    */
  def isWindows: Boolean =
    operatingSystem == OS.Windows

  def isUNIX: Boolean =
    operatingSystem == OS.Linux || operatingSystem == OS.MacOS

  /** Returns which [[OS]] this program is running on.
    */
  lazy val operatingSystem: OS = detectOS

  private val ENSO_OPERATING_SYSTEM = "ENSO_OPERATING_SYSTEM"

  private val knownOS = Seq(Linux, MacOS, Windows)
  private lazy val knownOSPossibleValuesString =
    knownOS.map(os => s"`${os.configName}`").mkString(", ")

  private def detectOS: OS = {
    val overridenName = Option(System.getenv(ENSO_OPERATING_SYSTEM))
    overridenName match {
      case Some(value) =>
        knownOS.find(value.toLowerCase == _.configName) match {
          case Some(overriden) =>
            logger.debug(
              "OS overriden by [{}] to [{}].",
              ENSO_OPERATING_SYSTEM,
              overriden
            )
            return overriden
          case None =>
            logger.warn(
              "{} is set to an unknown value [{}], " +
              "ignoring. Possible values are [{}].",
              ENSO_OPERATING_SYSTEM,
              value,
              knownOSPossibleValuesString
            )
        }
      case None =>
    }

    val name       = System.getProperty("os.name")
    val possibleOS = knownOS.filter(_.matches(name))
    if (possibleOS.length == 1) {
      possibleOS.head
    } else {
      logger.error(
        "Could not determine a supported operating system. Please make sure " +
        "the OS you are running is supported. You can try to manually " +
        "override the operating system detection by setting an environment " +
        "variable [{}] to one of the possible values " +
        "[{}] depending on the system that your OS most behaves like.",
        ENSO_OPERATING_SYSTEM,
        knownOSPossibleValuesString
      )
      throw new IllegalStateException(
        "fatal: Could not detect the operating system."
      )
    }
  }

  /** Name of the architecture that the program is running on.
    *
    * Currently the Launcher Native Image builds only support amd64
    * architecture, so it is hardcoded here. In the future, more architectures
    * may be supported. In that case, this will need to be updated to get the
    * target architecture from the build settings.
    *
    * This property should not use `System.getProperty("os.arch")` directly
    * because it can return different values for the same architecture (for
    * example on some systems `amd64` is called `x86_64`).
    */
  val architecture: String = "amd64"

  /** Wraps the base executable name with an optional platform-dependent
    * extension.
    */
  def executableName(baseName: String): String =
    if (isWindows) baseName + ".exe" else baseName

  /** A [[Decoder]] instance allowing to parse the OS name from JSON and YAML
    * configuration.
    */
  implicit val decoder: Decoder[OS] = { json =>
    json.as[String].flatMap { string =>
      knownOS.find(_.configName == string).toRight {
        DecodingFailure(
          s"`$string` is not a valid OS name. " +
          s"Possible values are $knownOSPossibleValuesString.",
          json.history
        )
      }
    }
  }
}
