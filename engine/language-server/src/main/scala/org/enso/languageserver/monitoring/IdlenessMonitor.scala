package org.enso.languageserver.monitoring

import java.time.{Clock, Duration, Instant}

import akka.actor.{Actor, Props}
import org.enso.languageserver.util.UnhandledLogging

/** An actor that monitors the server time spent idle.
  *
  * @param clock the system clock
  */
class IdlenessMonitor(clock: Clock) extends Actor with UnhandledLogging {

  override def receive: Receive = initialized(clock.instant())

  private def initialized(lastActiveTime: Instant): Receive = {
    case MonitoringProtocol.ResetIdleTime =>
      context.become(initialized(clock.instant()))

    case MonitoringProtocol.GetIdleTime =>
      val idleTime = Duration.between(lastActiveTime, clock.instant())
      sender() ! MonitoringProtocol.IdleTime(idleTime.toSeconds)
  }

}

object IdlenessMonitor {

  /** Creates a configuration object used to create an idleness monitor.
    *
    * @return a configuration object
    */
  def props(clock: Clock): Props = Props(new IdlenessMonitor(clock))

}
