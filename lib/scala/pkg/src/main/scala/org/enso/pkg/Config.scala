package org.enso.pkg

import io.circe._
import io.circe.syntax._
import io.circe.yaml.Printer
import nl.gn0s1s.bump.SemVer
import org.enso.editions.EditionSerialization._
import org.enso.editions.{
  DefaultEnsoVersion,
  EditionName,
  Editions,
  EnsoVersion,
  SemVerEnsoVersion
}

import scala.util.Try

/** An extra project dependency.
  *
  * @param name    name of the package
  * @param version package version
  */
case class Dependency(name: String, version: String)

/** Contact information to a user.
  *
  * Used for defining authors and maintainers.
  * At least one of the fields must not be None.
  *
  * @param name  contact name
  * @param email contact email
  */
case class Contact(name: Option[String], email: Option[String]) {
  if (name.isEmpty && email.isEmpty)
    throw new IllegalArgumentException(
      "At least one of fields `name` or `email` must be defined."
    )

  /** @inheritdoc */
  override def toString: String = {
    val space = if (name.isDefined && email.isDefined) " " else ""
    name.getOrElse("") + space + email.map(email => s"<$email>").getOrElse("")
  }
}

object Contact {

  /** Fields for use when serializing the [[Contact]]. */
  object Fields {
    val Name  = "name"
    val Email = "email"
  }

  /** [[Encoder]] instance for the [[Contact]]. */
  implicit val encoder: Encoder[Contact] = { contact =>
    val name  = contact.name.map(Fields.Name -> _.asJson)
    val email = contact.email.map(Fields.Email -> _.asJson)
    Json.obj((name.toSeq ++ email.toSeq): _*)
  }

  /** [[Decoder]] instance for the [[Contact]].
    */
  implicit val decoder: Decoder[Contact] = { json =>
    def verifyAtLeastOneDefined(
      name: Option[String],
      email: Option[String]
    ): Either[DecodingFailure, Unit] =
      if (name.isEmpty && email.isEmpty)
        Left(
          DecodingFailure(
            "At least one of the fields `name`, `email` must be defined.",
            json.history
          )
        )
      else Right(())

    for {
      name  <- json.getOrElse[Option[String]](Fields.Name)(None)
      email <- json.getOrElse[Option[String]](Fields.Email)(None)
      _     <- verifyAtLeastOneDefined(name, email)
    } yield Contact(name, email)
  }
}

/** Represents a package configuration stored in the `package.yaml` file.
  *
  * @param name package name
  * @param namespace    package namespace. This field is a temporary workaround
  *                     and will be removed with further improvements to the
  *                     libraries system. The default value is `local`.
  * @param version package version
  * @param license package license
  * @param authors name and contact information of the package author(s)
  * @param maintainers name and contact information of current package
  *                   maintainer(s)
  * @param edition the Edition associated with the project; it implies the
  *                engine version and dependency configuration to be used, if it
  *                is missing, the default edition should be used
  * @param preferLocalLibraries specifies if library resolution should prefer
  *                             local libraries over what is defined in the
  *                             edition
  * @param originalJson a Json object holding the original values that this
  *                     Config was created from, used to preserve configuration
  *                     keys that are not known
  */
case class Config(
  name: String,
  namespace: String,
  version: String,
  license: String,
  authors: List[Contact],
  maintainers: List[Contact],
  edition: Option[Editions.RawEdition],
  preferLocalLibraries: Boolean,
  originalJson: JsonObject = JsonObject()
) {

  /** Converts the configuration into a YAML representation. */
  def toYaml: String =
    Printer.spaces2.copy(preserveOrder = true).pretty(Config.encoder(this))
}

object Config {

  val defaultNamespace: String = "local"

  private object JsonFields {
    val name: String         = "name"
    val version: String      = "version"
    val ensoVersion: String  = "enso-version"
    val license: String      = "license"
    val author: String       = "authors"
    val namespace: String    = "namespace"
    val maintainer: String   = "maintainers"
    val edition: String      = "edition"
    val preferLocalLibraries = "prefer-local-libraries"
  }

