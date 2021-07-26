package org.enso.librarymanager.published.repository

import nl.gn0s1s.bump.SemVer
import org.enso.cli.task.TaskProgress
import org.enso.distribution.FileSystem.PathSyntax
import org.enso.downloader.http.{HTTPDownload, URIBuilder}
import org.enso.editions.Editions.Repository
import org.enso.editions.LibraryName
import org.enso.pkg.Package
import org.enso.yaml.YamlHelper

import java.nio.file.Path
import scala.util.Failure

/** A class that manages the HTTP API of the Library Repository.
  *
  * @see docs/libraries/repositories.md#libraries-repository
  */
object RepositoryHelper {

  /** Adds extension methods to the [[Repository]] type. */
  implicit class RepositoryMethods(val repository: Repository) {

    /** Creates a [[LibraryAccess]] instance that aids with downloading data of
      * the given library.
      */
    def accessLibrary(name: LibraryName, version: SemVer): LibraryAccess =
      new LibraryAccess(name, version, resolveLibraryRoot(name, version))

    /** Creates a [[URIBuilder]] that points to the directory in the repository
      * corresponding to the given library.
      */
    def resolveLibraryRoot(name: LibraryName, version: SemVer): URIBuilder =
      URIBuilder
        .fromUri(repository.url)
        .addPathSegment(name.namespace)
        .addPathSegment(name.name)
        .addPathSegment(version.toString)
  }

  /** A helper class that allows to access the Library Repository to query it
    * for metadata of a specific library or download its packages.
    *
    * @param libraryName name of the library
    * @param version     version of the library
    * @param libraryRoot a [[URIBuilder]] that points to the directory
    *                    corresponding to the library
    */
  class LibraryAccess(
    libraryName: LibraryName,
    version: SemVer,
    libraryRoot: URIBuilder
  ) {

    /** Downloads and parses the manifest file.
      *
      * If the repository responds with 404 status code, it returns a special
      * [[LibraryNotFoundException]] indicating that the repository does not
      * provide that library. Any other failures are indicated with the more
      * generic [[LibraryDownloadFailure]].
      */
    def downloadManifest(): TaskProgress[LibraryManifest] = {
      val url = (libraryRoot / manifestFilename).build()
      HTTPDownload.fetchString(url).flatMap { response =>
        response.statusCode match {
          case 200 =>
            YamlHelper.parseString[LibraryManifest](response.content).toTry
          case 404 =>
            Failure(
              LibraryNotFoundException(libraryName, version, url.toString)
            )
          case code =>
            Failure(
              new LibraryDownloadFailure(
                s"Could not download the manifest: The repository responded " +
                s"with $code status code."
              )
            )
        }
      }
    }

    /** A helper that downloads an artifact to a specific location. */
    private def downloadArtifact(
      artifactName: String,
      destination: Path
    ): TaskProgress[Unit] = {
      val url = (libraryRoot / artifactName).build()
      HTTPDownload.download(url, destination).map(_ => ())
    }

    /** Downloads the license file.
      *
      * It will fail with `ResourceNotFound` error if the license did not exist
      * and with a more generic `HTTPException` if it failed for other reasons.
      */
    def downloadLicense(destinationDirectory: Path): TaskProgress[Unit] =
      downloadArtifact(licenseFilename, destinationDirectory / licenseFilename)

    /** Downloads the package config file. */
    def downloadPackageConfig(destinationDirectory: Path): TaskProgress[Unit] =
      downloadArtifact(packageFileName, destinationDirectory / packageFileName)

    /** Downloads a sub-archive. */
    def downloadArchive(
      archiveName: String,
      destinationDirectory: Path
    ): TaskProgress[Unit] = downloadArtifact(archiveName, destinationDirectory)
  }

  /** Name of the manifest file. */
  val manifestFilename = "manifest.yaml"

  /** Name of the attached license file. */
  val licenseFilename = "LICENSE.md"

  /** Name of the package config file. */
  val packageFileName: String = Package.configFileName
}
