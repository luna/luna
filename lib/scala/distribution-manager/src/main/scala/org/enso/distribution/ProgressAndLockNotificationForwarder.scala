package org.enso.distribution

import org.enso.cli.task.{
  ProgressNotification,
  ProgressNotificationForwarder,
  ProgressUnit
}
import org.enso.distribution.locking.{LockUserInterface, Resource}

import java.util.UUID

/** A helper class that provides an implementation of
  * [[ProgressNotificationForwarder]] and [[LockUserInterface]] which are also
  * forwarded as progress notifications with indeterminate progress amounts.
  *
  * All it needs to function is for the user to define the
  * `sendProgressNotification` method.
  */
abstract class ProgressAndLockNotificationForwarder
    extends ProgressNotificationForwarder
    with LockUserInterface {
  private val waitingForResources =
    collection.concurrent.TrieMap[String, UUID]()

  /** @inheritdoc */
  override def startWaitingForResource(resource: Resource): Unit = {
    val uuid = UUID.randomUUID()
    sendProgressNotification(
      ProgressNotification.TaskStarted(
        uuid,
        None,
        ProgressUnit.Unspecified
      )
    )
    sendProgressNotification(
      ProgressNotification.TaskUpdate(
        uuid,
        Some(resource.waitMessage),
        0
      )
    )
    waitingForResources.put(resource.name, uuid)
  }

  /** @inheritdoc */
  override def finishWaitingForResource(resource: Resource): Unit = {
    for (uuid <- waitingForResources.remove(resource.name)) {
      sendProgressNotification(ProgressNotification.TaskSuccess(uuid))
    }
  }
}
