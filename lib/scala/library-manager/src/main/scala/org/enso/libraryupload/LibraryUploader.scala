package org.enso.libraryupload

import akka.http.scaladsl.marshalling.Marshal
import akka.http.scaladsl.model._
import akka.stream.scaladsl.Source
import com.typesafe.scalalogging.Logger
import nl.gn0s1s.bump.SemVer
import org.enso.cli.task.{ProgressReporter, TaskProgress}
import org.enso.distribution.FileSystem
import org.enso.distribution.FileSystem.PathSyntax
import org.enso.downloader.archive.TarGzWriter
import org.enso.downloader.http.{HTTPDownload, HTTPRequestBuilder, URIBuilder}
import org.enso.editions.LibraryName
import org.enso.librarymanager.published.repository.LibraryManifest
import org.enso.pkg.{Package, PackageManager}
import org.enso.yaml.YamlHelper

import java.nio.file.{Files, Path}
import scala.concurrent.{ExecutionContext, Future}
import scala.jdk.CollectionConverters._
import scala.util.{Failure, Success, Try, Using}

/** Gathers functions used for uploading libraries. */
object LibraryUploader {
  private lazy val logger = Logger[LibraryUploader.type]

  /** Uploads a library to a repository.
    *
    * @param projectRoot path to the library project root
    * @param uploadUrl an URL to the upload endpoint of a library repository
    * @param authToken a token describing the authentication method to use with
    *                  the repository
    * @param progressReporter a [[ProgressReporter]] to track long running tasks
    *                         like compression and upload
    * @param ec an execution context used for handling Futures
    */
  def uploadLibrary(
    projectRoot: Path,
    uploadUrl: String,
    authToken: auth.Token,
    progressReporter: ProgressReporter
  )(implicit ec: ExecutionContext): Try[Unit] = Try {
    FileSystem.withTemporaryDirectory("enso-upload") { tmpDir =>
      val pkg = PackageManager.Default.loadPackage(projectRoot.toFile).get
      val version = SemVer(pkg.config.version).getOrElse {
        throw new IllegalStateException(
          s"Project version [${pkg.config.version}] is not a valid semver " +
          s"string."
        )
      }
      val uri = buildUploadUri(uploadUrl, pkg.libraryName, version)

      val mainArchiveName = "main.tgz"
      val filesToIgnoreInArchive = Seq(
        Package.configFileName,
        LibraryManifest.filename
      )
      val archivePath = tmpDir / mainArchiveName
      val compressing =
        createMainArchive(projectRoot, filesToIgnoreInArchive, archivePath)
      progressReporter.trackProgress(
        s"Creating the [$mainArchiveName] archive.",
        compressing
      )
      compressing.force()

      val manifestPath = projectRoot / LibraryManifest.filename
      val loadedManifest =
        loadSavedManifest(manifestPath).getOrElse(LibraryManifest.empty)
      val updatedManifest =
        // TODO [RW] update dependencies in the manifest (#1773)
        loadedManifest.copy(archives = Seq(mainArchiveName))
      FileSystem.writeTextFile(manifestPath, YamlHelper.toYaml(updatedManifest))

      logger.info(s"Uploading library package to the server at [$uploadUrl].")
      val upload = uploadFiles(
        uri,
        authToken,
        files = Seq(
          projectRoot / Package.configFileName,
          projectRoot / LibraryManifest.filename,
          archivePath
        )
      )
      progressReporter.trackProgress(
        s"Uploading packages to [$uploadUrl].",
        upload
      )
      upload.force()

      logger.info(s"Upload complete.")
    }
  }

  /** Creates an URL for the upload, including information identifying the
    * library version.
    */
  private def buildUploadUri(
    baseUploadUrl: String,
    libraryName: LibraryName,
    version: SemVer
  ): Uri = {
    URIBuilder
      .fromUri(baseUploadUrl)
      .addQuery("namespace", libraryName.namespace)
      .addQuery("name", libraryName.name)
      .addQuery("version", version.toString)
      .build()
  }

