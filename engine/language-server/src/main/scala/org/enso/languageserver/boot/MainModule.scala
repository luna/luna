package org.enso.languageserver.boot

import akka.actor.ActorSystem
import org.enso.distribution.{
  DistributionManager,
  EditionManager,
  Environment,
  LanguageHome
}
import org.enso.editions.EditionResolver
import org.enso.jsonrpc.JsonRpcServer
import org.enso.languageserver.boot.DeploymentType.{Azure, Desktop}
import org.enso.languageserver.capability.CapabilityRouter
import org.enso.languageserver.data._
import org.enso.languageserver.effect.ZioExec
import org.enso.languageserver.filemanager._
import org.enso.languageserver.http.server.BinaryWebSocketServer
import org.enso.languageserver.io._
import org.enso.languageserver.libraries.{
  EditionReferenceResolver,
  LocalLibraryManager,
  ProjectSettingsManager
}
import org.enso.languageserver.monitoring.{
  HealthCheckEndpoint,
  IdlenessEndpoint,
  IdlenessMonitor
}
import org.enso.languageserver.protocol.binary.{
  BinaryConnectionControllerFactory,
  InboundMessageDecoder
}
import org.enso.languageserver.protocol.json.{
  JsonConnectionControllerFactory,
  JsonRpc
}
import org.enso.languageserver.requesthandler.monitoring.PingHandler
import org.enso.languageserver.runtime._
import org.enso.languageserver.search.SuggestionsHandler
import org.enso.languageserver.session.SessionRouter
import org.enso.languageserver.text.BufferRegistry
import org.enso.languageserver.util.binary.BinaryEncoder
import org.enso.loggingservice.{JavaLoggingLogHandler, LogLevel}
import org.enso.polyglot.{RuntimeOptions, RuntimeServerInfo}
import org.enso.searcher.sql.{SqlDatabase, SqlSuggestionsRepo, SqlVersionsRepo}
import org.enso.text.{ContentBasedVersioning, Sha3_224VersionCalculator}
import org.graalvm.polyglot.Context
import org.graalvm.polyglot.io.MessageEndpoint
import org.slf4j.LoggerFactory

import java.io.File
import java.net.URI
import java.time.Clock
import scala.concurrent.duration._

/** A main module containing all components of the server.
  *
  * @param serverConfig configuration for the language server
  * @param logLevel log level for the Language Server
  */
class MainModule(serverConfig: LanguageServerConfig, logLevel: LogLevel) {

  private val log = LoggerFactory.getLogger(this.getClass)
  log.info(
    "Initializing main module of the Language Server from [{}, {}]",
    serverConfig,
    logLevel
  )

  private val utcClock = Clock.systemUTC()

  val directoriesConfig = ProjectDirectoriesConfig(serverConfig.contentRootPath)
  private val contentRoot = ContentRootWithFile(
    ContentRoot.Project(serverConfig.contentRootUuid),
    new File(serverConfig.contentRootPath)
  )
  val languageServerConfig = Config(
    contentRoot,
    FileManagerConfig(timeout = 3.seconds),
    PathWatcherConfig(),
    ExecutionContextConfig(),
    directoriesConfig
  )
  log.trace("Created Language Server config [{}].", languageServerConfig)

  val zioExec = ZioExec(zio.Runtime.default)
  log.trace("Created ZIO executor [{}].", zioExec)

  val fileSystem: FileSystem = new FileSystem
  log.trace("Created file system [{}].", fileSystem)

  implicit val versionCalculator: ContentBasedVersioning =
    Sha3_224VersionCalculator
  log.trace("Created Version Calculator [{}].", versionCalculator)

  implicit val system =
    ActorSystem(
      serverConfig.name,
      None,
      None,
      Some(serverConfig.computeExecutionContext)
    )
  log.trace(s"Created ActorSystem $system.")

  val sqlDatabase =
    DeploymentType.fromEnvironment() match {
      case Desktop =>
        SqlDatabase(languageServerConfig.directories.suggestionsDatabaseFile)
      case Azure =>
        SqlDatabase.inmem("memdb")
    }

