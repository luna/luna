package org.enso.launcher.cli

import com.typesafe.scalalogging.Logger
import nl.gn0s1s.bump.SemVer
import org.enso.cli.CLIOutput
import org.enso.distribution.locking.Resource
import org.enso.launcher.InfoLogger
import org.enso.runtimeversionmanager.components.{
  GraalVMVersion,
  RuntimeVersionManagementUserInterface
}

/** [[RuntimeVersionManagementUserInterface]] that reports information and progress
  * to the command line.
  *
  * It may ask interactive questions to the user, depending on [[cliOptions]].
  */
class CLIRuntimeVersionManagementUserInterface(
  cliOptions: GlobalCLIOptions,
  alwaysInstallMissing: Boolean
) extends CLIProgressReporter(cliOptions)
    with RuntimeVersionManagementUserInterface {

  private val logger = Logger[CLIRuntimeVersionManagementUserInterface]

  /** @inheritdoc */
  override def shouldInstallBrokenEngine(version: SemVer): Boolean =
    if (cliOptions.autoConfirm) {
      logger.warn(
        s"The engine release $version is marked as broken and it should " +
        s"not be used. Since `auto-confirm` is set, the installation will " +
        s"continue, but you may want to reconsider changing versions to a " +
        s"stable release."
      )
      true
    } else {
      logger.warn(
        s"The engine release $version is marked as broken and it should " +
        s"not be used."
      )
      CLIOutput.askConfirmation(
        "Are you sure you still want to continue installing this version " +
        "despite the warning?"
      )
    }

  /** @inheritdoc */
  override def shouldInstallMissingEngine(version: SemVer): Boolean = {
    def complainAndAsk(): Boolean = {
      logger.warn(s"Engine $version is missing.")
      cliOptions.autoConfirm || CLIOutput.askConfirmation(
        "Do you want to install the missing engine?",
        yesDefault = true
      )
    }

    alwaysInstallMissing || complainAndAsk()
  }

  /** @inheritdoc */
  override def shouldInstallMissingRuntime(version: GraalVMVersion): Boolean = {
    logger.warn(s"Required runtime $version is missing.")
    cliOptions.autoConfirm || CLIOutput.askConfirmation(
      "Do you want to install the missing runtime?",
      yesDefault = true
    )
  }

  /** @inheritdoc */
  override def logInfo(message: => String): Unit = InfoLogger.info(message)

  /** @inheritdoc */
  override def startWaitingForResource(resource: Resource): Unit =
    logger.warn(resource.waitMessage)

  /** @inheritdoc */
  override def finishWaitingForResource(resource: Resource): Unit = ()
}
