package org.enso.distribution.locking

import com.typesafe.scalalogging.Logger

import scala.util.Using

/** Uses a [[LockManager]] implementation to synchronize access to [[Resource]].
  */
class ResourceManager(lockManager: LockManager) {

  private val logger = Logger[ResourceManager]

  /** Runs the `action` while holding a lock (of `lockType`) for the `resource`.
    *
    * If the lock cannot be acquired immediately, the `waitingInterface` is used
    * to notify the user.
    */
  def withResource[R](
    waitingInterface: LockUserInterface,
    resource: Resource,
    lockType: LockType
  )(action: => R): R = {
    Using(acquireResource(waitingInterface, resource, lockType)) { _ =>
      action
    }.get
  }

  /** Runs the `action` while holding multiple locks for a sequence of
    * resources.
    */
  def withResources[R](
    waitingInterface: LockUserInterface,
    resources: (Resource, LockType)*
  )(action: => R): R =
    resources match {
      case Seq((head, exclusive), tail @ _*) =>
        withResource(waitingInterface, head, exclusive) {
          withResources(waitingInterface, tail: _*)(action)
        }
      case Seq() =>
        action
    }

  /** Acquires a resource, handling possible waiting, and returns its [[Lock]]
    * instance that can be used to unlock it.
    */
  private def acquireResource(
    waitingInterface: LockUserInterface,
    resource: Resource,
    lockType: LockType
  ): Lock = {
    var waited = false
    val lock = lockManager.acquireLockWithWaitingAction(
      resource.name,
      lockType = lockType,
      () => {
        waited = true
        waitingInterface.startWaitingForResource(resource)
      }
    )
    if (waited) waitingInterface.finishWaitingForResource(resource)
    lock
  }

  var mainLock: Option[Lock] = None

  /** Initializes the [[MainLock]]. */
  def initializeMainLock(): Unit = {
    val lock =
      lockManager
        .tryAcquireLock(MainLock.name, LockType.Shared)
        .getOrElse {
          throw DistributionIsModifiedError(MainLock.waitMessage)
        }
    mainLock = Some(lock)
  }

  /** Exception that is thrown when the main lock is held exclusively.
    *
    * This situation means that the current distribution is being installed or
    * uninstalled, so it should not be used in the meantime and the application
    * has to terminate immediately.
    */
  case class DistributionIsModifiedError(message: String)
      extends RuntimeException(message)

  /** Acquires an exclusive main lock (first releasing the shared lock),
    * ensuring that no other processes using this distribution can be running in
    * parallel.
    *
    * @param waitAction function that is executed if the lock cannot be acquired
    *                   immediately
    */
  def acquireExclusiveMainLock(waitAction: () => Unit): Unit = {
    mainLock match {
      case Some(oldLock) =>
        oldLock.release()
        mainLock = None
      case None =>
    }

    val lock = lockManager.acquireLockWithWaitingAction(
      MainLock.name,
      LockType.Exclusive,
      waitAction
    )
    mainLock = Some(lock)
  }

  /** Releases the main lock.
    *
    * Should be called just before the program terminates. It is not an error to
    * skip it, as the operating system should unlock all resources after the
    * program terminates, but on some platforms this automatic 'garbage
    * collection for locks' may take some time, so it is better to release it
    * manually.
    */
  def releaseMainLock(): Unit =
    mainLock match {
      case Some(lock) =>
        lock.release()
        mainLock = None
      case None =>
    }

  /** Runs the provided `action` if an exclusive lock can be immediately
    * acquired for the temporary directory, i.e. the directory is not used by
    * anyone.
    *
    * If the lock cannot be acquired immediately, the action is not executed and
    * None is returned.
    */
  def tryWithExclusiveTemporaryDirectory[R](action: => R): Option[R] = {
    lockManager.tryAcquireLock(
      TemporaryDirectory.name,
      LockType.Exclusive
    ) match {
      case Some(lock) =>
        try Some(action)
        finally lock.release()
      case None => None
    }
  }

  private var temporaryDirectoryLock: Option[Lock] = None

  /** Marks the temporary directory as in use.
    *
    * This lock does not have to be released as it will be automatically
    * released when the program terminates.
    */
  def startUsingTemporaryDirectory(): Unit = {
    if (temporaryDirectoryLock.isDefined) {
      logger.trace("The temporary directory was already in-use.")
      return
    }

    val lock = lockManager.acquireLockWithWaitingAction(
      TemporaryDirectory.name,
      LockType.Shared,
      () => logger.warn(TemporaryDirectory.waitMessage)
    )
    temporaryDirectoryLock = Some(lock)
  }

  /** Releases the lock for the temporary directory.
    *
    * Used by the uninstaller as part of releasing all locks to ensure that the
    * locks directory can be removed.
    */
  def unlockTemporaryDirectory(): Unit =
    temporaryDirectoryLock match {
      case Some(lock) =>
        lock.release()
        temporaryDirectoryLock = None
      case None =>
    }

  /** This resource is acquired whenever the temporary directory is first
    * accessed.
    *
    * It ensures that installations running in parallel will not remove each
    * other's files. The temporary directory is only cleaned when no other
    * processes use it.
    */
  private case object TemporaryDirectory extends Resource {
    override def name: String = "temporary-files"
    override def waitMessage: String =
      "Another process is cleaning temporary files, " +
      "the installation has to wait until that is complete to avoid " +
      "conflicts. It should not take a long time."
  }

  /** The main lock that is held by all launcher processes.
    *
    * It is used to ensure that no other processes are running when the
    * distribution is being installed or uninstalled.
    */
  private case object MainLock extends Resource {
    override def name: String = "launcher-main"
    override def waitMessage: String =
      "Another process is installing or uninstalling the current " +
      "distribution. Please wait until that finishes."
  }
}
