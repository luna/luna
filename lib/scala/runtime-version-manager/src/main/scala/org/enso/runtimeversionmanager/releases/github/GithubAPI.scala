package org.enso.runtimeversionmanager.releases.github

import java.nio.file.Path
import io.circe._
import io.circe.parser._
import org.enso.cli.task.TaskProgress
import org.enso.downloader.http.{
  APIResponse,
  HTTPDownload,
  HTTPRequestBuilder,
  Header,
  URIBuilder
}
import org.enso.runtimeversionmanager.releases.{
  ReleaseNotFound,
  ReleaseProviderException
}

import scala.util.{Success, Try}

/** Contains functions used to query the GitHubAPI endpoints.
  */
object GithubAPI {

  /** Represents a GitHub repository.
    *
    * @param owner owner of the repository
    * @param name name of the repository
    */
  case class Repository(owner: String, name: String)

  /** Represents a GitHub release.
    *
    * @param tag tag associated with the release
    * @param assets sequence of assets present in this release
    */
  case class Release(tag: String, assets: Seq[Asset])

  /** Represents an asset available in a [[Release]]
    *
    * @param name filename of that asset
    * @param url URL that can be used to download this asset
    * @param size size of the asset in bytes
    */
  case class Asset(name: String, url: String, size: Long)

  /** Returns a list of all releases in the repository.
    *
    * It fetches all available pages of releases, to make sure all releases are
    * included. This is necessary, because the GitHub API does not guarantee
    * that the releases are returned in order. The 'empirical' order seems to be
    * 'latest first', but even assuming that is a too weak guarantee, because
    * *theoretically* so many patches for an earlier release could be released,
    * that the latest release in the semver sense will not make it to the first
    * page. So to be absolutely sure that we can find the latest release, we
    * need to list all of them.
    *
    * The endpoint is blocking, because we cannot estimate how many requests
    * will be necessary and each individual request should be quick as it just
    * downloads some text, so there is not much gain in displaying a progress
    * bar for this task.
    */
  def listReleases(repository: Repository): Try[Seq[Release]] = {
    val perPage = 100
    def listPage(page: Int): Try[Seq[Release]] = {
      val uri = (projectURI(repository) / "releases") ?
        ("per_page" -> perPage.toString) ? ("page" -> page.toString)

      val downloadTask = HTTPDownload
        .fetchString(HTTPRequestBuilder.fromURI(uri.build()).GET)
        .flatMap(response =>
          parse(response.content)
            .flatMap(
              _.as[Seq[Release]].left
                .map(err =>
                  handleError(
                    response,
                    ReleaseProviderException(s"Cannot fetch release list.", err)
                  )
                )
            )
            .toTry
        )
      TaskProgress.waitForTask(downloadTask)
    }

    def listAllPages(from: Int): Try[Seq[Release]] =
      listPage(from).flatMap { current =>
        if (current.length == perPage)
          listAllPages(from + 1).map(current ++ _)
        else
          Success(current)
      }

    listAllPages(1)
  }

  /** Fetches release metadata for the release associated with the given tag.
    */
  def getRelease(repo: Repository, tag: String): TaskProgress[Release] = {
    val uri = projectURI(repo) / "releases" / "tags" / tag

    HTTPDownload
      .fetchString(HTTPRequestBuilder.fromURI(uri.build()).GET)
      .flatMap(response =>
        parse(response.content)
          .flatMap(_.as[Release])
          .left
          .map(err =>
            handleError(
              response,
              ReleaseNotFound(tag, cause = err)
            )
          )
          .toTry
      )
  }

  /** A helper function that detecte a rate-limit error and tries to make a more
    * friendly user message.
    *
    * If the rate-limit is hit, an error message is returned by the API which is
    * of course not parsed correctly by a reader that expects a normal response.
    * The rate-limit is detected and if it caused the error, the error is
    * overridden with a message explaining the rate-limit.
    */
  private def handleError(
    response: APIResponse,
    defaultError: => Throwable
  ): Throwable = {
    def isLimitExceeded(header: Header): Boolean =
      header.value.toIntOption.contains(0)

    response.headers.find(_.is("X-RateLimit-Remaining")) match {
      case Some(header) if isLimitExceeded(header) =>
        ReleaseProviderException(
          "GitHub Release API rate limit exceeded for your IP address. " +
          "Please try again in a while."
        )
      case _ => defaultError
    }
  }

  /** Fetches an asset as text.
    *
    * Returns a [[TaskProgress]] that will return the asset contents as a
    * [[String]] on success.
    */
  def fetchTextAsset(asset: Asset): TaskProgress[String] = {
    val request = HTTPRequestBuilder
      .fromURIString(asset.url)
      .addHeader("Accept", "application/octet-stream")
      .GET

    HTTPDownload.fetchString(request, Some(asset.size)).map(_.content)
  }

  /** Downloads the asset to the provided `destination`.
    *
    * The returned [[TaskProgress]] succeeds iff the download was successful.
    */
  def downloadAsset(asset: Asset, destination: Path): TaskProgress[Unit] = {
    val request = HTTPRequestBuilder
      .fromURIString(asset.url)
      .addHeader("Accept", "application/octet-stream")
      .GET

    HTTPDownload
      .download(request, destination, Some(asset.size))
      .map(_ => ())
  }

  private val baseUrl =
    URIBuilder.fromHost("api.github.com")
  private def projectURI(project: Repository) =
    baseUrl / "repos" / project.owner / project.name

  implicit private val assetDecoder: Decoder[Asset] = { json =>
    for {
      url  <- json.get[String]("browser_download_url")
      name <- json.get[String]("name")
      size <- json.get[Long]("size")
    } yield Asset(name = name, url = url, size = size)
  }

  implicit private val releaseDecoder: Decoder[Release] = { json =>
    for {
      tag    <- json.get[String]("tag_name")
      assets <- json.get[Seq[Asset]]("assets")
    } yield Release(tag, assets)
  }
}
