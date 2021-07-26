package org.enso.languageserver.monitoring

import akka.actor.ActorRef
import akka.http.scaladsl.model.{
  ContentTypes,
  HttpEntity,
  MessageEntity,
  StatusCodes
}
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import akka.pattern.ask
import akka.util.Timeout
import com.typesafe.scalalogging.LazyLogging
import org.enso.jsonrpc._

import scala.concurrent.duration._
import scala.util.{Failure, Success}

/** HTTP endpoint that provides idleness capabilities.
  *
  * @param idlenessMonitor an actor monitoring the server idle time
  */
class IdlenessEndpoint(
  idlenessMonitor: ActorRef
) extends Endpoint
    with LazyLogging {

  implicit private val timeout: Timeout = Timeout(10.seconds)

  /** @inheritdoc */
  override def route: Route =
    idlenessProbe

  private val idlenessProbe =
    path("_idle") {
      get {
        checkIdleness()
      }
    }

  private def checkIdleness(): Route = {
    val future = idlenessMonitor ? MonitoringProtocol.GetIdleTime

    onComplete(future) {
      case Failure(_) =>
        complete(StatusCodes.InternalServerError)
      case Success(r: MonitoringProtocol.IdleTime) =>
        complete(IdlenessEndpoint.toHttpEntity(r))
      case Success(r) =>
        logger.error("Unexpected response from idleness monitor: [{}]", r)
        complete(StatusCodes.InternalServerError)
    }
  }
}

object IdlenessEndpoint {

  private def toJsonText(t: MonitoringProtocol.IdleTime): String =
    s"""{"idle_time_sec":${t.idleTimeSeconds}}"""

  def toHttpEntity(t: MonitoringProtocol.IdleTime): MessageEntity =
    HttpEntity(ContentTypes.`application/json`, toJsonText(t))
}
