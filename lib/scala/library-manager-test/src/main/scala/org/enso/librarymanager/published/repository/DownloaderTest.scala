package org.enso.librarymanager.published.repository

import org.enso.cli.task.{ProgressReporter, TaskProgress}
import org.enso.distribution.TemporaryDirectoryManager
import org.enso.distribution.locking.{
  LockUserInterface,
  Resource,
  ResourceManager,
  ThreadSafeFileLockManager
}
import org.enso.librarymanager.published.cache.DownloadingLibraryCache
import org.enso.testkit.HasTestDirectory

trait DownloaderTest { self: HasTestDirectory =>
  def withDownloader[R](action: DownloadingLibraryCache => R): R = {
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

      action(cache)
    } finally {
      resourceManager.releaseMainLock()
      resourceManager.unlockTemporaryDirectory()
    }
  }

}
