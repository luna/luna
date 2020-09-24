package org.enso.loggingservice.internal.server

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.HttpMethods._
import akka.http.scaladsl.model.ws.{
  BinaryMessage,
  Message,
  TextMessage,
  UpgradeToWebSocket
}
import akka.http.scaladsl.model.{HttpRequest, HttpResponse, Uri}
import akka.stream.scaladsl.{Flow, Sink, Source}
import com.typesafe.config.{ConfigFactory, ConfigValueFactory}
import io.circe.{parser, Error}
import org.enso.loggingservice.LogLevel
import org.enso.loggingservice.internal.BlockingConsumerMessageQueue
import org.enso.loggingservice.internal.protocol.WSLogMessage
import org.enso.loggingservice.internal.serviceconnection.Service
import org.enso.loggingservice.printers.Printer

import scala.annotation.nowarn
import scala.concurrent.Future
import scala.concurrent.duration.DurationInt

class Server(
  interface: String,
  port: Short,
  queue: BlockingConsumerMessageQueue,
  printers: Seq[Printer],
  logLevel: LogLevel
) extends Service {
  implicit private val system: ActorSystem = {
    val config = ConfigFactory.load()
    import scala.jdk.CollectionConverters._
    val loggers: java.lang.Iterable[String] =
      Seq("akka.event.Logging$StandardOutLogger").asJava
    config.withValue("akka.loggers", ConfigValueFactory.fromAnyRef(loggers))
    ActorSystem("logging-service-server", config)
  }

  def start(): Unit = {
    startWebSocketServer()
    startPrinters()
  }

  private var queueThread: Option[Thread] = None
  private def startPrinters(): Unit = {
    val thread = new Thread(() => runQueue())
    queueThread = Some(thread)
    thread.start()
  }

  private def runQueue(): Unit = {
    while (!Thread.currentThread().isInterrupted) {
      val message = queue.nextMessage()
      if (logLevel.shouldLog(message.logLevel)) {
        printers.foreach(_.print(message))
      }
    }
  }

  private def startWebSocketServer(): Unit = {
    val greeterWebSocketService =
      Flow.fromSinkAndSourceCoupled(messageProcessor, Source.never)

    val requestHandler: HttpRequest => HttpResponse = {
      case req @ HttpRequest(GET, Uri.Path("/"), _, _, _) =>
        req.header[UpgradeToWebSocket @nowarn] match {
          case Some(upgrade) => upgrade.handleMessages(greeterWebSocketService)
          case None =>
            HttpResponse(400, entity = "Not a valid websocket request!")
        }
      case r: HttpRequest =>
        r.discardEntityBytes() // important to drain incoming HTTP Entity stream
        HttpResponse(404, entity = "Unknown resource!")
    }

    binding = Some(
      Http().bindAndHandleSync(
        requestHandler,
        interface = interface,
        port      = port.toInt
      )
    )
  }

  private var binding: Option[Future[Http.ServerBinding]] = None

  private def messageProcessor =
    Sink.foreach[Message] {
      case tm: TextMessage =>
        System.err.println(s"Got message $tm")
        val rawMessage     = tm.textStream.fold("")(_ + _)
        val decodedMessage = rawMessage.map(decodeMessage)
        decodedMessage.runForeach {
          case Left(error)    => reportInvalidMessage(error)
          case Right(message) => queue.send(Right(message))
        }
      case bm: BinaryMessage =>
        reportInvalidMessage(
          new IllegalStateException("Unexpected binary message.")
        )
        bm.dataStream.runWith(Sink.ignore)
    }

  private def reportInvalidMessage(error: Throwable): Unit = {
    // TODO do this only once?
    System.err.println(s"Invalid message: $error.")
  }

  override def terminate(): Unit = {
    import system.dispatcher
    binding match {
      case Some(bindingFuture) =>
        scala.language.postfixOps
        bindingFuture
          .flatMap(_.terminate(hardDeadline = 2.seconds))
          .onComplete(_ => system.terminate())
      case None =>
        system.terminate()
    }

    queueThread match {
      case Some(thread) =>
        thread.interrupt()
        thread.join(100)
      case None =>
    }
  }

  private def decodeMessage(message: String): Either[Error, WSLogMessage] =
    parser.parse(message).flatMap(_.as[WSLogMessage])
}

object Server {
  def setup(
    interface: String,
    port: Short,
    queue: BlockingConsumerMessageQueue,
    printers: Seq[Printer],
    logLevel: LogLevel
  ): Server = {
    val server = new Server(interface, port, queue, printers, logLevel)
    try {
      server.start()
      server
    } catch {
      case e: Throwable =>
        server.terminate()
        throw e
    }
  }
}
