package org.enso.languageserver.libraries

import akka.actor.Props
import org.enso.editions.{DefaultEdition, EditionResolver, Editions}
import org.enso.pkg.PackageManager

import java.io.File
import scala.util.Try

/** An Actor that manages edition-related settings of the current project. */
class ProjectSettingsManager(
  projectRoot: File,
  editionResolver: EditionResolver
) extends BlockingSynchronizedRequestHandler {
  import ProjectSettingsManager._

  def requestStage: Receive = { case request: Request =>
    request match {
      case GetSettings =>
        startRequest(loadSettings())
      case SetParentEdition(editionName) =>
        startRequest(setParentEdition(editionName))
      case SetPreferLocalLibraries(preferLocalLibraries) =>
        startRequest(setPreferLocalLibraries(preferLocalLibraries))
    }
  }

  private def loadSettings(): Try[SettingsResponse] = for {
    pkg <- PackageManager.Default.loadPackage(projectRoot)
    edition = pkg.config.edition.getOrElse(DefaultEdition.getDefaultEdition)
  } yield SettingsResponse(edition.parent, pkg.config.preferLocalLibraries)

  private def setParentEdition(editionName: String): Try[SettingsUpdated] =
    for {
      pkg <- PackageManager.Default.loadPackage(projectRoot)
      newEdition = pkg.config.edition match {
        case Some(edition) => edition.copy(parent = Some(editionName))
        case None          => Editions.Raw.Edition(parent = Some(editionName))
      }
      _ <- editionResolver.resolve(newEdition).toTry
      updated = pkg.updateConfig { config =>
        config.copy(edition = Some(newEdition))
      }
      _ <- updated.save()
    } yield SettingsUpdated()

  private def setPreferLocalLibraries(
    preferLocalLibraries: Boolean
  ): Try[SettingsUpdated] = for {
    pkg <- PackageManager.Default.loadPackage(projectRoot)
    updated = pkg.updateConfig { config =>
      config.copy(preferLocalLibraries = preferLocalLibraries)
    }
    _ <- updated.save()
  } yield SettingsUpdated()
}

object ProjectSettingsManager {
  def props(projectRoot: File, editionResolver: EditionResolver): Props = Props(
    new ProjectSettingsManager(projectRoot, editionResolver)
  )

  /** A request to the [[ProjectSettingsManager]]. */
  sealed trait Request

  /** A request to get the current project settings. */
  case object GetSettings extends Request

  /** Response to [[GetSettings]]. */
  case class SettingsResponse(
    parentEdition: Option[String],
    preferLocalLibraries: Boolean
  )

  /** A request to set the parent edition for the project. */
  case class SetParentEdition(editionName: String) extends Request

  /** A request to set the local libraries preference. */
  case class SetPreferLocalLibraries(preferLocalLibraries: Boolean)
      extends Request

  /** A response to the requests that update project settings which indicates
    * that the settings were successfully updated.
    */
  case class SettingsUpdated()
}
