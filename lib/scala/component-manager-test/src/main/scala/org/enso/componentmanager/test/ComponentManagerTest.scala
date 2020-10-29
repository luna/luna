package org.enso.componentmanager.test

import java.nio.file.Path

import nl.gn0s1s.bump.SemVer
import org.enso.componentmanager._
import org.enso.componentmanager.components.{
  ComponentManagementUserInterface,
  ComponentManager
}
import org.enso.componentmanager.distribution.{
  DistributionManager,
  TemporaryDirectoryManager
}
import org.enso.componentmanager.releases.engine.EngineReleaseProvider
import org.enso.componentmanager.releases.runtime.GraalCEReleaseProvider
import org.enso.componentmanager.releases.testing.FakeReleaseProvider
import org.enso.pkg.{PackageManager, SemVerEnsoVersion}
import org.scalatest.OptionValues
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/** Gathers helper methods for testing the [[ComponentManager]]. */
class ComponentManagerTest
    extends AnyWordSpec
    with Matchers
    with OptionValues
    with WithTemporaryDirectory
    with FakeEnvironment
    with DropLogs {

  /** Creates the [[DistributionManager]], [[ComponentManager]] and an
    * [[Environment]] for use in the tests.
    *
    * Should be called separately for each test case, as the components use
    * temporary directories which are separate for each test case.
    *
    * Additional environment variables may be provided that are added to the
    * [[Environment]] for the created managers.
    */
  def makeManagers(
    environmentOverrides: Map[String, String] = Map.empty,
    userInterface: ComponentManagementUserInterface =
      TestComponentManagementUserInterface.default
  ): (DistributionManager, ComponentManager, Environment) = {
    val env                 = fakeInstalledEnvironment(environmentOverrides)
    val distributionManager = new DistributionManager(env)
    val fakeReleasesRoot    = FakeReleases.path
    val engineProvider = new EngineReleaseProvider(
      FakeReleaseProvider(
        fakeReleasesRoot.resolve("enso"),
        copyIntoArchiveRoot = Seq("manifest.yaml")
      )
    )
    val runtimeProvider = new GraalCEReleaseProvider(
      FakeReleaseProvider(fakeReleasesRoot.resolve("graalvm"))
    )

    val resourceManager = TestLocalResourceManager.create()
    val temporaryDirectoryManager =
      new TemporaryDirectoryManager(distributionManager, resourceManager)

    val componentsManager = new ComponentManager(
      userInterface,
      distributionManager,
      temporaryDirectoryManager,
      resourceManager,
      engineProvider,
      runtimeProvider
    )

    (distributionManager, componentsManager, env)
  }

  /** Returns just the [[ComponentManager]].
    *
    * See [[makeManagers]] for details.
    */
  def makeComponentsManager(): ComponentManager = makeManagers()._2

  /** Creates a new project using the default package manager.
    */
  def newProject(name: String, path: Path, version: SemVer): Unit = {
    PackageManager.Default.create(
      root        = path.toFile,
      name        = name,
      ensoVersion = SemVerEnsoVersion(version)
    )
  }
}
