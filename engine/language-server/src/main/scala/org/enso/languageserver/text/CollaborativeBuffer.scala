package org.enso.languageserver.text

import akka.actor.{Actor, ActorRef, Cancellable, Props, Stash}
import cats.implicits._
import com.typesafe.scalalogging.LazyLogging
import org.enso.languageserver.capability.CapabilityProtocol._
import org.enso.languageserver.data.{CanEdit, CapabilityRegistration, ClientId}
import org.enso.languageserver.event.{
  BufferClosed,
  BufferOpened,
  JsonSessionTerminated
}
import org.enso.languageserver.filemanager.FileManagerProtocol.{
  ReadTextualFileResult,
  TextualFileContent,
  WriteFileResult
}
import org.enso.languageserver.filemanager.{
  FileManagerProtocol,
  OperationTimeout,
  Path
}
import org.enso.languageserver.session.JsonSession
import org.enso.languageserver.text.CollaborativeBuffer.IOTimeout
import org.enso.languageserver.text.TextProtocol._
import org.enso.languageserver.util.UnhandledLogging
import org.enso.polyglot.runtime.Runtime.Api
import org.enso.text.{ContentBasedVersioning, ContentVersion}
import org.enso.text.editing._
import org.enso.text.editing.model.TextEdit

import scala.concurrent.duration._
import scala.language.postfixOps

/** An actor enabling multiple users edit collaboratively a file.
  *
  * @param bufferPath a path to a file
  * @param fileManager a file manger actor
  * @param runtimeConnector a gateway to the runtime
  * @param timeout a request timeout
  * @param versionCalculator a content based version calculator
  */
