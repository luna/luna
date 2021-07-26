package org.enso.projectmanager.requesthandler

import akka.actor._
import org.enso.editions.DefaultEnsoVersion
import org.enso.projectmanager.control.core.CovariantFlatMap
import org.enso.projectmanager.control.core.syntax._
import org.enso.projectmanager.control.effect.syntax._
import org.enso.projectmanager.control.effect.{ErrorChannel, Exec}
import org.enso.projectmanager.data.MissingComponentAction
import org.enso.projectmanager.protocol.ProjectManagementApi.ProjectCreate
import org.enso.projectmanager.requesthandler.ProjectServiceFailureMapper.failureMapper
import org.enso.projectmanager.service.config.GlobalConfigServiceApi
import org.enso.projectmanager.service.{
  ProjectServiceApi,
  ProjectServiceFailure
}

import scala.concurrent.duration.FiniteDuration

/** A request handler for `project/create` commands.
  *
  * @param configurationService the configuration service
  * @param projectService a project service
  * @param requestTimeout a request timeout
  */
class ProjectCreateHandler[F[+_, +_]: Exec: CovariantFlatMap: ErrorChannel](
  configurationService: GlobalConfigServiceApi[F],
  projectService: ProjectServiceApi[F],
  requestTimeout: FiniteDuration
) extends RequestHandler[
      F,
      ProjectServiceFailure,
      ProjectCreate.type,
      ProjectCreate.Params,
      ProjectCreate.Result
    ](
      ProjectCreate,
      Some(requestTimeout)
    ) {

  override def handleRequest = { params =>
    val version = params.version.getOrElse(DefaultEnsoVersion)
    val missingComponentAction =
      params.missingComponentAction.getOrElse(MissingComponentAction.Fail)

    for {
      actualVersion <- configurationService
        .resolveEnsoVersion(version)
        .mapError { error =>
          ProjectServiceFailure.ComponentRepositoryAccessFailure(
            s"Could not determine the default version: $error"
          )
        }
      _ = logger.trace(s"Creating project using engine $actualVersion")
      projectId <- projectService.createUserProject(
        progressTracker        = self,
        name                   = params.name,
        engineVersion          = actualVersion,
        missingComponentAction = missingComponentAction
      )
    } yield ProjectCreate.Result(projectId)
  }
}

object ProjectCreateHandler {

  /** Creates a configuration object used to create a [[ProjectCreateHandler]].
    *
    * @param configurationService
    * @param projectService a project service
    * @param requestTimeout a request timeout
    * @return a configuration object
    */
  def props[F[+_, +_]: Exec: CovariantFlatMap: ErrorChannel](
    configurationService: GlobalConfigServiceApi[F],
    projectService: ProjectServiceApi[F],
    requestTimeout: FiniteDuration
  ): Props =
    Props(
      new ProjectCreateHandler(
        configurationService,
        projectService,
        requestTimeout
      )
    )

}