  /** Gathers project files to create the main archive.
    *
    * For now it just filters out the files like manifest which are uploaded
    * separately. In the future this may be extended to create separate
    * sub-archives for platform specific binaries or tests.
    *
    * @param projectRoot path to the project root
    * @param rootFilesToIgnore names of files at the root that should *not* be
    *                          included in the archive
    * @param destination path at which the archive is created
    * @return
    */
  private def createMainArchive(
    projectRoot: Path,
    rootFilesToIgnore: Seq[String],
    destination: Path
  ): TaskProgress[Unit] = {
    def relativePath(file: Path): String = projectRoot.relativize(file).toString
    def shouldBeUploaded(file: Path): Boolean = {
      def isIgnored = rootFilesToIgnore.contains(relativePath(file))
      Files.isRegularFile(file) && !isIgnored
    }

    logger.trace("Gathering files to compress.")
    val filesToCompress = Using(Files.walk(projectRoot)) { filesStream =>
      filesStream.iterator().asScala.filter(shouldBeUploaded).toSeq
    }.get

    logger.info(
      s"Compressing ${filesToCompress.size} project files " +
      s"into [${destination.getFileName}]."
    )

    val compression = TarGzWriter.compress(
      archiveDestination = destination,
      files              = filesToCompress,
      basePath           = projectRoot
    )

    compression.map { _ =>
      logger.info(s"Archive [${destination.getFileName}] created.")
    }
  }

  /** Creates a [[RequestEntity]] that will upload the provided files. */
  private def createRequestEntity(
    files: Seq[Path]
  )(implicit ec: ExecutionContext): Future[RequestEntity] = {

    val fileBodies = files.map { path =>
      val filename = path.getFileName.toString
      Multipart.FormData.BodyPart(
        filename,
        HttpEntity.fromPath(detectContentType(path), path),
        Map("filename" -> filename)
      )
    }

    val formData = Multipart.FormData(Source(fileBodies))
    Marshal(formData).to[RequestEntity]
  }

  /** Loads a manifest, if it exists. */
  private def loadSavedManifest(manifestPath: Path): Option[LibraryManifest] = {
    if (Files.exists(manifestPath)) {
      val loaded = YamlHelper.load[LibraryManifest](manifestPath).get
      Some(loaded)
    } else None
  }

  /** Tries to detect the content type of the file to upload.
    *
    * If it is not a known type, it falls back to `application/octet-stream`.
    */
  private def detectContentType(path: Path): ContentType = {
    val filename = path.getFileName.toString
    if (filename.endsWith(".tgz") || filename.endsWith(".tar.gz"))
      ContentType(MediaTypes.`application/x-gtar`)
    else if (filename.endsWith(".yaml") || filename.endsWith(".enso"))
      ContentTypes.`text/plain(UTF-8)`
    else ContentTypes.`application/octet-stream`
  }

  /** Uploads the provided files to the provided url, using the provided token
    * for authentication.
    */
  private def uploadFiles(
    uri: Uri,
    authToken: auth.Token,
    files: Seq[Path]
  )(implicit ec: ExecutionContext): TaskProgress[Unit] = {
    val future = createRequestEntity(files).map { entity =>
      val request = authToken
        .alterRequest(HTTPRequestBuilder.fromURI(uri))
        .setEntity(entity)
        .POST
      // TODO [RW] upload progress
      HTTPDownload.fetchString(request).force()
    }
    TaskProgress.fromFuture(future).flatMap { response =>
      if (response.statusCode == 200) {
        logger.debug("Server responded with 200 OK.")
        Success(())
      } else {
        // TODO [RW] we may want to have more precise error messages to handle auth errors etc. (#1773)
        val includedMessage = for {
          json    <- io.circe.parser.parse(response.content).toOption
          obj     <- json.asObject
          message <- obj("error").flatMap(_.asString)
        } yield message
        val message = includedMessage.getOrElse("Unknown error")
        val errorMessage =
          s"Upload failed: $message (Status code: ${response.statusCode})."
        logger.error(errorMessage)
        Failure(
          new RuntimeException(
            errorMessage
          )
        )
      }
    }
  }
}
