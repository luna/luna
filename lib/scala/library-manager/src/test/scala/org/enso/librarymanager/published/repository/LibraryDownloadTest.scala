package org.enso.librarymanager.published.repository

import org.enso.cli.task.{ProgressReporter, TaskProgress}
import org.enso.distribution.TemporaryDirectoryManager
import org.enso.distribution.locking.{
  LockUserInterface,
  Resource,
  ResourceManager,
  ThreadSafeFileLockManager
}
import org.enso.editions.Editions
import org.enso.librarymanager.published.cache.DownloadingLibraryCache
import org.enso.loggingservice.TestLogger.TestLogMessage
import org.enso.loggingservice.{LogLevel, TestLogger}
import org.enso.pkg.PackageManager
import org.enso.testkit.WithTemporaryDirectory
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

import java.nio.file.Files

class LibraryDownloadTest
    extends AnyWordSpec
    with Matchers
    with WithTemporaryDirectory {

  val port: Int = 47306

  "DownloadingLibraryCache" should {
    "be able to download and install libraries from a repository" in {
      val repo = new ExampleRepository

      val repoRoot = getTestDirectory.resolve("repo")
      repo.createRepository(repoRoot)
      val lockManager =
        new ThreadSafeFileLockManager(getTestDirectory.resolve("locks"))
      val resourceManager = new ResourceManager(lockManager)
      try {
        val cache = new DownloadingLibraryCache(
          cacheRoot = getTestDirectory.resolve("cache"),
          temporaryDirectoryManager = new TemporaryDirectoryManager(
            getTestDirectory.resolve("tmp"),
            resourceManager
          ),
          resourceManager = resourceManager,
          lockUserInterface = new LockUserInterface {
            override def startWaitingForResource(resource: Resource): Unit =
              println(s"Waiting for ${resource.name}")

            override def finishWaitingForResource(resource: Resource): Unit =
              println(s"${resource.name} is ready")
          },
          progressReporter = new ProgressReporter {
            override def trackProgress(
              message: String,
              task: TaskProgress[_]
            ): Unit = {}
          }
        )

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
      } finally {
        resourceManager.releaseMainLock()
        resourceManager.unlockTemporaryDirectory()
      }
    }
  }
}
