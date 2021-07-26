package org.enso.runtimeversionmanager.distributuion

import org.enso.distribution.{
  DistributionManager,
  Environment,
  FileSystem,
  PortableDistributionManager
}

import java.nio.file.{Files, Path}
import org.enso.distribution.FileSystem.PathSyntax
import org.enso.runtimeversionmanager.test.FakeEnvironment
import org.enso.testkit.WithTemporaryDirectory
import org.scalatest.OptionValues
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

class DistributionManagerSpec
    extends AnyWordSpec
    with Matchers
    with WithTemporaryDirectory
    with FakeEnvironment
    with OptionValues {

  "DistributionManager" should {
    "detect portable distribution" in {
      val executable = fakeExecutablePath(portable = true)
      val fakeEnvironment = new Environment {
        override def getPathToRunningExecutable: Path = executable
      }

      val distributionManager = new PortableDistributionManager(fakeEnvironment)
      distributionManager.isRunningPortable shouldEqual true
      distributionManager.paths.dataRoot shouldEqual getTestDirectory
      distributionManager.paths.config shouldEqual getTestDirectory / "config"
      distributionManager.paths.runtimes shouldEqual
      getTestDirectory / "runtime"
      distributionManager.paths.engines shouldEqual getTestDirectory / "dist"
      distributionManager.paths.bundle shouldEqual None
    }

    "detect installed distribution" in {
      val executable = fakeExecutablePath()
      val fakeEnvironment = new Environment {
        override def getPathToRunningExecutable: Path = executable
      }

      val distributionManager = new PortableDistributionManager(fakeEnvironment)
      distributionManager.isRunningPortable shouldEqual false
      distributionManager.paths.bundle shouldEqual None
    }

    "detect bundles" in {
      val executable = fakeExecutablePath()
      FileSystem.writeTextFile(getTestDirectory / ".enso.bundle", "placeholder")

      val fakeEnvironment = new Environment {
        override def getPathToRunningExecutable: Path = executable
      }

      val distributionManager = new PortableDistributionManager(fakeEnvironment)
      val bundle              = distributionManager.paths.bundle.value
      assert(Files.isSameFile(bundle.engines, getTestDirectory / "dist"))
      assert(Files.isSameFile(bundle.runtimes, getTestDirectory / "runtime"))
    }

    "respect environment variable overrides " +
    "for installed distribution location" in {
      val dataDir   = getTestDirectory / "test_data"
      val configDir = getTestDirectory / "test_config"
      val binDir    = getTestDirectory / "test_bin"

      val distributionManager =
        new DistributionManager(fakeInstalledEnvironment())
      distributionManager.paths.dataRoot shouldEqual dataDir
      distributionManager.paths.config shouldEqual configDir
      distributionManager.LocallyInstalledDirectories.binDirectory shouldEqual
      binDir
    }
  }
}
