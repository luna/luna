package org.enso.lockmanager

import akka.actor.{Actor, Props, Stash}
import org.enso.lockmanager.ActorToHandlerConnector.SetRequestHandler
import org.enso.lockmanager.client.RuntimeServerRequestHandler
import org.enso.polyglot.runtime.Runtime.Api

/** A helper Actor that forwards messages sent to it to a request handler.
  *
  * It is used so that the messages sent from the endpoint can have some actor
  * set as their sender, so that the lock manager service has a destination to
  * reply to. It then forwards any received messages to the endpoint.
  */
class ActorToHandlerConnector extends Actor with Stash {

  override def receive: Receive = initializingStage

  private def initializedStage(
    requestHandler: RuntimeServerRequestHandler
  ): Receive = { case response: Api.Response =>
    requestHandler.onResponseReceived(response)
  }

  private def initializingStage: Receive = {
    case SetRequestHandler(handler) =>
      unstashAll()
      context.become(initializedStage(handler))
    case _ =>
      stash()
  }

}

object ActorToHandlerConnector {
  def props(): Props = Props(
    new ActorToHandlerConnector
  )

  /** A message that must be sent to the [[ActorToHandlerConnector]] to
    * initialize it.
    */
  case class SetRequestHandler(
    runtimeServerRequestHandler: RuntimeServerRequestHandler
  )
}
