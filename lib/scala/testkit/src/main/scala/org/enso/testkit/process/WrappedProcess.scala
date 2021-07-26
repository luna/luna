package org.enso.testkit.process

import java.io._
import java.util.concurrent.{Semaphore, TimeUnit}
import scala.collection.Factory
import scala.concurrent.TimeoutException
import scala.jdk.CollectionConverters._
import scala.jdk.StreamConverters._

/** Represents a started and possibly running process. */
class WrappedProcess(command: Seq[String], process: Process) {

  private val outQueue =
    new java.util.concurrent.LinkedTransferQueue[String]()
  private val errQueue =
    new java.util.concurrent.LinkedTransferQueue[String]()

  sealed trait StreamType
  case object StdErr extends StreamType
  case object StdOut extends StreamType
  @volatile private var ioHandlers: Seq[(String, StreamType) => Unit] = Seq()

  private def watchStream(
    stream: InputStream,
    streamType: StreamType
  ): Unit = {
    val reader       = new BufferedReader(new InputStreamReader(stream))
    var line: String = null
    val queue = streamType match {
      case StdErr => errQueue
      case StdOut => outQueue
    }
    try {
      while ({ line = reader.readLine(); line != null }) {
        queue.add(line)
        ioHandlers.foreach(f => f(line, streamType))
      }
    } catch {
      case _: InterruptedException =>
      case _: IOException =>
        ioHandlers.foreach(f => f("<Unexpected EOF>", streamType))
    }
  }

  private val outThread = new Thread(() =>
    watchStream(process.getInputStream, StdOut)
  )
  private val errThread = new Thread(() =>
    watchStream(process.getErrorStream, StdErr)
  )
  outThread.start()
  errThread.start()

  /** Waits for a message on the stderr to appear. */
  def waitForMessageOnErrorStream(
    message: String,
    timeoutSeconds: Long
  ): Unit = waitForMessage(message, timeoutSeconds, StdErr)

  /** Waits for a message on one of the output streams. */
  def waitForMessage(
    message: String,
    timeoutSeconds: Long,
    stream: StreamType
  ): Unit = {
    val semaphore = new Semaphore(0)
    def handler(line: String, streamType: StreamType): Unit = {
      if (streamType == stream && line.contains(message)) {
        semaphore.release()
      }
    }

    this.synchronized {
      ioHandlers ++= Seq(handler _)
    }

    stream match {
      case StdErr =>
        errQueue.asScala.toSeq.foreach(handler(_, StdErr))
      case StdOut =>
        outQueue.asScala.toSeq.foreach(handler(_, StdOut))
    }

    val acquired = semaphore.tryAcquire(timeoutSeconds, TimeUnit.SECONDS)
    if (!acquired) {
      throw new TimeoutException(s"Waiting for `$message` timed out.")
    }
  }

  private lazy val inputWriter = new PrintWriter(process.getOutputStream)

  /** Prints a message to the standard input stream of the process.
    *
    * Does not append any newlines, so if a newline is expected, it has to be
    * contained within the `message`.
    */
  def sendToInputStream(message: String): Unit = {
    inputWriter.print(message)
    inputWriter.flush()
  }

  /** Starts printing the stdout and stderr of the started process to the
    * stdout with prefixes to indicate that these messages come from another
    * process.
    *
    * It also prints lines that were printed before invoking this method.
    * Thus, it is possible that a line may be printed twice (once as
    * 'before-printIO' and once normally).
    */
  def printIO(): Unit = {
    def handler(line: String, streamType: StreamType): Unit = {
      val prefix = streamType match {
        case StdErr => "stderr> "
        case StdOut => "stdout> "
      }
      println(prefix + line)
    }
    this.synchronized {
      ioHandlers ++= Seq(handler _)
    }
    outQueue.asScala.toSeq.foreach(line =>
      println(s"stdout-before-printIO> $line")
    )
    errQueue.asScala.toSeq.foreach(line =>
      println(s"stderr-before-printIO> $line")
    )
  }

  /** Checks if the process is still running. */
  def isAlive: Boolean = process.isAlive

  /** Tries to kill the process immediately. */
  def kill(killDescendants: Boolean = false): Unit = {
    process.destroyForcibly()
    if (killDescendants) {
      for (processHandle <- findDescendants()) {
        processHandle.destroyForcibly()
      }
    }
  }

  private def findDescendants(): Seq[ProcessHandle] =
    process.descendants().toScala(Factory.arrayFactory).toSeq

  /** Waits for the process to finish and returns its [[RunResult]].
    *
    * If `waitForDescendants` is set, tries to wait for descendants of the
    * launched process to finish too. Especially important on Windows where
    * child processes may run after the launcher parent has been terminated.
    *
    * It will timeout after `timeoutSeconds` and try to kill the process (or
    * its descendants), although it may not always be able to.
    */
  def join(
    waitForDescendants: Boolean = true,
    timeoutSeconds: Long        = 15
  ): RunResult = {
    var descendants: Seq[ProcessHandle] = Seq()
    try {
      val exitCode =
        if (process.waitFor(timeoutSeconds, TimeUnit.SECONDS))
          process.exitValue()
        else throw new TimeoutException("Process timed out")
      if (waitForDescendants) {
        descendants = findDescendants()
        descendants.foreach(_.onExit().get(timeoutSeconds, TimeUnit.SECONDS))
      }
      errThread.join(1000)
      outThread.join(1000)
      if (errThread.isAlive) {
        errThread.interrupt()
      }
      if (outThread.isAlive) {
        outThread.interrupt()
      }
      val stdout = outQueue.asScala.toSeq.mkString("\n")
      val stderr = errQueue.asScala.toSeq.mkString("\n")
      RunResult(exitCode, stdout, stderr)
    } catch {
      case e @ (_: InterruptedException | _: TimeoutException) =>
        if (process.isAlive) {
          println(s"Killing the timed-out process: ${command.mkString(" ")}")
          process.destroyForcibly()
        }
        for (processHandle <- descendants) {
          if (processHandle.isAlive) {
            processHandle.destroyForcibly()
          }
        }
        throw e
    }
  }
}
