package org.enso.languageserver.protocol.json

import akka.actor.{ActorRef, ActorSystem}
import org.enso.distribution.EditionManager
import org.enso.jsonrpc.ClientControllerFactory
import org.enso.languageserver.boot.resource.InitializationComponent
import org.enso.languageserver.data.Config
import org.enso.languageserver.libraries.EditionReferenceResolver

import java.util.UUID

/** Language server client controller factory.
  *
  * @param mainComponent the main initialization logic
  * @param bufferRegistry the buffer registry actor ref
  * @param capabilityRouter the capability router actor ref
  * @param system the actor system
  */
class JsonConnectionControllerFactory(
  mainComponent: InitializationComponent,
  bufferRegistry: ActorRef,
  capabilityRouter: ActorRef,
  fileManager: ActorRef,
  contentRootManager: ActorRef,
  contextRegistry: ActorRef,
  suggestionsHandler: ActorRef,
  stdOutController: ActorRef,
  stdErrController: ActorRef,
  stdInController: ActorRef,
  runtimeConnector: ActorRef,
  idlenessMonitor: ActorRef,
  projectSettingsManager: ActorRef,
  localLibraryManager: ActorRef,
  editionReferenceResolver: EditionReferenceResolver,
  editionManager: EditionManager,
  config: Config
)(implicit system: ActorSystem)
    extends ClientControllerFactory {

  /** Creates a client controller actor.
    *
    * @param clientId the internal client id.
    * @return the client controller actor
    */
  override def createClientController(clientId: UUID): ActorRef =
    system.actorOf(
      JsonConnectionController.props(
        connectionId             = clientId,
        mainComponent            = mainComponent,
        bufferRegistry           = bufferRegistry,
        capabilityRouter         = capabilityRouter,
        fileManager              = fileManager,
        contentRootManager       = contentRootManager,
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
        languageServerConfig     = config
      )
    )
}
