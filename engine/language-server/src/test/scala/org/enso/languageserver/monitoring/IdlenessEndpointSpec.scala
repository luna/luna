package org.enso.languageserver.monitoring

import akka.actor.ActorRef
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.Directives
import akka.http.scaladsl.testkit.{RouteTestTimeout, ScalatestRouteTest}
import org.enso.languageserver.TestClock
import org.enso.testkit.FlakySpec
import org.scalatest.flatspec.AnyFlatSpecLike
import org.scalatest.matchers.should.Matchers

import scala.concurrent.duration._

class IdlenessEndpointSpec
    extends AnyFlatSpecLike
    with Matchers
    with FlakySpec
    with ScalatestRouteTest
    with Directives {

  implicit val timeout = RouteTestTimeout(25.seconds)

  "An idleness probe" should "reply with server idle time" in withEndpoint {
    (_, _, endpoint) =>
      Get("/_idle") ~> endpoint.route ~> check {
        status shouldEqual StatusCodes.OK
        responseAs[String] shouldEqual s"""{"idle_time_sec":0}"""
      }
  }

  it should "count idle time" in withEndpoint { (clock, _, endpoint) =>
    Get("/_idle") ~> endpoint.route ~> check {
      status shouldEqual StatusCodes.OK
      responseAs[String] shouldEqual s"""{"idle_time_sec":0}"""
    }

    val idleTimeSeconds = 1L
    clock.moveTimeForward(idleTimeSeconds)
    Get("/_idle") ~> endpoint.route ~> check {
      status shouldEqual StatusCodes.OK
      responseAs[String] shouldEqual s"""{"idle_time_sec":$idleTimeSeconds}"""
    }
  }

  it should "reset idle time" in withEndpoint { (clock, monitor, endpoint) =>
    Get("/_idle") ~> endpoint.route ~> check {
      status shouldEqual StatusCodes.OK
      responseAs[String] shouldEqual s"""{"idle_time_sec":0}"""
    }

    val idleTimeSeconds = 1L
    clock.moveTimeForward(idleTimeSeconds)
    Get("/_idle") ~> endpoint.route ~> check {
      status shouldEqual StatusCodes.OK
      responseAs[String] shouldEqual s"""{"idle_time_sec":$idleTimeSeconds}"""
    }

    monitor ! MonitoringProtocol.ResetIdleTime
    Get("/_idle") ~> endpoint.route ~> check {
      status shouldEqual StatusCodes.OK
      responseAs[String] shouldEqual s"""{"idle_time_sec":0}"""
    }
  }

  def withEndpoint(
    test: (TestClock, ActorRef, IdlenessEndpoint) => Any
  ): Unit = {
    val clock           = TestClock()
    val idlenessMonitor = system.actorOf(IdlenessMonitor.props(clock))
    val endpoint        = new IdlenessEndpoint(idlenessMonitor)

    test(clock, idlenessMonitor, endpoint)
  }

}
