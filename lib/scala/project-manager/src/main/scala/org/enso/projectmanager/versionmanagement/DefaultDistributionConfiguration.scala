package org.enso.projectmanager.versionmanagement

import com.typesafe.scalalogging.LazyLogging
import org.enso.distribution.{
  DistributionManager,
  EditionManager,
  Environment,
  TemporaryDirectoryManager
}
import org.enso.distribution.locking.{
  ResourceManager,
  ThreadSafeFileLockManager
}
import org.enso.runtimeversionmanager.components.{
  GraalVMComponentConfiguration,
  InstallerKind,
  RuntimeComponentConfiguration,
  RuntimeComponentUpdaterFactory,
  RuntimeVersionManagementUserInterface,
  RuntimeVersionManager
}
import org.enso.runtimeversionmanager.releases.ReleaseProvider
import org.enso.runtimeversionmanager.releases.engine.{
  EngineRelease,
  EngineRepository
}
import org.enso.runtimeversionmanager.releases.graalvm.GraalCEReleaseProvider
import org.enso.runtimeversionmanager.runner.JVMSettings

/** Default distribution configuration to use for the Project Manager in
  * production.
  *
  * The distribution manager and others need to be lazily initialized to ensure
  * that they are initialized at runtime and not at build time if we try
  * building a Native Image.
  */
object DefaultDistributionConfiguration
    extends DistributionConfiguration
    with LazyLogging {

  /** The default [[Environment]] implementation, with no overrides. */
  val environment: Environment = new Environment {}

  /** @inheritdoc */
  lazy val distributionManager = new DistributionManager(environment)

  /** @inheritdoc */
  lazy val lockManager = new ThreadSafeFileLockManager(
    distributionManager.paths.locks
  )

  /** @inheritdoc */
  lazy val resourceManager = new ResourceManager(lockManager)

  /** @inheritdoc */
  lazy val editionManager = EditionManager(distributionManager)

  /** @inheritdoc */
  lazy val temporaryDirectoryManager =
    TemporaryDirectoryManager(distributionManager, resourceManager)

  lazy val componentConfiguration: RuntimeComponentConfiguration =
    new GraalVMComponentConfiguration

  lazy val runtimeComponentUpdaterFactory: RuntimeComponentUpdaterFactory =
    RuntimeComponentUpdaterFactory.Default

  /** @inheritdoc */
  def engineReleaseProvider: ReleaseProvider[EngineRelease] =
    EngineRepository.defaultEngineReleaseProvider

  /** @inheritdoc */
  def makeRuntimeVersionManager(
    userInterface: RuntimeVersionManagementUserInterface
  ): RuntimeVersionManager =
    new RuntimeVersionManager(
      userInterface             = userInterface,
      distributionManager       = distributionManager,
      temporaryDirectoryManager = temporaryDirectoryManager,
      resourceManager           = resourceManager,
      engineReleaseProvider     = engineReleaseProvider,
      runtimeReleaseProvider    = GraalCEReleaseProvider.default,
      componentConfig           = componentConfiguration,
      componentUpdaterFactory   = runtimeComponentUpdaterFactory,
      installerKind             = InstallerKind.ProjectManager
    )

  /** @inheritdoc */
  override def defaultJVMSettings: JVMSettings = JVMSettings.default

  /** @inheritdoc */
  override def shouldDiscardChildOutput: Boolean = false
}
