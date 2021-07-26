package org.enso.projectmanager

import org.enso.distribution.{
  DistributionManager,
  EditionManager,
  TemporaryDirectoryManager
}
import org.enso.distribution.locking.ResourceManager

import java.nio.file.Path
import org.enso.projectmanager.versionmanagement.DistributionConfiguration
import org.enso.runtimeversionmanager.components.{
  GraalVMComponentConfiguration,
  InstallerKind,
  RuntimeVersionManagementUserInterface,
  RuntimeVersionManager
}
import org.enso.runtimeversionmanager.releases.engine.{
  EngineRelease,
  EngineReleaseProvider
}
import org.enso.runtimeversionmanager.releases.graalvm.{
  GraalCEReleaseProvider,
  GraalVMRuntimeReleaseProvider
}
import org.enso.runtimeversionmanager.releases.{
  Release,
  ReleaseProvider,
  SimpleReleaseProvider
}
import org.enso.runtimeversionmanager.runner.{JVMSettings, JavaCommand}
import org.enso.runtimeversionmanager.test.{
  FakeEnvironment,
  NoopComponentUpdaterFactory,
  TestLocalLockManager
}
import org.enso.testkit.HasTestDirectory

import scala.jdk.OptionConverters.RichOptional
import scala.util.{Failure, Success, Try}

/** A distribution configuration for use in tests.
  *
  * @param distributionRoot root of the test distribution, should be located
  *                         within some temporary directory
  * @param engineReleaseProvider provider of (fake) engine releases
  * @param runtimeReleaseProvider provider of (fake) Graal releases
  * @param discardChildOutput specifies if input of launched runner processes
  *                           should be ignored
  */
class TestDistributionConfiguration(
  distributionRoot: Path,
  override val engineReleaseProvider: ReleaseProvider[EngineRelease],
  runtimeReleaseProvider: GraalVMRuntimeReleaseProvider,
  discardChildOutput: Boolean
) extends DistributionConfiguration
    with FakeEnvironment
    with HasTestDirectory {

  def getTestDirectory: Path = distributionRoot

  lazy val environment = fakeInstalledEnvironment()

  lazy val distributionManager = new DistributionManager(environment)

  lazy val lockManager = new TestLocalLockManager

  lazy val resourceManager = new ResourceManager(lockManager)

  lazy val editionManager: EditionManager = EditionManager(distributionManager)

  lazy val temporaryDirectoryManager =
    TemporaryDirectoryManager(distributionManager, resourceManager)

  lazy val componentConfig = new GraalVMComponentConfiguration

  lazy val componentUpdaterFactory = NoopComponentUpdaterFactory

  override def makeRuntimeVersionManager(
    userInterface: RuntimeVersionManagementUserInterface
  ): RuntimeVersionManager = new RuntimeVersionManager(
    userInterface             = userInterface,
    distributionManager       = distributionManager,
    temporaryDirectoryManager = temporaryDirectoryManager,
    resourceManager           = resourceManager,
    engineReleaseProvider     = engineReleaseProvider,
    runtimeReleaseProvider    = runtimeReleaseProvider,
    componentConfig           = componentConfig,
    componentUpdaterFactory   = componentUpdaterFactory,
    installerKind             = InstallerKind.ProjectManager
  )

  /** JVM settings that will force to use the same JVM that we are running.
    *
    * This is done to avoiding downloading GraalVM in tests (that would be far
    * too slow) and to ensure that a GraalVM instance is selected, regardless of
    * the default JVM set in the current environment.
    */
  override def defaultJVMSettings: JVMSettings = {
    val currentProcess =
      ProcessHandle.current().info().command().toScala.getOrElse("java")
    val javaCommand = JavaCommand(currentProcess, None)
    new JVMSettings(
      javaCommandOverride = Some(javaCommand),
      jvmOptions          = Seq()
    )
  }

  override def shouldDiscardChildOutput: Boolean = discardChildOutput
}

object TestDistributionConfiguration {

  /** Creates a [[TestDistributionConfiguration]] with repositories that do not
    * have any available releases.
    */
  def withoutReleases(
    distributionRoot: Path,
    discardChildOutput: Boolean
  ): TestDistributionConfiguration = {
    val noReleaseProvider = new NoReleaseProvider
    new TestDistributionConfiguration(
      distributionRoot       = distributionRoot,
      engineReleaseProvider  = new EngineReleaseProvider(noReleaseProvider),
      runtimeReleaseProvider = new GraalCEReleaseProvider(noReleaseProvider),
      discardChildOutput
    )
  }

  /** Creates a [[TestDistributionConfiguration]] instance. */
  def apply(
    distributionRoot: Path,
    engineReleaseProvider: ReleaseProvider[EngineRelease],
    runtimeReleaseProvider: GraalVMRuntimeReleaseProvider,
    discardChildOutput: Boolean
  ): TestDistributionConfiguration =
    new TestDistributionConfiguration(
      distributionRoot,
      engineReleaseProvider,
      runtimeReleaseProvider,
      discardChildOutput
    )

  /** A [[SimpleReleaseProvider]] that has no releases. */
  private class NoReleaseProvider extends SimpleReleaseProvider {
    override def releaseForTag(tag: String): Try[Release] = Failure(
      new IllegalStateException(
        "This provider does not support fetching releases."
      )
    )

    override def listReleases(): Try[Seq[Release]] = Success(Seq())
  }
}
