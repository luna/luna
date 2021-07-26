package org.enso.projectmanager

import java.io.File
import java.nio.file.{Files, Path}
import java.time.{OffsetDateTime, ZoneOffset}
import java.util.UUID
import akka.testkit.TestActors.blackholeProps
import akka.testkit._
import io.circe.Json
import io.circe.parser.parse
import nl.gn0s1s.bump.SemVer
import org.apache.commons.io.FileUtils
import org.enso.distribution.FileSystem.PathSyntax
import org.enso.distribution.FileSystem
import org.enso.editions.Editions
import org.enso.cli.OS
import org.enso.jsonrpc.test.JsonRpcServerTestKit
import org.enso.jsonrpc.{ClientControllerFactory, Protocol}
import org.enso.loggingservice.printers.StderrPrinterWithColors
import org.enso.loggingservice.{LogLevel, LoggerMode, LoggingServiceManager}
import org.enso.pkg.{Config, PackageManager}
import org.enso.projectmanager.boot.Globals.{ConfigFilename, ConfigNamespace}
import org.enso.projectmanager.boot.configuration._
import org.enso.projectmanager.control.effect.ZioEnvExec
import org.enso.projectmanager.data.MissingComponentAction
import org.enso.projectmanager.infrastructure.file.BlockingFileSystem
import org.enso.projectmanager.infrastructure.languageserver.{
  ExecutorWithUnlimitedPool,
  LanguageServerGatewayImpl,
  LanguageServerRegistry,
  ShutdownHookActivator
}
import org.enso.projectmanager.infrastructure.log.Slf4jLogging
import org.enso.projectmanager.infrastructure.repository.ProjectFileRepository
import org.enso.projectmanager.protocol.{
  JsonRpc,
  ManagerClientControllerFactory
}
import org.enso.projectmanager.service.config.GlobalConfigService
import org.enso.projectmanager.service.versionmanagement.{
  RuntimeVersionManagementService,
  RuntimeVersionManagerFactory
}
import org.enso.projectmanager.service.{
  MonadicProjectValidator,
  ProjectCreationService,
  ProjectService
}
import org.enso.projectmanager.test.{ObservableGenerator, ProgrammableClock}
import org.enso.runtimeversionmanager.components.GraalVMVersion
import org.enso.runtimeversionmanager.test.FakeReleases
import org.scalatest.BeforeAndAfterAll
import pureconfig.ConfigSource
import pureconfig.generic.auto._
import zio.interop.catz.core._
import zio.{Runtime, Semaphore, ZEnv, ZIO}

import scala.concurrent.duration._
import scala.concurrent.{Await, Future}

class BaseServerSpec extends JsonRpcServerTestKit with BeforeAndAfterAll {

  /** Tests can override this value to request a specific engine version to be
    * preinstalled when running the suite.
    */
  val engineToInstall: Option[SemVer] = None

  /** Tests can override this to set up a logging service that will print debug
    * logs.
    */
  val debugLogs: Boolean = false

  /** Tests can override this to allow child process output to be displayed. */
  val debugChildLogs: Boolean = false

  override def protocol: Protocol = JsonRpc.protocol

  val config: ProjectManagerConfig =
    ConfigSource
      .resources(ConfigFilename)
      .withFallback(ConfigSource.systemProperties)
      .at(ConfigNamespace)
      .loadOrThrow[ProjectManagerConfig]

  val testClock =
    new ProgrammableClock[ZEnv](OffsetDateTime.now(ZoneOffset.UTC))

  def getGeneratedUUID: UUID = {
    Await.result(Future(gen.takeFirst())(system.dispatcher), 3.seconds.dilated)
  }

  lazy val gen = new ObservableGenerator[ZEnv]()

  val testProjectsRoot = Files.createTempDirectory(null).toFile
  sys.addShutdownHook(FileUtils.deleteQuietly(testProjectsRoot))

  val testDistributionRoot = Files.createTempDirectory(null).toFile
  sys.addShutdownHook(FileUtils.deleteQuietly(testDistributionRoot))

  val userProjectDir = new File(testProjectsRoot, "projects")

  lazy val testStorageConfig = StorageConfig(
    projectsRoot             = testProjectsRoot,
    userProjectsPath         = userProjectDir,
    projectMetadataDirectory = ".enso",
    projectMetadataFileName  = "project.json"
  )

