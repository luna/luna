package org.enso.projectmanager.service.versionmanagement

import akka.actor.ActorRef
import com.typesafe.scalalogging.Logger
import nl.gn0s1s.bump.SemVer
import org.enso.cli.task.ProgressNotification
import org.enso.distribution.ProgressAndLockNotificationForwarder
import org.enso.runtimeversionmanager.components.{
  GraalVMVersion,
  RuntimeVersionManagementUserInterface
}

/** A [[RuntimeVersionManagementUserInterface]] that sends
  * [[ProgressNotification]] to the specified actors (both for usual tasks and
  * indeterminate progress when waiting on locks).
  *
  * @param progressTracker        the actor to send progress updates to
  * @param allowMissingComponents specifies if missing components should be
  *                               automatically installed
  * @param allowBrokenComponents  specifies if broken components can be installed
  */
class ControllerInterface(
  progressTracker: ActorRef,
  allowMissingComponents: Boolean,
  allowBrokenComponents: Boolean
) extends ProgressAndLockNotificationForwarder
    with RuntimeVersionManagementUserInterface {

  /** @inheritdoc */
  override def shouldInstallMissingEngine(version: SemVer): Boolean =
    allowMissingComponents

  /** @inheritdoc */
  override def shouldInstallMissingRuntime(version: GraalVMVersion): Boolean =
    allowMissingComponents

  /** @inheritdoc */
  override def shouldInstallBrokenEngine(version: SemVer): Boolean =
    allowBrokenComponents

  /** @inheritdoc */
  override def logInfo(message: => String): Unit =
    Logger[ControllerInterface].info(message)

  /** @inheritdoc */
  override def sendProgressNotification(
    notification: ProgressNotification
  ): Unit =
    progressTracker ! notification
}
