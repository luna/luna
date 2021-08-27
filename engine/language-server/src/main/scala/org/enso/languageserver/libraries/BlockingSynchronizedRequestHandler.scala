package org.enso.languageserver.libraries

import akka.actor.{Actor, ActorRef, Stash, Status}
import akka.pattern.pipe

import scala.concurrent.Future
import scala.util.Try

/** Gathers common logic for actors that should process requests sequentially,
  * but they perform blocking operations so the request itself should be
  * performed in a separate execution context.
  */
trait BlockingSynchronizedRequestHandler extends Actor with Stash {

  override def receive: Receive = requestStage

  /** The implementation should override this stage to handle incoming requests.
    */
  def requestStage: Receive

  private def requestFinished(): Unit = {
    unstashAll()
    context.become(requestStage)
  }

  /** An internal type for successful request results. */
  case class Result[A](result: A)

  import context.dispatcher

  /** The [[requestStage]] can use this function to start the request processing
    * in the proper execution context.
    *
    * Other requests that will be coming while this is being processed will be
    * stashed, and they will be handled once this one finishes.
    */
  def startRequest[A](action: => Try[A]): Unit = {
    val future: Future[Result[A]] =
      BlockingOperation.run(action.get).map(Result(_))
    future pipeTo self
    context.become(processingStage(sender()))
  }

  private def processingStage(replyTo: ActorRef): Receive = {
    case Result(result) =>
      replyTo ! result
      requestFinished()

    case failure @ Status.Failure(_) =>
      replyTo ! failure
      requestFinished()

    case _ =>
      stash()
  }
}
