package org.enso.languageserver.libraries

import io.circe.Json
import io.circe.literal.JsonStringContext
import org.enso.editions.{LibraryName, LibraryVersion}
import org.enso.jsonrpc.{Error, HasParams, HasResult, Method, Unused}

object LibraryApi {
  case object EditionsListAvailable extends Method("editions/listAvailable") {
    self =>

    case class Params(update: Boolean)

    case class Result(editionNames: Seq[String])

    implicit val hasParams = new HasParams[this.type] {
      type Params = self.Params
    }
    implicit val hasResult = new HasResult[this.type] {
      type Result = self.Result
    }
  }

  case object EditionsResolve extends Method("editions/resolve") {
    self =>

    case class Params(edition: EditionReference)

    case class Result(engineVersion: String)

    implicit val hasParams = new HasParams[this.type] {
      type Params = self.Params
    }
    implicit val hasResult = new HasResult[this.type] {
      type Result = self.Result
    }
  }

  case object EditionsGetProjectSettings
      extends Method("editions/getProjectSettings") { self =>

    case class Result(
      parentEdition: Option[String],
      preferLocalLibraries: Boolean
    )

    implicit val hasParams = new HasParams[this.type] {
      type Params = Unused.type
    }
    implicit val hasResult = new HasResult[this.type] {
      type Result = self.Result
    }
  }

  case object EditionsSetParentEdition
      extends Method("editions/setParentEdition") { self =>

    case class Params(newEditionName: String)

    case class Result(needsRestart: Option[Boolean])

    implicit val hasParams = new HasParams[this.type] {
      type Params = self.Params
    }
    implicit val hasResult = new HasResult[this.type] {
      type Result = self.Result
    }
  }

  case object EditionsSetLocalLibrariesPreference
      extends Method("editions/setProjectLocalLibrariesPreference") { self =>

    case class Params(preferLocalLibraries: Boolean)

    case class Result(needsRestart: Option[Boolean])

    implicit val hasParams = new HasParams[this.type] {
      type Params = self.Params
    }
    implicit val hasResult = new HasResult[this.type] {
      type Result = self.Result
    }
  }

  case object EditionsListDefinedLibraries
      extends Method("editions/listDefinedLibraries") { self =>

    case class Params(edition: EditionReference)

    case class Result(availableLibraries: Seq[LibraryEntry])

    implicit val hasParams = new HasParams[this.type] {
      type Params = self.Params
    }
    implicit val hasResult = new HasResult[this.type] {
      type Result = self.Result
    }
  }

  case object LibraryListLocal extends Method("library/listLocal") { self =>

    case class Result(localLibraries: Seq[LibraryEntry])

    implicit val hasParams = new HasParams[this.type] {
      type Params = Unused.type
    }
    implicit val hasResult = new HasResult[this.type] {
      type Result = self.Result
    }
  }

  case object LibraryCreate extends Method("library/create") { self =>

    case class Params(
      namespace: String,
      name: String,
      authors: Seq[String],
      maintainers: Seq[String],
      license: String
    )

    implicit val hasParams = new HasParams[this.type] {
      type Params = self.Params
    }
    implicit val hasResult = new HasResult[this.type] {
      type Result = Unused.type
    }
  }

  case object LibraryGetMetadata extends Method("library/getMetadata") { self =>

    case class Params(namespace: String, name: String)

    case class Result(description: Option[String], tagLine: Option[String])

    implicit val hasParams = new HasParams[this.type] {
      type Params = self.Params
    }
    implicit val hasResult = new HasResult[this.type] {
      type Result = self.Result
    }
  }

  case object LibrarySetMetadata extends Method("library/setMetadata") { self =>

    case class Params(
      namespace: String,
      name: String,
      description: Option[String],
      tagLine: Option[String]
    )

    implicit val hasParams = new HasParams[this.type] {
      type Params = self.Params
    }
    implicit val hasResult = new HasResult[this.type] {
      type Result = Unused.type
    }
  }

  case object LibraryPublish extends Method("library/publish") { self =>

    case class Params(
      namespace: String,
      name: String,
      authToken: String,
      bumpVersionAfterPublish: Option[Boolean]
    )

    implicit val hasParams = new HasParams[this.type] {
      type Params = self.Params
    }
    implicit val hasResult = new HasResult[this.type] {
      type Result = Unused.type
    }
  }

  case object LibraryPreinstall extends Method("library/preinstall") { self =>

    case class Params(namespace: String, name: String)

    implicit val hasParams = new HasParams[this.type] {
      type Params = self.Params
    }
    implicit val hasResult = new HasResult[this.type] {
      type Result = Unused.type
    }
  }

  case class EditionNotFoundError(editionName: String)
      extends Error(8001, s"Edition [$editionName] could not be found.") {
    override def payload: Option[Json] = Some(
      json""" { "editionName" : $editionName } """
    )
  }

  case class LibraryAlreadyExists(libraryName: LibraryName)
      extends Error(8002, s"Library [$libraryName] already exists.")

  case class LibraryRepositoryAuthenticationError(reason: String)
      extends Error(8003, s"Authentication failed: $reason.")

  case class LibraryPublishError(reason: String)
      extends Error(8004, s"Could not publish the library: $reason.")

  case class LibraryUploadError(reason: String)
      extends Error(8005, s"Could not upload the library: $reason.")

  case class LibraryDownloadError(
    name: LibraryName,
    version: LibraryVersion,
    reason: String
  ) extends Error(8006, s"Could not download the library: $reason.") {
    override def payload: Option[Json] = Some(
      json""" {
            "namespace" : ${name.namespace},
            "name" : ${name.name},
            "version" : ${version.toString}
            } """
    )
  }

  case class LocalLibraryNotFound(libraryName: LibraryName)
      extends Error(8007, s"Local library [$libraryName] has not been found.")

  case class LibraryNotResolved(name: LibraryName)
      extends Error(8008, s"Could not resolve [$name].") {
    override def payload: Option[Json] = Some(
      json""" { 
            "namespace" : ${name.namespace},
            "name" : ${name.name}
            } """
    )
  }
}
