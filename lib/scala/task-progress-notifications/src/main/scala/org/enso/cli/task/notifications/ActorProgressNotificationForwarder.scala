package org.enso.cli.task.notifications

import akka.actor.ActorRef
import org.enso.cli.task.{
  ProgressNotification,
  ProgressNotificationForwarder,
  ProgressReporter
}
import org.enso.cli.task.ProgressNotification.{
  TaskFailure,
  TaskStarted,
  TaskSuccess,
  TaskUpdate
}
import org.enso.jsonrpc.Notification

object ActorProgressNotificationForwarder {
  def translateAndForward(
    relatedOperationName: String,
    recipient: ActorRef
  ): ProgressReporter =
    new ProgressNotificationForwarder {
      override def sendProgressNotification(
        notification: ProgressNotification
      ): Unit = {
        val translated: Notification[_, _] =
          translateProgressNotification(relatedOperationName, notification)
        recipient ! translated
      }
    }

  /** Translates a [[ProgressNotification]] into a protocol message. */
  def translateProgressNotification(
    relatedOperationName: String,
    progressNotification: ProgressNotification
  ): Notification[_, _] = progressNotification match {
    case TaskStarted(taskId, total, unit) =>
      Notification(
        TaskNotificationApi.TaskStarted,
        TaskNotificationApi.TaskStarted.Params(
          taskId           = taskId,
          relatedOperation = relatedOperationName,
          unit             = unit,
          total            = total
        )
      )
    case TaskUpdate(taskId, message, done) =>
      Notification(
        TaskNotificationApi.TaskProgressUpdate,
        TaskNotificationApi.TaskProgressUpdate.Params(taskId, message, done)
      )
    case TaskSuccess(taskId) =>
      Notification(
        TaskNotificationApi.TaskFinished,
        TaskNotificationApi.TaskFinished.Params(taskId, None, success = true)
      )
    case TaskFailure(taskId, throwable) =>
      Notification(
        TaskNotificationApi.TaskFinished,
        TaskNotificationApi.TaskFinished
          .Params(taskId, Option(throwable.getMessage), success = false)
      )
  }
}
