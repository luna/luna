package org.enso.projectmanager.service

import java.util.UUID

import akka.actor.ActorRef
import nl.gn0s1s.bump.SemVer
import org.enso.projectmanager.data.{
  MissingComponentAction,
  ProjectMetadata,
  RunningLanguageServerInfo
}
import org.enso.projectmanager.model.Project

/** A contract for the Project Service.
  *
  * @tparam F a monadic context
  */
trait ProjectServiceApi[F[+_, +_]] {

  /** Creates a user project.
    *
    * @param progressTracker the actor to send progress updates to
    * @param name the name of the project
    * @param engineVersion Enso version to use for the new project
    * @param projectTemplate the name of the project template
    * @param missingComponentAction specifies how to handle missing components
    * @return project
    */
  def createUserProject(
    progressTracker: ActorRef,
    name: String,
    engineVersion: SemVer,
    projectTemplate: Option[String],
    missingComponentAction: MissingComponentAction
  ): F[ProjectServiceFailure, Project]

  /** Deletes a user project.
    *
    * @param projectId the project id
    * @return either failure or unit representing success
    */
  def deleteUserProject(projectId: UUID): F[ProjectServiceFailure, Unit]

  /** Renames a project.
    *
    * @param projectId the project id
    * @param name the new name
    * @return either failure or unit representing success
    */
  def renameProject(
    projectId: UUID,
    name: String
  ): F[ProjectServiceFailure, Unit]

  /** Opens a project. It starts up a Language Server if needed.
    *
    * @param progressTracker the actor to send progress updates to
    * @param clientId the requester id
    * @param projectId the project id
    * @param missingComponentAction specifies how to handle missing components
    * @return either failure or a socket of the Language Server
    */
  def openProject(
    progressTracker: ActorRef,
    clientId: UUID,
    projectId: UUID,
    missingComponentAction: MissingComponentAction
  ): F[ProjectServiceFailure, RunningLanguageServerInfo]

  /** Closes a project. Tries to shut down the Language Server.
    *
    * @param clientId the requester id
    * @param projectId the project id
    * @return either failure or [[Unit]] representing void success
    */
  def closeProject(
    clientId: UUID,
    projectId: UUID
  ): F[ProjectServiceFailure, Unit]

  /** Lists the user's most recently opened projects..
    *
    * @param maybeSize the size of result set
    * @return list of recent projects
    */
  def listProjects(
    maybeSize: Option[Int]
  ): F[ProjectServiceFailure, List[ProjectMetadata]]

}
