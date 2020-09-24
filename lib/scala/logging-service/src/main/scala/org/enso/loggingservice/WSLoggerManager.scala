package org.enso.loggingservice

import java.util.concurrent.LinkedTransferQueue

import org.enso.loggingservice.internal.serviceconnection.{Fallback, Service}
import org.enso.loggingservice.internal.{
  InternalLogMessage,
  Level,
  LoggerConnection
}

import scala.concurrent.Future

object WSLoggerManager {

  val maxQueueSizeForFallback: Int = 1000
  private val messageQueue         = new LinkedTransferQueue[InternalLogMessage]()
  private var currentLevel         = Level.Trace // TODO configurable
  object Connection extends LoggerConnection {
    override def send(message: InternalLogMessage): Unit = addMessage(message)
    override def logLevel: Level                         = currentLevel
  }

  private var currentService: Option[Service] = None

  private def addMessage(message: InternalLogMessage): Unit = {
    if (
      currentService.isEmpty && messageQueue.size() > maxQueueSizeForFallback
    ) {
      startFallbackStderrLogger()
    }
    messageQueue.add(message)
  }

  /**
    * Sets up the logging service, but in a separate thread to avoid stalling
    * the application.
    */
  def setup(mode: WSLoggerMode): Future[Unit] = {
    import scala.concurrent.ExecutionContext.Implicits.global
    Future(doSetup(mode))
  }

  private def doSetup(mode: WSLoggerMode): Unit = {
    currentService.synchronized {
      stopPriorSessions()
      mode match {
        case WSLoggerMode.Client(ip, port)           =>
        case WSLoggerMode.Server(port, host, config) =>
        case WSLoggerMode.Local(config)              => setUpFallback(config)
      }
    }
  }

  private def stopPriorSessions(): Unit = {
    currentService match {
      case Some(fallback: Fallback) =>
        fallback.terminate()
      case Some(_) =>
        throw new IllegalStateException("The system was already initialized.")
      case None =>
    }
  }

  /**
    * Starts the fallback as long as no other service has been initialized.
    */
  private def startFallbackStderrLogger(): Unit =
    currentService.synchronized {
      currentService match {
        case Some(_) =>
        case None    => setUpFallback(LoggingConfig.Default)
      }
    }

  private def setUpFallback(config: LoggingConfig): Unit = {
    currentService = Some(Fallback.setup(config, messageQueue))
  }
}
