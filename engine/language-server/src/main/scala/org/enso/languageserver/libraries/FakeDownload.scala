package org.enso.languageserver.libraries

import org.enso.cli.task.{
  ProgressReporter,
  ProgressUnit,
  TaskProgress,
  TaskProgressImplementation
}

import scala.util.Success

/** A temporary helper for mocked parts of the API.
  *
  * It should be removed soon, when the missing parts are implemented.
  */
object FakeDownload {

  /** Creates a [[TaskProgress]] which reports progress updates for a few seconds.
    *
    * Intended for mocking download-like endpoints.
    */
  def make(seconds: Int = 10): TaskProgress[Unit] = {
    val tracker = new TaskProgressImplementation[Unit](ProgressUnit.Bytes)
    val thread = new Thread(() => {
      val n = (seconds * 10).toLong
      for (i <- 0L to n) {
        tracker.reportProgress(i, Some(n))
        Thread.sleep(100)
      }
      tracker.setComplete(Success(()))
    })
    thread.start()
    tracker
  }

  /** Simulates a download operation reporting progress updates to the
    * [[ProgressReporter]].
    */
  def simulateDownload(
    message: String,
    progressReporter: ProgressReporter,
    seconds: Int = 10
  ): Unit = {
    val download = make(seconds = seconds)
    progressReporter.trackProgress(message, download)
    download.force()
  }
}
