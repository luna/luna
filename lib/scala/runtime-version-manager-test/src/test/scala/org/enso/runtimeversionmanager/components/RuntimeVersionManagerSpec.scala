package org.enso.runtimeversionmanager.components

import java.nio.file.{Files, Path}
import nl.gn0s1s.bump.SemVer
import org.enso.cli.OS
import org.enso.distribution.FileSystem
import org.enso.distribution.FileSystem.PathSyntax
import org.enso.runtimeversionmanager.config.GlobalConfigurationManager
import org.enso.runtimeversionmanager.releases.ReleaseNotFound
import org.enso.runtimeversionmanager.test.{
  RuntimeVersionManagerTest,
  TestRuntimeVersionManagementUserInterface
}
import org.enso.runtimeversionmanager.components
import org.enso.testkit.OsSpec

class RuntimeVersionManagerSpec extends RuntimeVersionManagerTest with OsSpec {

  "RuntimeVersionManager" should {
    "find the latest engine version in semver ordering " +
    "(skipping broken releases)" in {
      val componentsManager = makeRuntimeVersionManager()
      componentsManager.fetchLatestEngineVersion() shouldEqual SemVer(0, 1, 0)
    }

    "install the engine and a matching runtime for it" in {
      val (distributionManager, componentsManager, _) = makeManagers()

      val version = SemVer(0, 0, 1)
      val engine  = componentsManager.findOrInstallEngine(SemVer(0, 0, 1))

      engine.version shouldEqual version
      assert(
        engine.path.startsWith(distributionManager.paths.engines),
        "Engine should be installed in the engines directory."
      )

      val runtime = componentsManager.findGraalRuntime(engine)
      runtime.value.version shouldEqual GraalVMVersion("2.0.0", "11")
      assert(
        runtime.value.path.startsWith(distributionManager.paths.runtimes),
        "Engine should be installed in the engines directory."
      )
    }

    "list installed engines and runtimes" in {
      val componentsManager = makeRuntimeVersionManager()
      val engineVersions =
        Set(SemVer(0, 0, 0), SemVer(0, 0, 1), SemVer(0, 0, 1, Some("pre")))
      val runtimeVersions =
        Set(
          components.GraalVMVersion("1.foo", "11"),
          components.GraalVMVersion("2.0.0", "11")
        )
      engineVersions.map(componentsManager.findOrInstallEngine)

      componentsManager
        .listInstalledEngines()
        .map(_.version)
        .toSet shouldEqual engineVersions
      componentsManager
        .listInstalledGraalRuntimes()
        .map(_.version)
        .toSet shouldEqual runtimeVersions

      val runtime2 =
        componentsManager
          .findGraalRuntime(components.GraalVMVersion("2.0.0", "11"))
          .value
      componentsManager.findEnginesUsingRuntime(runtime2) should have length 2
    }

    "preserve the broken mark when installing a broken release" in {
      val componentsManager = makeManagers(userInterface =
        new TestRuntimeVersionManagementUserInterface(installBroken = true)
      )._2
      val brokenVersion = SemVer(0, 999, 0, Some("broken"))
      componentsManager.findOrInstallEngine(brokenVersion)

      assert(
        componentsManager.findEngine(brokenVersion).value.isMarkedBroken,
        "The broken release should still be marked as broken after being " +
        "installed and loaded."
      )
    }

    "skip broken releases when finding latest installed version" in {
      val (distributionManager, componentsManager, _) =
        makeManagers(userInterface =
          new TestRuntimeVersionManagementUserInterface(installBroken = true)
        )
      val configurationManager =
        new GlobalConfigurationManager(componentsManager, distributionManager)

      val validVersion          = SemVer(0, 0, 1)
      val newerButBrokenVersion = SemVer(0, 999, 0, Some("broken"))
      componentsManager.findOrInstallEngine(validVersion)
      componentsManager.findOrInstallEngine(newerButBrokenVersion)

      configurationManager.defaultVersion shouldEqual validVersion
    }

    "issue a warning when a broken release is requested" in {
      val userInterface =
        new TestRuntimeVersionManagementUserInterface(installBroken = true)
      val componentsManager = makeManagers(userInterface = userInterface)._2

      val brokenVersion = SemVer(0, 999, 0, Some("broken"))
      componentsManager.findOrInstallEngine(brokenVersion)
      assert(
        userInterface.wasAskedToInstallBroken,
        "User interface should have been queried if broken versions are allowed."
      )
      componentsManager.findEngine(brokenVersion).value
    }

    "issue a context-specific error when a nightly release cannot be found" in {
      val componentsManager = makeManagers()._2
      val nightlyVersion    = SemVer(0, 0, 0, Some("SNAPSHOT.2000-01-01"))
      val exception = intercept[ReleaseNotFound] {
        componentsManager.findOrInstallEngine(nightlyVersion)
      }
      exception.getMessage should include("Nightly releases expire")
    }

    "uninstall the runtime iff it is not used by any engines" in {
      val componentsManager = makeRuntimeVersionManager()
      val engineVersions =
        Seq(SemVer(0, 0, 0), SemVer(0, 0, 1), SemVer(0, 0, 1, Some("pre")))
      engineVersions.map(componentsManager.findOrInstallEngine)

      componentsManager.listInstalledEngines() should have length 3
      componentsManager.listInstalledGraalRuntimes() should have length 2

      // remove the engine that shares the runtime with another one
      val version1 = SemVer(0, 0, 1, Some("pre"))
      componentsManager.uninstallEngine(version1)
      val engines1 = componentsManager.listInstalledEngines()
      engines1 should have length 2
      engines1.map(_.version) should not contain version1
      componentsManager.listInstalledGraalRuntimes() should have length 2

      // remove the second engine that shared the runtime
      val version2 = SemVer(0, 0, 1)
      componentsManager.uninstallEngine(version2)
      val engines2 = componentsManager.listInstalledEngines()
      engines2 should have length 1
      engines2.map(_.version) should not contain version2
      val runtimes2 = componentsManager.listInstalledGraalRuntimes()
      runtimes2 should have length 1
      runtimes2.map(_.version).head shouldEqual components.GraalVMVersion(
        "1.foo",
        "11"
      )

      // remove the last engine
      componentsManager.uninstallEngine(SemVer(0, 0, 0))
      componentsManager.listInstalledEngines() should have length 0
      componentsManager.listInstalledGraalRuntimes() should have length 0
    }

    "correctly handle version depending on installer type" in {
      val projectManager =
        makeManagers(installerKind = InstallerKind.ProjectManager)._2
      val launcher =
        makeManagers(installerKind = InstallerKind.Launcher)._2

      val engineWithDifferentVersionRequirements = SemVer(0, 1, 0)

      val manifest =
        launcher
          .findOrInstallEngine(engineWithDifferentVersionRequirements)
          .manifest

      val usualVersion = SemVer(0, 0, 1)
      val bigVersion   = SemVer(999, 0, 0)
      manifest.requiredInstallerVersions.launcher shouldEqual usualVersion
      manifest.requiredInstallerVersions.projectManager shouldEqual bigVersion

      manifest.minimumRequiredVersion(installerKind =
        InstallerKind.Launcher
      ) shouldEqual usualVersion
      manifest.minimumRequiredVersion(installerKind =
        InstallerKind.ProjectManager
      ) shouldEqual bigVersion

      val upgradeException = intercept[UpgradeRequiredError] {
        projectManager.findOrInstallEngine(
          engineWithDifferentVersionRequirements
        )
      }
      upgradeException.expectedVersion shouldEqual bigVersion
    }

    "support bundled components" in {
      val engineVersion  = SemVer(0, 1, 0)
      val runtimeVersion = GraalVMVersion("1.foo", "11")
      prepareBundle(
        engines  = Seq(engineVersion),
        runtimes = Seq(runtimeVersion)
      )
      val manager = makeRuntimeVersionManager()

      val engine = manager.findEngine(engineVersion).value
      engine.version shouldEqual engineVersion
      engine.isMarkedBroken shouldEqual false
      engine.ensureValid()

      manager.findGraalRuntime(engine).value.version shouldEqual runtimeVersion
      manager.findGraalRuntime(runtimeVersion).value.ensureValid()
    }

    "fail to uninstall a read-only bundled component" taggedAs OsUnix in {
      val engineVersion  = SemVer(0, 1, 0)
      val runtimeVersion = GraalVMVersion("1.foo", "11")
      prepareBundle(
        engines  = Seq(engineVersion),
        runtimes = Seq(runtimeVersion)
      )
      val manager          = makeRuntimeVersionManager()
      def installedEngines = manager.listInstalledEngines().map(_.version)
      def installedRuntimes =
        manager.listInstalledGraalRuntimes().map(_.version)

      val enginePath = getTestDirectory / "dist" / "0.1.0"
      val runtimePath =
        getTestDirectory / "runtime" / "graalvm-ce-java11-1.foo"

      enginePath.toFile.setWritable(false)
      try {
        intercept[UninstallationError] {
          manager.uninstallEngine(engineVersion)
        }

        installedEngines shouldEqual Seq(engineVersion)
        installedRuntimes shouldEqual Seq(runtimeVersion)
      } finally {
        enginePath.toFile.setWritable(true)
      }

      runtimePath.toFile.setWritable(false)
      try {
        manager.uninstallEngine(engineVersion)

        installedEngines shouldEqual Seq()
        installedRuntimes shouldEqual Seq(runtimeVersion)

        manager.cleanupRuntimes()
        installedRuntimes shouldEqual Seq(runtimeVersion)
      } finally {
        runtimePath.toFile.setWritable(true)
      }

      manager.cleanupRuntimes()
      installedRuntimes shouldEqual Seq()
    }

    "include both bundled and installed components in list" in {
      prepareBundle(
        engines  = Seq(SemVer(0, 0, 1)),
        runtimes = Seq(GraalVMVersion("1.foo", "11"))
      )
      val manager = makeRuntimeVersionManager()
      manager.findOrInstallEngine(SemVer(0, 1, 0))

      manager
        .listInstalledEngines()
        .map(_.version) should contain theSameElementsAs Seq(
        SemVer(0, 0, 1),
        SemVer(0, 1, 0)
      )

      val runtimeVersions = manager.listInstalledGraalRuntimes().map(_.version)
      runtimeVersions.map(_.graalVersion) should contain theSameElementsAs Seq(
        "1.foo",
        "2.0.0"
      )
      runtimeVersions.map(_.java).toSet shouldEqual Set("11")
    }
  }