  val suggestionsRepo = new SqlSuggestionsRepo(sqlDatabase)(system.dispatcher)
  val versionsRepo    = new SqlVersionsRepo(sqlDatabase)(system.dispatcher)
  log.trace("Created SQL repos: [{}. {}].", suggestionsRepo, versionsRepo)

  val idlenessMonitor =
    system.actorOf(IdlenessMonitor.props(utcClock))

  lazy val sessionRouter =
    system.actorOf(SessionRouter.props(), "session-router")

  lazy val runtimeConnector =
    system.actorOf(RuntimeConnector.props, "runtime-connector")

  lazy val contentRootManagerActor =
    system.actorOf(
      ContentRootManagerActor.props(languageServerConfig),
      "content-root-manager"
    )

  lazy val contentRootManagerWrapper: ContentRootManager =
    new ContentRootManagerWrapper(languageServerConfig, contentRootManagerActor)

  lazy val fileManager = system.actorOf(
    FileManager.pool(
      languageServerConfig.fileManager,
      contentRootManagerWrapper,
      fileSystem,
      zioExec
    ),
    "file-manager"
  )

  lazy val bufferRegistry =
    system.actorOf(
      BufferRegistry.props(fileManager, runtimeConnector),
      "buffer-registry"
    )

  lazy val receivesTreeUpdatesHandler =
    system.actorOf(
      ReceivesTreeUpdatesHandler.props(
        languageServerConfig,
        contentRootManagerWrapper,
        fileSystem,
        zioExec
      ),
      "file-event-registry"
    )

  lazy val suggestionsHandler =
    system.actorOf(
      SuggestionsHandler
        .props(
          languageServerConfig,
          contentRootManagerWrapper,
          suggestionsRepo,
          versionsRepo,
          sessionRouter,
          runtimeConnector
        ),
      "suggestions-handler"
    )

  lazy val capabilityRouter =
    system.actorOf(
      CapabilityRouter.props(
        bufferRegistry,
        receivesTreeUpdatesHandler,
        suggestionsHandler
      ),
      "capability-router"
    )

  lazy val contextRegistry =
    system.actorOf(
      ContextRegistry
        .props(
          suggestionsRepo,
          languageServerConfig,
          RuntimeFailureMapper(contentRootManagerWrapper),
          runtimeConnector,
          sessionRouter
        ),
      "context-registry"
    )

  val stdOut    = new ObservableOutputStream
  val stdErr    = new ObservableOutputStream
  val stdInSink = new ObservableOutputStream
  val stdIn     = new ObservablePipedInputStream(stdInSink)

  val context = Context
    .newBuilder()
    .allowAllAccess(true)
    .allowExperimentalOptions(true)
    .option(RuntimeServerInfo.ENABLE_OPTION, "true")
    .option(RuntimeOptions.INTERACTIVE_MODE, "true")
    .option(RuntimeOptions.PROJECT_ROOT, serverConfig.contentRootPath)
    .option(
      RuntimeOptions.LOG_LEVEL,
      JavaLoggingLogHandler.getJavaLogLevelFor(logLevel).getName
    )
    .option(
      RuntimeServerInfo.JOB_PARALLELISM_OPTION,
      Runtime.getRuntime.availableProcessors().toString
    )
    .out(stdOut)
    .err(stdErr)
    .in(stdIn)
    .logHandler(
      JavaLoggingLogHandler.create(JavaLoggingLogHandler.defaultLevelMapping)
    )
    .serverTransport((uri: URI, peerEndpoint: MessageEndpoint) => {
      if (uri.toString == RuntimeServerInfo.URI) {
        val connection = new RuntimeConnector.Endpoint(
          runtimeConnector,
          peerEndpoint
        )
        runtimeConnector ! RuntimeConnector.Initialize(peerEndpoint)
        connection
      } else null
    })
    .build()
  log.trace("Created Runtime context [{}].", context)

  system.eventStream.setLogLevel(LogLevel.toAkka(logLevel))
  log.trace("Set akka log level to [{}].", logLevel)