  lazy val bootloaderConfig = config.bootloader

  lazy val timeoutConfig = config.timeout

  lazy val netConfig = config.network

  lazy val supervisionConfig = config.supervision

  implicit val exec = new ZioEnvExec(Runtime.default)

  lazy val fileSystem = new BlockingFileSystem(5.seconds)

  lazy val storageSemaphore =
    Runtime.default.unsafeRun(Semaphore.make(1))

  lazy val projectRepository =
    new ProjectFileRepository(
      testStorageConfig,
      testClock,
      fileSystem,
      gen
    )

  lazy val projectValidator = new MonadicProjectValidator[ZIO[ZEnv, *, *]]()

  val distributionConfiguration =
    TestDistributionConfiguration(
      distributionRoot       = testDistributionRoot.toPath,
      engineReleaseProvider  = FakeReleases.engineReleaseProvider,
      runtimeReleaseProvider = FakeReleases.runtimeReleaseProvider,
      discardChildOutput     = !debugChildLogs
    )

  val loggingService = new TestLoggingService

  lazy val languageServerRegistry =
    system.actorOf(
      LanguageServerRegistry
        .props(
          netConfig,
          bootloaderConfig,
          supervisionConfig,
          timeoutConfig,
          distributionConfiguration,
          loggingService,
          ExecutorWithUnlimitedPool
        )
    )

  lazy val shutdownHookActivator =
    system.actorOf(ShutdownHookActivator.props[ZIO[ZEnv, +*, +*]]())

  lazy val languageServerGateway =
    new LanguageServerGatewayImpl[ZIO[ZEnv, +*, +*]](
      languageServerRegistry,
      shutdownHookActivator,
      system,
      timeoutConfig
    )

  lazy val projectCreationService =
    new ProjectCreationService[ZIO[ZEnv, +*, +*]](
      distributionConfiguration,
      loggingService
    )

  lazy val globalConfigService = new GlobalConfigService[ZIO[ZEnv, +*, +*]](
    distributionConfiguration
  )

  lazy val projectService =
    new ProjectService[ZIO[ZEnv, +*, +*]](
      projectValidator,
      projectRepository,
      projectCreationService,
      globalConfigService,
      new Slf4jLogging[ZIO[ZEnv, +*, +*]],
      testClock,
      gen,
      languageServerGateway,
      distributionConfiguration
    )

  lazy val runtimeVersionManagementService =
    new RuntimeVersionManagementService[ZIO[ZEnv, +*, +*]](
      distributionConfiguration
    )

  override def clientControllerFactory: ClientControllerFactory = {
    new ManagerClientControllerFactory[ZIO[ZEnv, +*, +*]](
      system                          = system,
      projectService                  = projectService,
      globalConfigService             = globalConfigService,
      runtimeVersionManagementService = runtimeVersionManagementService,
      loggingServiceDescriptor        = loggingService,
      timeoutConfig                   = timeoutConfig
    )
  }

  /** Can be used to avoid deleting the project's root. */
  val deleteProjectsRootAfterEachTest = true

  override def afterEach(): Unit = {
    super.afterEach()

    if (deleteProjectsRootAfterEachTest)
      FileUtils.deleteQuietly(testProjectsRoot)
  }

  override def afterAll(): Unit = {
    super.afterAll()

    FileUtils.deleteQuietly(testProjectsRoot)
  }

  override def beforeAll(): Unit = {
    super.beforeAll()

    setupEditions()

    if (debugLogs) {
      LoggingServiceManager.setup(
        LoggerMode.Local(
          Seq(StderrPrinterWithColors.colorPrinterIfAvailable(true))
        ),
        LogLevel.Trace
      )
    }

    engineToInstall.foreach(preInstallEngine)
  }

  private def setupEditions(): Unit = {
    val engineVersion =
      engineToInstall.map(_.toString).getOrElse(buildinfo.Info.ensoVersion)
    val editionsDir = testDistributionRoot.toPath / "test_data" / "editions"
    Files.createDirectories(editionsDir)
    val editionName = buildinfo.Info.currentEdition + ".yaml"
    val editionConfig =
      s"""engine-version: $engineVersion
         |""".stripMargin
    FileSystem.writeTextFile(editionsDir / editionName, editionConfig)
  }

