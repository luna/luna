package org.enso.cli.task

import java.util.UUID
import scala.util.{Failure, Success, Try}

/** A [[ProgressReporter]] implementation that tracks tasks and sends
  * [[ProgressNotification]]s using a generic interface.
  */
trait ProgressNotificationForwarder extends ProgressReporter {

  /** The callback that is used to send the progress notification. */
  def sendProgressNotification(notification: ProgressNotification): Unit

  /** @inheritdoc */
  override def trackProgress(message: String, task: TaskProgress[_]): Unit = {
    var uuid: Option[UUID] = None

    /** Initializes the task on first invocation and just returns the
      * generated UUID on further invocations.
      */
    def initializeTask(total: Option[Long]): UUID = uuid match {
      case Some(value) => value
      case None =>
        val generated = UUID.randomUUID()
        uuid = Some(generated)
        sendProgressNotification(
          ProgressNotification.TaskStarted(
            generated,
            total,
            task.unit
          )
        )
        generated
    }

    task.addProgressListener(new ProgressListener[Any] {

      /** @inheritdoc */
      override def progressUpdate(
        done: Long,
        total: Option[Long]
      ): Unit = {
        val uuid = initializeTask(total)
        sendProgressNotification(
          ProgressNotification.TaskUpdate(
            uuid,
            Some(message),
            done
          )
        )
      }

      /** @inheritdoc */
      override def done(result: Try[Any]): Unit = result match {
        case Failure(exception) =>
          val uuid = initializeTask(None)
          sendProgressNotification(
            ProgressNotification.TaskFailure(uuid, exception)
          )
        case Success(_) =>
          val uuid = initializeTask(None)
          sendProgressNotification(ProgressNotification.TaskSuccess(uuid))
      }
    })
  }
}
