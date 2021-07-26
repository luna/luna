package org.enso.launcher.cli

import org.enso.cli.ProgressBar
import org.enso.cli.task.{ProgressReporter, TaskProgress}
import org.enso.launcher.InfoLogger

/** A [[ProgressReporter]] that displays a progress bar in the console or waits
  * for the task silently, depending on CLI options.
  */
class CLIProgressReporter(hideProgress: Boolean) extends ProgressReporter {

  /** @inheritdoc */
  override def trackProgress(message: String, task: TaskProgress[_]): Unit = {
    InfoLogger.info(message)
    if (!hideProgress) {
      ProgressBar.waitWithProgress(task)
    }
  }
}

object CLIProgressReporter {

  /** A helper method to create [[CLIProgressReporter]] instances. */
  def apply(globalCLIOptions: GlobalCLIOptions): CLIProgressReporter =
    new CLIProgressReporter(globalCLIOptions.hideProgress)
}