  implicit val decoder: Decoder[Config] = { json =>
    for {
      name <- json.get[String](JsonFields.name)
      namespace <- json.getOrElse[String](JsonFields.namespace)(
        defaultNamespace
      )
      version     <- json.getOrElse[String](JsonFields.version)("dev")
      ensoVersion <- json.get[Option[EnsoVersion]](JsonFields.ensoVersion)
      rawEdition <- json
        .get[EditionName](JsonFields.edition)
        .map(x => Left(x.name))
        .orElse(
          json
            .get[Option[Editions.RawEdition]](JsonFields.edition)
            .map(Right(_))
        )
      edition = rawEdition.fold(
        editionName => Some(Editions.Raw.Edition(parent = Some(editionName))),
        identity
      )
      license    <- json.getOrElse(JsonFields.license)("")
      author     <- json.getOrElse[List[Contact]](JsonFields.author)(List())
      maintainer <- json.getOrElse[List[Contact]](JsonFields.maintainer)(List())
      preferLocal <-
        json.getOrElse[Boolean](JsonFields.preferLocalLibraries)(false)
      finalEdition <-
        editionOrVersionBackwardsCompatibility(edition, ensoVersion).left.map {
          error => DecodingFailure(error, json.history)
        }
      originals <- json.as[JsonObject]
    } yield Config(
      name                 = name,
      namespace            = namespace,
      version              = version,
      license              = license,
      authors              = author,
      maintainers          = maintainer,
      edition              = finalEdition,
      preferLocalLibraries = preferLocal,
      originalJson         = originals
    )
  }

  implicit val encoder: Encoder[Config] = { config =>
    val originals = config.originalJson

    val edition = config.edition
      .map { edition =>
        if (edition.isDerivingWithoutOverrides) edition.parent.get.asJson
        else edition.asJson
      }
      .map(JsonFields.edition -> _)

    val overrides = Seq(
      JsonFields.name       -> config.name.asJson,
      JsonFields.namespace  -> config.namespace.asJson,
      JsonFields.version    -> config.version.asJson,
      JsonFields.license    -> config.license.asJson,
      JsonFields.author     -> config.authors.asJson,
      JsonFields.maintainer -> config.maintainers.asJson
    ) ++ edition.toSeq

    val preferLocalOverride =
      if (config.preferLocalLibraries)
        Seq(JsonFields.preferLocalLibraries -> true.asJson)
      else Seq()
    val overridesObject = JsonObject(
      overrides ++ preferLocalOverride: _*
    )

    /** Fields that should not be inherited from the original set of fields.
      *
      * `ensoVersion` is dropped, because due to migration it is overridden by
      * `edition` and we don't want to have both to avoid inconsistency.
      *
      * `prefer-local-libraries` cannot be inherited, because if it was set to
      * `true` and we have changed it to `false`, overrides will not include it,
      * because, as `false` is its default value, we just ignore the field. But
      * if we inherit it from original fields, we would get `true` back. If the
      * setting is still set to true, it will be included in the overrides, so
      * it does not have to be inherited either.
      */
    val fieldsToRemoveFromOriginals =
      Seq(JsonFields.ensoVersion, JsonFields.preferLocalLibraries)

    val removed = fieldsToRemoveFromOriginals.foldLeft(originals) {
      case (obj, key) => obj.remove(key)
    }
    removed.deepMerge(overridesObject).asJson
  }

  /** Tries to parse the [[Config]] from a YAML string. */
  def fromYaml(yamlString: String): Try[Config] = {
    yaml.parser.parse(yamlString).flatMap(_.as[Config]).toTry
  }

  /** Creates a simple edition that just defines the provided engine version.
    *
    * A compatibility layer for migrating from just specifying the engine
    * version to the edition system.
    *
    * TODO [RW] once the edition is actually used for loading libraries, this
    * may need to be revisited, because an edition created in this way will not
    * have any libraries present which is highly undesirable. We may either
    * remove the compatibility layer and return errors for the old format or
    * need to use the latest/default edition or some hardcoded edition for
    * compatibility.
    */
  def makeCompatibilityEditionFromVersion(
    ensoVersion: SemVer
  ): Editions.RawEdition = Editions.Raw.Edition(
    parent        = None,
    engineVersion = Some(ensoVersion),
    repositories  = Map(),
    libraries     = Map()
  )

  /** A helper method that reconciles the old and new fields of the config
    * related to the edition.
    *
    * If an edition is present, it is just returned as-is. If the engine version
    * is specified, a special edition is created that specifies this particular
    * engine version and nothing else.
    *
    * If both fields are defined, an error is raised as the configuration may be
    * inconsistent - the `engine-version` field should only be present in old
    * configs and after migration to the edition format it should be removed.
    */
  private def editionOrVersionBackwardsCompatibility(
    edition: Option[Editions.RawEdition],
    ensoVersion: Option[EnsoVersion]
  ): Either[String, Option[Editions.RawEdition]] =
    (edition, ensoVersion) match {
      case (Some(_), Some(_)) =>
        Left(
          s"The deprecated `${JsonFields.ensoVersion}` should not be defined " +
          s"if the `${JsonFields.edition}` that replaces it is already defined."
        )
      case (Some(edition), _) =>
        Right(Some(edition))
      case (_, Some(SemVerEnsoVersion(version))) =>
        Right(Some(makeCompatibilityEditionFromVersion(version)))
      case (_, Some(DefaultEnsoVersion)) =>
        // If the `default` version is specified, we return None, so that later
        // on, it will fallback to the default edition.
        Right(None)
      case (None, None) =>
        Right(None)
    }
}
