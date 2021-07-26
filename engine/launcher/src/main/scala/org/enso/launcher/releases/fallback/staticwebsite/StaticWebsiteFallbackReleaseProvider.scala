package org.enso.launcher.releases.fallback.staticwebsite

import org.enso.downloader.http.URIBuilder
import org.enso.runtimeversionmanager.releases.Release
import org.enso.launcher.releases.fallback.FallbackReleaseProvider

import scala.util.Try

/** Implements a fallback release provider which queries a simple static web
  * storage.
  *
  * This provider can use for example AWS S3 as its backend.
  *
  * It has the following requirements:
  * 1. It should contain a file `fallback-manifest.yaml` with a key `enabled` in
  *    the root directory. This key determines if the provider [[isEnabled]].
  * 2. It should contain a file `release-list.json` in the root directory.
  *    That file should contain a key `releases` which should contain a list of
  *    keys whose names are tags of the releases and their values are lists of
  *    their assets.
  * 3. A directory [[releasesDirectory]] that contains subdirectories for each
  *    release tag which contain the listed assets.
  *
  * It must adhere to the fallback mechanism specification as defined at
  * [[https://enso.org/docs/developer/docs/enso/distribution/fallback-launcher-release-infrastructure.html#fallback-infrastructure-specification]].
  *
  * @param bucketRoot root URI that should contain the described files
  */
class StaticWebsiteFallbackReleaseProvider(
  bucketRoot: URIBuilder,
  releasesDirectory: String
) extends FallbackReleaseProvider {
  private val storage = StaticWebsite(bucketRoot)
  private val provider =
    new FileStorageFallbackReleaseProvider(storage, releasesDirectory)

  /** @inheritdoc
    */
  override def isEnabled: Boolean = provider.isEnabled

  /** @inheritdoc
    */
  override def releaseForTag(tag: String): Try[Release] =
    provider.releaseForTag(tag)

  /** @inheritdoc
    */
  override def listReleases(): Try[Seq[Release]] = provider.listReleases()
}