class CollaborativeBuffer(
  bufferPath: Path,
  fileManager: ActorRef,
  runtimeConnector: ActorRef,
  timeout: FiniteDuration
)(implicit
  versionCalculator: ContentBasedVersioning
) extends Actor
    with Stash
    with LazyLogging
    with UnhandledLogging {

  import context.dispatcher

  override def preStart(): Unit = {
    context.system.eventStream
      .subscribe(self, classOf[JsonSessionTerminated]): Unit
  }

  override def receive: Receive = uninitialized

  private def uninitialized: Receive = { case OpenFile(client, path) =>
    context.system.eventStream.publish(BufferOpened(path))
    logger.info(
      "Buffer opened for [path:{}, client:{}].",
      path,
      client.clientId
    )
    readFile(client, path)
  }

  private def waitingForFileContent(
    rpcSession: JsonSession,
    replyTo: ActorRef,
    timeoutCancellable: Cancellable
  ): Receive = {
    case ReadTextualFileResult(Right(content)) =>
      handleFileContent(rpcSession, replyTo, content)
      unstashAll()
      timeoutCancellable.cancel(): Unit

    case ReadTextualFileResult(Left(failure)) =>
      replyTo ! OpenFileResponse(Left(failure))
      timeoutCancellable.cancel()
      stop()

    case IOTimeout =>
      replyTo ! OpenFileResponse(Left(OperationTimeout))
      stop()

    case _ => stash()
  }

  private def collaborativeEditing(
    buffer: Buffer,
    clients: Map[ClientId, JsonSession],
    lockHolder: Option[JsonSession]
  ): Receive = {
    case OpenFile(client, _) =>
      openFile(buffer, clients, lockHolder, client)

    case AcquireCapability(client, CapabilityRegistration(CanEdit(path))) =>
      acquireWriteLock(buffer, clients, lockHolder, client, path)

    case ReleaseCapability(client, CapabilityRegistration(CanEdit(_))) =>
      releaseWriteLock(buffer, clients, lockHolder, client.clientId)

    case JsonSessionTerminated(client) =>
      if (clients.contains(client.clientId)) {
        removeClient(buffer, clients, lockHolder, client.clientId)
      }

    case CloseFile(clientId, _) =>
      if (clients.contains(clientId)) {
        removeClient(buffer, clients, lockHolder, clientId)
        sender() ! FileClosed
      } else {
        sender() ! FileNotOpened
      }

    case ApplyEdit(clientId, change) =>
      edit(buffer, clients, lockHolder, clientId, change)

    case SaveFile(clientId, _, clientVersion) =>
      saveFile(
        buffer,
        clients,
        lockHolder,
        clientId,
        ContentVersion(clientVersion)
      )
  }

  private def saving(
    buffer: Buffer,
    clients: Map[ClientId, JsonSession],
    lockHolder: Option[JsonSession],
    replyTo: ActorRef,
    timeoutCancellable: Cancellable
  ): Receive = {
    case IOTimeout =>
      replyTo ! SaveFailed(OperationTimeout)
      unstashAll()
      context.become(collaborativeEditing(buffer, clients, lockHolder))

    case WriteFileResult(Left(failure)) =>
      replyTo ! SaveFailed(failure)
      unstashAll()
      timeoutCancellable.cancel()
      context.become(collaborativeEditing(buffer, clients, lockHolder))

    case WriteFileResult(Right(())) =>
      replyTo ! FileSaved
      unstashAll()
      timeoutCancellable.cancel()
      context.become(collaborativeEditing(buffer, clients, lockHolder))

    case _ => stash()
  }

  private def saveFile(
    buffer: Buffer,
    clients: Map[ClientId, JsonSession],
    lockHolder: Option[JsonSession],
    clientId: ClientId,
    clientVersion: ContentVersion
  ): Unit = {
    val hasLock = lockHolder.exists(_.clientId == clientId)
    if (hasLock) {
      if (clientVersion == buffer.version) {
        fileManager ! FileManagerProtocol.WriteFile(
          bufferPath,
          buffer.contents.toString
        )

        val timeoutCancellable = context.system.scheduler
          .scheduleOnce(timeout, self, IOTimeout)
        context.become(
          saving(buffer, clients, lockHolder, sender(), timeoutCancellable)
        )
      } else {
        sender() ! SaveFileInvalidVersion(
          clientVersion.toHexString,
          buffer.version.toHexString
        )
      }
    } else {
      sender() ! SaveDenied
    }
  }

  private def edit(
    buffer: Buffer,
    clients: Map[ClientId, JsonSession],
    lockHolder: Option[JsonSession],
    clientId: ClientId,
    change: FileEdit
  ): Unit = {
    applyEdits(buffer, lockHolder, clientId, change) match {
      case Left(failure) =>
        sender() ! failure

      case Right(modifiedBuffer) =>
        sender() ! ApplyEditSuccess
        val subscribers = clients.filterNot(_._1 == clientId).values
        subscribers foreach { _.rpcController ! TextDidChange(List(change)) }
        runtimeConnector ! Api.Request(
          Api.EditFileNotification(buffer.file, change.edits)
        )
        context.become(
          collaborativeEditing(modifiedBuffer, clients, lockHolder)
        )
    }
  }

  private def applyEdits(
    buffer: Buffer,
    lockHolder: Option[JsonSession],
    clientId: ClientId,
    change: FileEdit
  ): Either[ApplyEditFailure, Buffer] =
    for {
      _              <- validateAccess(lockHolder, clientId)
      _              <- validateVersions(ContentVersion(change.oldVersion), buffer.version)
      modifiedBuffer <- doEdit(buffer, change.edits)
      _ <- validateVersions(
        ContentVersion(change.newVersion),
        modifiedBuffer.version
      )
    } yield modifiedBuffer

  private def validateVersions(
    clientVersion: ContentVersion,
    serverVersion: ContentVersion
  ): Either[ApplyEditFailure, Unit] = {
    if (clientVersion == serverVersion) {
      Right(())
    } else {
      Left(
        TextEditInvalidVersion(
          clientVersion.toHexString,
          serverVersion.toHexString
        )
      )
    }
  }

  private def validateAccess(
    lockHolder: Option[JsonSession],
    clientId: ClientId
  ): Either[ApplyEditFailure, Unit] = {
    val hasLock = lockHolder.exists(_.clientId == clientId)
    if (hasLock) {
      Right(())
    } else {
      Left(WriteDenied)
    }
  }

  private def doEdit(
    buffer: Buffer,
    edits: List[TextEdit]
  ): Either[ApplyEditFailure, Buffer] = {
    EditorOps
      .applyEdits(buffer.contents, edits)
      .leftMap(toEditFailure)
      .map(rope =>
        Buffer(buffer.file, rope, versionCalculator.evalVersion(rope.toString))
      )
  }

  private val toEditFailure: TextEditValidationFailure => ApplyEditFailure = {
    case EndPositionBeforeStartPosition =>
      TextEditValidationFailed("The start position is after the end position")
    case NegativeCoordinateInPosition =>
      TextEditValidationFailed("Negative coordinate in a position object")
    case InvalidPosition(position) =>
      TextEditValidationFailed(s"Invalid position: $position")
  }

  private def readFile(rpcSession: JsonSession, path: Path): Unit = {
    fileManager ! FileManagerProtocol.ReadFile(path)
    val timeoutCancellable = context.system.scheduler
      .scheduleOnce(timeout, self, IOTimeout)
    context.become(
      waitingForFileContent(rpcSession, sender(), timeoutCancellable)
    )
  }

  private def handleFileContent(
    rpcSession: JsonSession,
    originalSender: ActorRef,
    file: TextualFileContent
  ): Unit = {
    val buffer = Buffer(file.path, file.content)
    val cap    = CapabilityRegistration(CanEdit(bufferPath))
    originalSender ! OpenFileResponse(
      Right(OpenFileResult(buffer, Some(cap)))
    )
    runtimeConnector ! Api.Request(
      Api.OpenFileNotification(file.path, file.content)
    )
    context.become(
      collaborativeEditing(
        buffer,
        Map(rpcSession.clientId -> rpcSession),
        Some(rpcSession)
      )
    )
  }

  private def openFile(
    buffer: Buffer,
    clients: Map[ClientId, JsonSession],
    lockHolder: Option[JsonSession],
    rpcSession: JsonSession
  ): Unit = {
    val writeCapability =
      if (lockHolder.isEmpty)
        Some(CapabilityRegistration(CanEdit(bufferPath)))
      else
        None
    sender() ! OpenFileResponse(Right(OpenFileResult(buffer, writeCapability)))
    context.become(
      collaborativeEditing(
        buffer,
        clients + (rpcSession.clientId -> rpcSession),
        lockHolder
      )
    )
  }

  private def removeClient(
    buffer: Buffer,
    clients: Map[ClientId, JsonSession],
    lockHolder: Option[JsonSession],
    clientId: ClientId
  ): Unit = {
    val newLock =
      lockHolder.flatMap {
        case holder if holder.clientId == clientId => None
        case holder                                => Some(holder)
      }
    val newClientMap = clients - clientId
    if (newClientMap.isEmpty) {
      runtimeConnector ! Api.Request(Api.CloseFileNotification(buffer.file))
      stop()
    } else {
      context.become(collaborativeEditing(buffer, newClientMap, newLock))
    }
  }

  private def releaseWriteLock(
    buffer: Buffer,
    clients: Map[ClientId, JsonSession],
    lockHolder: Option[JsonSession],
    clientId: ClientId
  ): Unit = {
    lockHolder match {
      case None =>
        sender() ! CapabilityReleaseBadRequest
        context.become(collaborativeEditing(buffer, clients, lockHolder))

      case Some(holder) if holder.clientId != clientId =>
        sender() ! CapabilityReleaseBadRequest
        context.become(collaborativeEditing(buffer, clients, lockHolder))

      case Some(_) =>
        sender() ! CapabilityReleased
        context.become(collaborativeEditing(buffer, clients, None))
    }
  }

  private def acquireWriteLock(
    buffer: Buffer,
    clients: Map[ClientId, JsonSession],
    lockHolder: Option[JsonSession],
    clientId: JsonSession,
    path: Path
  ): Unit = {
    lockHolder match {
      case None =>
        sender() ! CapabilityAcquired
        context.become(collaborativeEditing(buffer, clients, Some(clientId)))

      case Some(holder) if holder == clientId =>
        sender() ! CapabilityAcquisitionBadRequest
        context.become(collaborativeEditing(buffer, clients, lockHolder))

      case Some(holder) =>
        sender() ! CapabilityAcquired
        holder.rpcController ! CapabilityForceReleased(
          CapabilityRegistration(CanEdit(path))
        )
        context.become(collaborativeEditing(buffer, clients, Some(clientId)))
    }
  }

  def stop(): Unit = {
    context.system.eventStream.publish(BufferClosed(bufferPath))
    context.stop(self)
  }

}

object CollaborativeBuffer {

  case object IOTimeout

  /** Creates a configuration object used to create a [[CollaborativeBuffer]]
    *
    * @param bufferPath a path to a file
    * @param fileManager a file manager actor
    * @param runtimeConnector a gateway to the runtime
    * @param timeout a request timeout
    * @param versionCalculator a content based version calculator
    * @return a configuration object
    */
  def props(
    bufferPath: Path,
    fileManager: ActorRef,
    runtimeConnector: ActorRef,
    timeout: FiniteDuration = 10 seconds
  )(implicit versionCalculator: ContentBasedVersioning): Props =
    Props(
      new CollaborativeBuffer(
        bufferPath,
        fileManager,
        runtimeConnector,
        timeout
      )
    )

}
