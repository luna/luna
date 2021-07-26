package org.enso.cli.task

import java.util.UUID

/** Internal representation of progress notifications. */
sealed trait ProgressNotification
object ProgressNotification {

  /** Singals that a new task with progress has been started.
    *
    * @param taskId a unique id of the task
    * @param total the total amount of units that the task is expected to take
    * @param unit unit of that the progress is reported in
    */
  case class TaskStarted(
    taskId: UUID,
    total: Option[Long],
    unit: ProgressUnit
  ) extends ProgressNotification

  /** Signals an update to task's progress.
    *
    * @param taskId the task id
    * @param message an optional message to display
    * @param done indication of how much progress has been done since the task
    *             started
    */
  case class TaskUpdate(taskId: UUID, message: Option[String], done: Long)
      extends ProgressNotification

  /** Signals that a task has been finished successfully.
    *
    * @param taskId the task id
    */
  case class TaskSuccess(taskId: UUID) extends ProgressNotification

  /** Signals that a task has failed.
    *
    * @param taskId the task id
    * @param throwable an exception associated with the failure
    */
  case class TaskFailure(taskId: UUID, throwable: Throwable)
      extends ProgressNotification
}
