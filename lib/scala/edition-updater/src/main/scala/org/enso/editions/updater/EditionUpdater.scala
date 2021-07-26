package org.enso.editions.updater

import com.typesafe.scalalogging.Logger
import org.enso.downloader.http.{HTTPDownload, HTTPRequestBuilder, URIBuilder}
import org.enso.editions.EditionName
import org.enso.editions.repository.Manifest
import org.enso.yaml.YamlHelper

import java.nio.file.{Files, Path}
import scala.util.{Failure, Try}

/** A helper class that handles updating available editions.
  *
  * It downloads lists of available editions from all sources and downloads any
  * missing (thus new) editions. Any editions that are already cached are not
  * re-downloaded, as we assume that, once published, the edition is immutable.
  *
  * If two sources provide an edition with the same name, the one that is first
  * on the sources list will take precedence.
  *
  * @param cachePath the path to the directory that contains the cached editions
  *                  and where the new editions will be downloaded to
  * @param sources the list of URLs indicating roots of edition repositories
  *                that should be queried for new editions
  */
class EditionUpdater(cachePath: Path, sources: Seq[String]) {
  private lazy val logger = Logger[EditionUpdater]

  /** Downloads edition lists from the [[sources]] and downloads any missing
    * editions to the [[cachePath]].
    *
    * If there are errors when processing one of the edition sources or
    * downloading the editions, the errors are logged as warnings, but other
    * sources proceed as normal.
    */
  def updateEditions(): Try[Unit] = Try {
    for {
      source <- sources
      repositoryRoot <- Try { URIBuilder.fromUri(source) }
        .recoverWith { error =>
          logger.warn(s"Failed to parse the source URI [$source]: $error")
          Failure(error)
        }
      manifest <- downloadEditionRepositoryManifest(repositoryRoot)
        .recoverWith { error =>
          logger.warn(s"Failed to fetch editions from [$source]: $error")
          Failure(error)
        }
      edition <- manifest.editions
      if !isEditionAlreadyCached(edition)
    } {
      downloadEdition(repositoryRoot, edition).getOrElse {
        logger.warn(s"Failed to download edition [$edition] from [$source].")
      }
    }
  }

  private def downloadEditionRepositoryManifest(
    repositoryRoot: URIBuilder
  ): Try[Manifest] =
    Try {
      val uri      = repositoryRoot.addPathSegment(Manifest.filename).build()
      val request  = HTTPRequestBuilder.fromURI(uri).GET
      val response = HTTPDownload.fetchString(request).force()
      YamlHelper.parseString[Manifest](response.content).toTry.get
    }

  private def downloadEdition(
    repositoryRoot: URIBuilder,
    editionName: EditionName
  ): Try[Unit] = Try {
    val destinationPath = cachePath.resolve(editionName.toFileName)

    val uri     = repositoryRoot.addPathSegment(editionName.toFileName).build()
    val request = HTTPRequestBuilder.fromURI(uri).GET

    HTTPDownload.download(request, destinationPath).force()
  }

  private def isEditionAlreadyCached(editionName: EditionName): Boolean =
    Files.exists(cachePath.resolve(editionName.toFileName))
}
