package org.enso.libraryupload

import nl.gn0s1s.bump.SemVer
import org.enso.cli.task.{ProgressReporter, TaskProgress}
import org.enso.editions.{Editions, LibraryName}
import org.enso.librarymanager.published.repository.{
  DownloaderTest,
  EmptyRepository
}
import org.enso.libraryupload.auth.SimpleHeaderToken
import org.enso.pkg.PackageManager
import org.enso.testkit.WithTemporaryDirectory
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

import java.nio.file.Files

class LibraryUploadTest
    extends AnyWordSpec
    with Matchers
    with WithTemporaryDirectory
    with DownloaderTest {

  def port: Int = 47305

  "LibraryUploader" should {
    "upload the files to the server" in {
      val projectRoot = getTestDirectory.resolve("lib_root")
      val repoRoot    = getTestDirectory.resolve("repo")

      val libraryName    = LibraryName("tester", "Upload_Test")
      val libraryVersion = SemVer(1, 2, 3)
      PackageManager.Default.create(
        projectRoot.toFile,
        name      = libraryName.name,
        namespace = libraryName.namespace,
        version   = libraryVersion.toString
      )

      val server = EmptyRepository.startServer(port, repoRoot, uploads = true)
      try {
        val uploadUrl = s"http://localhost:$port/upload"
        val token     = SimpleHeaderToken("TODO")
        import scala.concurrent.ExecutionContext.Implicits.global
        LibraryUploader
          .uploadLibrary(
            projectRoot,
            uploadUrl,
            token,
            new ProgressReporter {
              override def trackProgress(message: String, task: TaskProgress[_])
                : Unit = ()
            }
          )
          .get

        val libRoot = repoRoot
          .resolve("libraries")
          .resolve("tester")
          .resolve("Upload_Test")
          .resolve("1.2.3")

        PackageManager.Default
          .loadPackage(libRoot.toFile)
          .get
          .name shouldEqual libraryName.name
        assert(Files.exists(libRoot.resolve("manifest.yaml")))
        assert(Files.exists(libRoot.resolve("main.tgz")))

        withDownloader { cache =>
          cache.findCachedLibrary(libraryName, libraryVersion) shouldBe empty

          val repo = Editions.Repository(
            "test_repo",
            s"http://localhost:$port/libraries"
          )
          val installedRoot =
            cache.findOrInstallLibrary(libraryName, libraryVersion, repo).get
          val pkg = PackageManager.Default.loadPackage(installedRoot.toFile).get
          pkg.name shouldEqual libraryName.name
          val sources = pkg.listSources
          sources should have size 1
          sources.head.file.getName shouldEqual "Main.enso"
        }

      } finally {
        server.kill(killDescendants    = true)
        server.join(waitForDescendants = true)
      }
    }
  }
}
