package org.enso.interpreter.instrument

import org.enso.cli.task.{
  ProgressUnit,
  ProgressNotification => TaskProgressNotification
}
import org.enso.polyglot.runtime.Runtime.Api.ProgressNotification
import org.enso.polyglot.runtime.Runtime.Api.ProgressNotification._
import org.enso.polyglot.runtime.Runtime.ApiResponse

/** A helper for translating notification formats. */
object ProgressNotificationTranslator {

  /** Translates a notification as defined in the CLI module into the format
    * that is used in the API of the runtime connector, so that it can be
    * forwarded to the Language Server.
    *
    * @param relatedOperationName name of a related operation; these were
    *                             originally tied to Project Manager or Language
    *                             Server operations, but they can also be based
    *                             on internal compiler operations
    * @param progressNotification the notification to translate
    */
  def translate(
    relatedOperationName: String,
    progressNotification: TaskProgressNotification
  ): ApiResponse = {
    val payload = progressNotification match {
      case TaskProgressNotification.TaskStarted(taskId, total, unit) =>
        TaskStarted(
          taskId           = taskId,
          relatedOperation = relatedOperationName,
          unit             = ProgressUnit.toString(unit),
          total            = total
        )
      case TaskProgressNotification.TaskUpdate(taskId, message, done) =>
        TaskProgressUpdate(taskId, message, done)
      case TaskProgressNotification.TaskSuccess(taskId) =>
        TaskFinished(taskId, message = None, success = true)
      case TaskProgressNotification.TaskFailure(taskId, throwable) =>
        TaskFinished(
          taskId,
          message = Option(throwable.getMessage),
          success = false
        )
    }
    ProgressNotification(payload)
  }
}