  val runtimeKiller =
    system.actorOf(
      RuntimeKiller.props(runtimeConnector, context),
      "runtime-context"
    )

  val stdOutController =
    system.actorOf(
      OutputRedirectionController
        .props(stdOut, OutputKind.StandardOutput, sessionRouter),
      "std-out-controller"
    )

  val stdErrController =
    system.actorOf(
      OutputRedirectionController
        .props(stdErr, OutputKind.StandardError, sessionRouter),
      "std-err-controller"
    )

  val stdInController =
    system.actorOf(
      InputRedirectionController.props(stdIn, stdInSink, sessionRouter),
      "std-in-controller"
    )

  val initializationComponent = ResourcesInitialization(
    system.eventStream,
    directoriesConfig,
    suggestionsRepo,
    versionsRepo,
    context
  )(system.dispatcher)

  val environment         = new Environment {}
  val languageHome        = LanguageHome.detectFromExecutableLocation(environment)
  val distributionManager = new DistributionManager(environment)

  val editionProvider =
    EditionManager.makeEditionProvider(distributionManager, Some(languageHome))
  val editionResolver = EditionResolver(editionProvider)
  val editionReferenceResolver = new EditionReferenceResolver(
    contentRoot.file,
    editionProvider,
    editionResolver
  )
  val editionManager = EditionManager(distributionManager, Some(languageHome))

  val projectSettingsManager = system.actorOf(
    ProjectSettingsManager.props(contentRoot.file, editionResolver),
    "project-settings-manager"
  )

  val localLibraryManager = system.actorOf(
    LocalLibraryManager.props(contentRoot.file, distributionManager),
    "local-library-manager"
  )

  val jsonRpcControllerFactory = new JsonConnectionControllerFactory(
    mainComponent            = initializationComponent,
    bufferRegistry           = bufferRegistry,
    capabilityRouter         = capabilityRouter,
    fileManager              = fileManager,
    contentRootManager       = contentRootManagerActor,
    contextRegistry          = contextRegistry,
    suggestionsHandler       = suggestionsHandler,
    stdOutController         = stdOutController,
    stdErrController         = stdErrController,
    stdInController          = stdInController,
    runtimeConnector         = runtimeConnector,
    idlenessMonitor          = idlenessMonitor,
    projectSettingsManager   = projectSettingsManager,
    localLibraryManager      = localLibraryManager,
    editionReferenceResolver = editionReferenceResolver,
    editionManager           = editionManager,
    config                   = languageServerConfig
  )
  log.trace(
    "Created JSON connection controller factory [{}].",
    jsonRpcControllerFactory
  )

  val pingHandlerProps =
    PingHandler.props(
      List(
        bufferRegistry,
        capabilityRouter,
        fileManager,
        contextRegistry
      ),
      10.seconds,
      true
    )

  val healthCheckEndpoint =
    new HealthCheckEndpoint(pingHandlerProps, system)(
      serverConfig.computeExecutionContext
    )

  val idlenessEndpoint =
    new IdlenessEndpoint(idlenessMonitor)

  val jsonRpcServer =
    new JsonRpcServer(
      JsonRpc.protocol,
      jsonRpcControllerFactory,
      JsonRpcServer
        .Config(outgoingBufferSize = 10000, lazyMessageTimeout = 10.seconds),
      List(healthCheckEndpoint, idlenessEndpoint)
    )
  log.trace("Created JSON RPC Server [{}].", jsonRpcServer)

  val binaryServer =
    new BinaryWebSocketServer(
      InboundMessageDecoder,
      BinaryEncoder.empty,
      new BinaryConnectionControllerFactory(fileManager)
    )
  log.trace("Created Binary WebSocket Server [{}].", binaryServer)

  log.info(
    "Main module of the Language Server initialized with config [{}].",
    languageServerConfig
  )

  /** Close the main module releasing all resources. */
  def close(): Unit = {
    suggestionsRepo.close()
    versionsRepo.close()
    log.info("Closed Language Server main module.")
  }
}