  private def prepareBundle(
    engines: Seq[SemVer],
    runtimes: Seq[GraalVMVersion]
  ): Unit = {
    FileSystem.writeTextFile(
      getTestDirectory / ".enso.bundle",
      "Enso Bundle Marker"
    )
    for (engineVersion <- engines) {
      fakeInstallEngine(getTestDirectory / "dist", engineVersion)
    }
    for (runtimeVersion <- runtimes) {
      fakeInstallRuntime(getTestDirectory / "runtime", runtimeVersion)
    }
  }

  private def fakeInstallEngine(searchPath: Path, version: SemVer): Unit = {
    val manifest = """minimum-launcher-version: 0.0.1
                     |minimum-project-manager-version: 0.0.1
                     |graal-vm-version: 1.foo
                     |graal-java-version: 11""".stripMargin
    val root     = searchPath / version.toString
    Files.createDirectories(root)
    FileSystem.writeTextFile(root / "manifest.yaml", manifest)
    val components = root / "component"
    Files.createDirectories(components)
    makePlaceholder(components / "runner.jar")
    FileSystem.writeTextFile(components / "runtime.jar", "placeholder")
  }

  private def fakeInstallRuntime(
    searchPath: Path,
    version: GraalVMVersion
  ): Unit = {
    val root =
      searchPath / s"graalvm-ce-java${version.java}-${version.graalVersion}"
    val bin =
      if (OS.operatingSystem == OS.MacOS) root / "Contents" / "Home" / "bin"
      else root / "bin"
    Files.createDirectories(bin)
    val executable = if (OS.isWindows) "java.exe" else "java"
    makePlaceholder(bin / executable)
  }

  private def makePlaceholder(path: Path): Unit = {
    FileSystem.writeTextFile(path, "placeholder")
    path.toFile.setExecutable(true)
  }
}
