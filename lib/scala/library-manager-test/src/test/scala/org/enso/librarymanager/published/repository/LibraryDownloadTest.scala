package org.enso.librarymanager.published.repository

import org.enso.editions.Editions
import org.enso.loggingservice.{LogLevel, TestLogger}
import org.enso.loggingservice.TestLogger.TestLogMessage
import org.enso.pkg.PackageManager
import org.enso.testkit.WithTemporaryDirectory
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

import java.nio.file.Files

class LibraryDownloadTest
    extends AnyWordSpec
    with Matchers
    with WithTemporaryDirectory
    with DownloaderTest {

  val port: Int = 47306

  "DownloadingLibraryCache" should {
    "be able to download and install libraries from a repository" in {
      val repo = new ExampleRepository

      val repoRoot = getTestDirectory.resolve("repo")
      repo.createRepository(repoRoot)
      withDownloader { cache =>
        val server = repo.startServer(port, repoRoot)
        try {
          cache.findCachedLibrary(
            repo.testLib.libraryName,
            repo.testLib.version
          ) shouldBe empty

          val (libPath, logs) = TestLogger.gatherLogs {
            cache
              .findOrInstallLibrary(
                repo.testLib.libraryName,
                repo.testLib.version,
                Editions
                  .Repository("test_repo", s"http://localhost:$port/libraries")
              )
              .get
          }
          val pkg = PackageManager.Default.loadPackage(libPath.toFile).get
          pkg.name shouldEqual "Bar"
          val sources = pkg.listSources
          sources should have size 1
          sources.head.file.getName shouldEqual "Main.enso"
          assert(
            Files.notExists(libPath.resolve("LICENSE.md")),
            "The license file should not exist as it was not provided " +
            "in the repository."
          )
          logs should contain(
            TestLogMessage(
              LogLevel.Warning,
              "License file for library [Foo.Bar:1.0.0] was missing."
            )
          )
        } finally {
          server.kill(killDescendants    = true)
          server.join(waitForDescendants = true)
        }
      }
    }
  }
}
