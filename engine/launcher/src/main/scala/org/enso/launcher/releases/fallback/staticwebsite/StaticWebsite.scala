package org.enso.launcher.releases.fallback.staticwebsite

import org.enso.cli.task.TaskProgress
import org.enso.downloader.http.{HTTPDownload, HTTPRequestBuilder, URIBuilder}

import java.nio.file.Path

/** Provides [[FileStorage]] backed by a static HTTPS website.
  *
  * @param root [[URIBuilder]] for the website's storage root
  */
case class StaticWebsite(root: URIBuilder) extends FileStorage {

  /** @inheritdoc
    */
  override def download(
    path: Seq[String],
    destination: Path
  ): TaskProgress[Unit] =
    HTTPDownload.download(makeGETRequest(path), destination).map(_ => ())

  /** @inheritdoc
    */
  override def fetchString(path: Seq[String]): TaskProgress[String] =
    HTTPDownload.fetchString(makeGETRequest(path)).map(_.content)

  private def makeGETRequest(path: Seq[String]) =
    HTTPRequestBuilder.fromURI(resolve(path).build()).GET

  private def resolve(path: Seq[String]): URIBuilder =
    path.foldLeft(root) { (uri, part) => uri / part }
}