  /** This is a temporary solution to ensure that a valid engine distribution is
    * preinstalled.
    *
    * In the future the fake release mechanism can be properly updated to allow
    * for this kind of configuration without special logic.
    */
  def preInstallEngine(version: SemVer): Unit = {
    val os   = OS.operatingSystem.configName
    val ext  = if (OS.isWindows) "zip" else "tar.gz"
    val arch = OS.architecture
    val path = FakeReleases.releaseRoot
      .resolve("enso")
      .resolve(s"enso-$version")
      .resolve(s"enso-engine-$version-$os-$arch.$ext")
      .resolve(s"enso-$version")
      .resolve("component")
    val root = Path.of("../../../").toAbsolutePath.normalize
    FileUtils.copyFile(
      root.resolve("runner.jar").toFile,
      path.resolve("runner.jar").toFile
    )
    FileUtils.copyFile(
      root.resolve("runtime.jar").toFile,
      path.resolve("runtime.jar").toFile
    )

    val blackhole = system.actorOf(blackholeProps)
    val installAction = runtimeVersionManagementService.installEngine(
      blackhole,
      version,
      forceInstallBroken = false
    )
    Runtime.default.unsafeRun(installAction)
  }

  def uninstallEngine(version: SemVer): Unit = {
    val blackhole = system.actorOf(blackholeProps)
    val action = runtimeVersionManagementService.uninstallEngine(
      blackhole,
      version
    )
    Runtime.default.unsafeRun(action)
  }

  def uninstallRuntime(graalVMVersion: GraalVMVersion): Unit = {
    val blackhole = system.actorOf(blackholeProps)
    val runtimeVersionManager = RuntimeVersionManagerFactory(
      distributionConfiguration
    ).makeRuntimeVersionManager(blackhole, MissingComponentAction.Fail)
    val runtime = runtimeVersionManager.findGraalRuntime(graalVMVersion).get
    FileUtils.deleteDirectory(runtime.path.toFile)
  }

  implicit class ClientSyntax(client: WsTestClient) {
    def expectTaskStarted(
      timeout: FiniteDuration = 20.seconds.dilated
    ): Unit = {
      inside(parse(client.expectMessage(timeout))) { case Right(json) =>
        getMethod(json) shouldEqual Some("task/started")
      }
    }

    private def getMethod(json: Json): Option[String] = for {
      obj    <- json.asObject
      method <- obj("method").flatMap(_.asString)
    } yield method

    def expectJsonIgnoring(
      shouldIgnore: Json => Boolean,
      timeout: FiniteDuration = 20.seconds.dilated
    ): Json = {
      inside(parse(client.expectMessage(timeout))) { case Right(json) =>
        if (shouldIgnore(json)) expectJsonIgnoring(shouldIgnore, timeout)
        else json
      }
    }

    def expectError(
      expectedCode: Int,
      timeout: FiniteDuration = 10.seconds.dilated
    ): Unit = {
      withClue("Response should be an error: ") {
        inside(parse(client.expectMessage(timeout))) { case Right(json) =>
          val code = for {
            obj   <- json.asObject
            error <- obj("error").flatMap(_.asObject)
            code  <- error("code").flatMap(_.asNumber).flatMap(_.toInt)
          } yield code
          code shouldEqual Some(expectedCode)
        }
      }
    }

    def expectJsonAfterSomeProgress(
      json: Json,
      timeout: FiniteDuration = 10.seconds.dilated
    ): Unit =
      expectJsonIgnoring(
        json => getMethod(json).exists(_.startsWith("task/")),
        timeout
      ) shouldEqual json
  }

  /** Modifies the project's package.yaml. */
  def updateProjectConfig(
    projectName: String
  )(update: Config => Config): Unit = {
    val pkgFile = new File(userProjectDir, projectName)
    val pkg     = PackageManager.Default.loadPackage(pkgFile).get
    pkg.updateConfig(update)
  }

  /** Sets project's parent edition. */
  def setProjectParentEdition(
    projectName: String,
    newParentEditionName: String
  ): Unit = updateProjectConfig(projectName) { config =>
    config.copy(edition =
      Some(Editions.Raw.Edition(parent = Some(newParentEditionName)))
    )
  }
}
