package org.enso.editions

import cats.Show
import io.circe._
import io.circe.syntax.EncoderOps
import nl.gn0s1s.bump.SemVer
import org.enso.editions.Editions.{Raw, Repository}
import org.enso.editions.SemVerJson._
import org.enso.yaml.YamlHelper

import java.io.FileReader
import java.nio.file.Path
import scala.util.{Failure, Try, Using}

/** Gathers methods for decoding and encoding of Raw editions. */
object EditionSerialization {

  /** Tries to parse an edition definition from a string in the YAML format. */
  def parseYamlString(yamlString: String): Try[Raw.Edition] =
    YamlHelper
      .parseString[Raw.Edition](yamlString)
      .left
      .map(EditionResolutionError.EditionParseError)
      .toTry

  /** Tries to load an edition definition from a YAML file. */
  def loadEdition(path: Path): Try[Raw.Edition] =
    Using(new FileReader(path.toFile)) { reader =>
      yaml.parser
        .parse(reader)
        .flatMap(_.as[Raw.Edition])
        .toTry
    }.flatten
      .recoverWith { error =>
        Failure(
          EditionResolutionError.wrapLoadingError(
            path.getFileName.toString,
            error
          )
        )
      }

  /** A [[Decoder]] instance for [[Raw.Edition]].
    *
    * It can be used to decode nested editions in other kinds of configurations
    * files, for example in `package.yaml`.
    */
  implicit val editionDecoder: Decoder[Raw.Edition] = { json =>
    for {
      parent        <- json.get[Option[EditionName]](Fields.parent)
      engineVersion <- json.get[Option[SemVer]](Fields.engineVersion)
      _ <-
        if (parent.isEmpty && engineVersion.isEmpty)
          Left(
            DecodingFailure(
              s"The edition must specify at least one of " +
              s"${Fields.engineVersion} or ${Fields.parent}.",
              json.history
            )
          )
        else Right(())
      repositories <-
        json.getOrElse[Seq[Repository]](Fields.repositories)(Seq())
      libraries <- json.getOrElse[Seq[Raw.Library]](Fields.libraries)(Seq())
      res <- {
        val repositoryMap = Map.from(repositories.map(r => (r.name, r)))
        val libraryMap    = Map.from(libraries.map(l => (l.name, l)))
        if (libraryMap.size != libraries.size)
          Left(
            DecodingFailure(
              "Names of libraries defined within a single edition file must be unique.",
              json.downField(Fields.libraries).history
            )
          )
        else if (repositoryMap.size != repositories.size)
          Left(
            DecodingFailure(
              "Names of repositories defined within a single edition file must be unique.",
              json.downField(Fields.libraries).history
            )
          )
        else
          Right(
            Raw.Edition(
              parent        = parent.map(_.name),
              engineVersion = engineVersion,
              repositories  = repositoryMap,
              libraries     = libraryMap
            )
          )
      }
    } yield res
  }

  /** An [[Encoder]] instance for [[Raw.Edition]]. */
  implicit val editionEncoder: Encoder[Raw.Edition] = { edition =>
    val parent = edition.parent.map { parent => Fields.parent -> parent.asJson }
    val engineVersion = edition.engineVersion.map { version =>
      Fields.engineVersion -> version.asJson
    }

    if (parent.isEmpty && engineVersion.isEmpty) {
      throw new IllegalArgumentException(
        "Internal error: An edition must specify at least the engine version or extends clause"
      )
    }

    val repositories =
      if (edition.repositories.isEmpty) None
      else Some(Fields.repositories -> edition.repositories.values.asJson)
    val libraries =
      if (edition.libraries.isEmpty) None
      else Some(Fields.libraries -> edition.libraries.values.asJson)
    Json.obj(
      parent.toSeq ++ engineVersion.toSeq ++ repositories.toSeq ++ libraries.toSeq: _*
    )
  }

  object Fields {
    val name                = "name"
    val version             = "version"
    val repository          = "repository"
    val url                 = "url"
    val parent              = "extends"
    val engineVersion       = "engine-version"
    val repositories        = "repositories"
    val libraries           = "libraries"
    val localRepositoryName = "local"
  }

  case class EditionLoadingError(message: String, cause: Throwable)
      extends RuntimeException(message, cause) {

    /** @inheritdoc */
    override def toString: String = message
  }

  object EditionLoadingError {

    /** Creates a [[EditionLoadingError]] by wrapping another [[Throwable]].
      *
      * Special logic is used for [[io.circe.Error]] to display the error
      * summary in a human-readable way.
      */
    def fromThrowable(throwable: Throwable): EditionLoadingError =
      throwable match {
        case decodingError: io.circe.Error =>
          val errorMessage =
            implicitly[Show[io.circe.Error]].show(decodingError)
          EditionLoadingError(
            s"Could not parse the edition file: $errorMessage",
            decodingError
          )
        case other =>
          EditionLoadingError(s"Could not load the edition file: $other", other)
      }
  }

  implicit private val libraryDecoder: Decoder[Raw.Library] = { json =>
    def makeLibrary(
      name: LibraryName,
      repository: String,
      version: Option[SemVer]
    ) =
      if (repository == Fields.localRepositoryName)
        if (version.isDefined)
          Left(
            DecodingFailure(
              "Version field must not be set for libraries associated with the local repository.",
              json.history
            )
          )
        else Right(Raw.LocalLibrary(name))
      else {
        version match {
          case Some(versionValue) =>
            Right(Raw.PublishedLibrary(name, versionValue, repository))
          case None =>
            Left(
              DecodingFailure(
                "Version field is mandatory for non-local libraries.",
                json.history
              )
            )
        }
      }
    for {
      name       <- json.get[LibraryName](Fields.name)
      repository <- json.get[String](Fields.repository)
      version    <- json.get[Option[SemVer]](Fields.version)
      res        <- makeLibrary(name, repository, version)
    } yield res
  }

  implicit private val libraryEncoder: Encoder[Raw.Library] = {
    case Raw.LocalLibrary(name) =>
      Json.obj(
        Fields.name       -> name.asJson,
        Fields.repository -> Fields.localRepositoryName.asJson
      )
    case Raw.PublishedLibrary(name, version, repository) =>
      Json.obj(
        Fields.name       -> name.asJson,
        Fields.version    -> version.asJson,
        Fields.repository -> repository.asJson
      )
  }

  implicit private val repositoryEncoder: Encoder[Repository] = { repo =>
    Json.obj(
      Fields.name -> repo.name.asJson,
      Fields.url  -> repo.url.asJson
    )
  }

  implicit private val repositoryDecoder: Decoder[Repository] = { json =>
    val nameField = json.downField(Fields.name)
    for {
      name <- nameField.as[String]
      url  <- json.get[String](Fields.url)
      res <-
        if (name == Fields.localRepositoryName)
          Left(
            DecodingFailure(
              s"A defined repository cannot be called " +
              s"`${Fields.localRepositoryName}` which is a reserved keyword.",
              nameField.history
            )
          )
        else Right(Repository(name, url))
    } yield res
  }
}
