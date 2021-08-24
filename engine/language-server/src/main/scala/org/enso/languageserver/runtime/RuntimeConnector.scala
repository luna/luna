package org.enso.languageserver.runtime

import java.nio.ByteBuffer
import akka.actor.{Actor, ActorRef, Props, Stash}
import com.typesafe.scalalogging.LazyLogging
import org.enso.languageserver.util.UnhandledLogging
import org.enso.languageserver.runtime.RuntimeConnector.{
  Destroy,
  MessageFromRuntime
}
import org.enso.polyglot.runtime.Runtime
import org.enso.polyglot.runtime.Runtime.{Api, ApiEnvelope}
import org.graalvm.polyglot.io.MessageEndpoint

/** An actor managing a connection to Enso's runtime server.
  */
class RuntimeConnector
    extends Actor
    with LazyLogging
    with UnhandledLogging
    with Stash {

  override def preStart(): Unit = {
    logger.info("Starting the runtime connector.")
  }

  override def receive: Receive = {
    case RuntimeConnector.Initialize(engine) =>
      logger.info(
        s"Runtime connector established connection with the message endpoint [{}].",
        engine
      )
      unstashAll()
      context.become(initialized(engine, Map()))
    case _ => stash()
  }

  /** Performs communication between runtime and language server.
    * Requests are sent from language server to runtime,
    * responses are forwarded from runtime to the sender.
    *
    * @param engine endpoint of a runtime
    * @param senders request ids with corresponding senders
    */
  def initialized(
    engine: MessageEndpoint,
    senders: Map[Runtime.Api.RequestId, ActorRef]
  ): Receive = {
    case Destroy => context.stop(self)

    case msg: Runtime.ApiEnvelope =>
      engine.sendBinary(Runtime.Api.serialize(msg))

      msg match {
        case Api.Request(Some(id), _) =>
          context.become(initialized(engine, senders + (id -> sender())))
        case _ =>
      }

    case MessageFromRuntime(msg: Runtime.Api.Request) =>
      context.system.eventStream.publish(msg)
    case MessageFromRuntime(
          Runtime.Api.Response(None, msg: Runtime.ApiResponse)
        ) =>
      context.system.eventStream.publish(msg)
    case MessageFromRuntime(
          msg @ Runtime.Api.Response(Some(correlationId), _)
        ) =>
      senders.get(correlationId).foreach(_ ! msg)
      context.become(initialized(engine, senders - correlationId))
  }
}

object RuntimeConnector {

  /** Protocol message to pass the runtime connection to the actor.
    *
    * @param engineConnection the open runtime connection.
    */
  case class Initialize(engineConnection: MessageEndpoint)

  /** Protocol message to inform the actor about the connection being closed.
    */
  case object Destroy

  /** Helper for creating instances of the [[RuntimeConnector]] actor.
    *
    * @return a [[Props]] instance for the newly created actor.
    */
  def props: Props =
    Props(new RuntimeConnector)

  /** Endpoint implementation used to handle connections with the runtime.
    *
    * @param actor the actor ref to pass received messages to.
    * @param peerEndpoint the runtime server's connection end.
    */
  class Endpoint(actor: ActorRef, peerEndpoint: MessageEndpoint)
      extends MessageEndpoint {
    override def sendText(text: String): Unit = {}

    override def sendBinary(data: ByteBuffer): Unit =
      Runtime.Api
        .deserializeApiEnvelope(data)
        .foreach(actor ! MessageFromRuntime(_))

    override def sendPing(data: ByteBuffer): Unit = peerEndpoint.sendPong(data)

    override def sendPong(data: ByteBuffer): Unit = {}

    override def sendClose(): Unit = actor ! RuntimeConnector.Destroy
  }

  case class MessageFromRuntime(message: ApiEnvelope)
}
