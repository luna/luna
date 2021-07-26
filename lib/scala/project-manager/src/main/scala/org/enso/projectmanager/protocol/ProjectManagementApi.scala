package org.enso.projectmanager.protocol

import java.util.UUID
import io.circe.Json
import io.circe.syntax._
import nl.gn0s1s.bump.SemVer
import org.enso.editions.EnsoVersion
import org.enso.jsonrpc.{Error, HasParams, HasResult, Method, Unused}
import org.enso.projectmanager.data.{
  EngineVersion,
  MissingComponentAction,
  ProjectMetadata,
  Socket
}

/** The project management JSON RPC API provided by the project manager.
  * See [[https://github.com/enso-org/enso/blob/main/docs/language-server/README.md]]
  * for message specifications.
  */
object ProjectManagementApi {

  case object ProjectCreate extends Method("project/create") {

    case class Params(
      name: String,
      version: Option[EnsoVersion],
      missingComponentAction: Option[MissingComponentAction]
    )

    case class Result(projectId: UUID)

    implicit val hasParams = new HasParams[this.type] {
      type Params = ProjectCreate.Params
    }

    implicit val hasResult = new HasResult[this.type] {
      type Result = ProjectCreate.Result
    }
  }

  case object ProjectDelete extends Method("project/delete") {

    case class Params(projectId: UUID)

    implicit val hasParams = new HasParams[this.type] {
      type Params = ProjectDelete.Params
    }

    implicit val hasResult = new HasResult[this.type] {
      type Result = Unused.type
    }
  }

  case object ProjectRename extends Method("project/rename") {

    case class Params(projectId: UUID, name: String)

    implicit val hasParams = new HasParams[this.type] {
      type Params = ProjectRename.Params
    }

    implicit val hasResult = new HasResult[this.type] {
      type Result = Unused.type
    }
  }

  case object ProjectOpen extends Method("project/open") {

    case class Params(
      projectId: UUID,
      missingComponentAction: Option[MissingComponentAction]
    )

    case class Result(
      engineVersion: SemVer,
      languageServerJsonAddress: Socket,
      languageServerBinaryAddress: Socket,
      projectName: String,
      projectNamespace: String
    )

    implicit val hasParams = new HasParams[this.type] {
      type Params = ProjectOpen.Params
    }

    implicit val hasResult = new HasResult[this.type] {
      type Result = ProjectOpen.Result
    }
  }

  case object ProjectClose extends Method("project/close") {

    case class Params(projectId: UUID)

    implicit val hasParams = new HasParams[this.type] {
      type Params = ProjectClose.Params
    }

    implicit val hasResult = new HasResult[this.type] {
      type Result = Unused.type
    }
  }

  case object ProjectList extends Method("project/list") {

    case class Params(numberOfProjects: Option[Int])

    case class Result(projects: List[ProjectMetadata])

    implicit val hasParams = new HasParams[this.type] {
      type Params = ProjectList.Params
    }

    implicit val hasResult = new HasResult[this.type] {
      type Result = ProjectList.Result
    }
  }

  case object EngineListInstalled extends Method("engine/list-installed") {

    case class Result(versions: Seq[EngineVersion])

    implicit val hasParams = new HasParams[this.type] {
      type Params = Unused.type
    }

    implicit val hasResult = new HasResult[this.type] {
      type Result = EngineListInstalled.Result
    }
  }

  case object EngineListAvailable extends Method("engine/list-available") {

    case class Result(versions: Seq[EngineVersion])

    implicit val hasParams = new HasParams[this.type] {
      type Params = Unused.type
    }

    implicit val hasResult = new HasResult[this.type] {
      type Result = EngineListAvailable.Result
    }
  }

  case object EngineInstall extends Method("engine/install") {

    case class Params(version: SemVer, forceInstallBroken: Option[Boolean])

    implicit val hasParams = new HasParams[this.type] {
      type Params = EngineInstall.Params
    }

    implicit val hasResult = new HasResult[this.type] {
      type Result = Unused.type
    }
  }

  case object EngineUninstall extends Method("engine/uninstall") {

    case class Params(version: SemVer)

    implicit val hasParams = new HasParams[this.type] {
      type Params = EngineUninstall.Params
    }

    implicit val hasResult = new HasResult[this.type] {
      type Result = Unused.type
    }
  }

  case object ConfigGet extends Method("global-config/get") {

    case class Params(key: String)

    case class Result(value: Option[String])

    implicit val hasParams = new HasParams[this.type] {
      type Params = ConfigGet.Params
    }

    implicit val hasResult = new HasResult[this.type] {
      type Result = ConfigGet.Result
    }
  }

  case object ConfigSet extends Method("global-config/set") {

    case class Params(key: String, value: String)

    implicit val hasParams = new HasParams[this.type] {
      type Params = ConfigSet.Params
    }

    implicit val hasResult = new HasResult[this.type] {
      type Result = Unused.type
    }
  }

  case object ConfigDelete extends Method("global-config/delete") {

    case class Params(key: String)

    implicit val hasParams = new HasParams[this.type] {
      type Params = ConfigDelete.Params
    }

    implicit val hasResult = new HasResult[this.type] {
      type Result = Unused.type
    }
  }

  case object LoggingServiceGetEndpoint
      extends Method("logging-service/get-endpoint") {

    case class Result(uri: String)

    implicit val hasParams = new HasParams[this.type] {
      type Params = Unused.type
    }

    implicit val hasResult = new HasResult[this.type] {
      type Result = LoggingServiceGetEndpoint.Result
    }
  }

  case class MissingComponentError(msg: String) extends Error(4020, msg)

  case class BrokenComponentError(msg: String) extends Error(4021, msg)

  case class ProjectManagerUpgradeRequired(minimumRequiredVersion: SemVer)
      extends Error(
        4022,
        s"Project manager $minimumRequiredVersion is required to install the " +
        s"requested engine. Please upgrade."
      ) {

    /** Additional payload that can be used to get the version string of the
      * minimum required project manager version.
      */
    override def payload: Option[Json] = Some(
      Json.obj(
        "minimumRequiredVersion" -> minimumRequiredVersion.toString.asJson
      )
    )
  }

  case class ComponentInstallationError(msg: String) extends Error(4023, msg)

  case class ComponentUninstallationError(msg: String) extends Error(4024, msg)

  case class ComponentRepositoryUnavailable(msg: String)
      extends Error(4025, msg)

  case class ProjectNameValidationError(msg: String) extends Error(4001, msg)

  case class ProjectDataStoreError(msg: String) extends Error(4002, msg)

  case object ProjectExistsError
      extends Error(4003, "Project with the provided name exists")

  case object ProjectNotFoundError
      extends Error(4004, "Project with the provided id does not exist")

  case class ProjectOpenError(msg: String) extends Error(4005, msg)

  case object ProjectNotOpenError
      extends Error(4006, "Cannot close project that is not open")

  case object ProjectOpenByOtherPeersError
      extends Error(
        4007,
        "Cannot close project because it is open by other peers"
      )

  case object CannotRemoveOpenProjectError
      extends Error(4008, "Cannot remove open project")

  case class ProjectCloseError(msg: String) extends Error(4009, msg)

  case class LanguageServerError(msg: String) extends Error(4010, msg)

  case class GlobalConfigurationAccessError(msg: String)
      extends Error(4011, msg)

  case class ProjectCreateError(msg: String) extends Error(4012, msg)

  case class LoggingServiceUnavailable(msg: String) extends Error(4013, msg)

}
