package org.enso.loggingservice

import org.enso.loggingservice.printers.TestPrinter

import scala.concurrent.Await
import scala.concurrent.duration.DurationInt

/** A helper object for handling logs in tests.
  */
object TestLogger {

  /** A log message returned by [[gatherLogs]].
    *
    * It contains the loglevel and message, but ignores attached exceptions.
    */
  case class TestLogMessage(logLevel: LogLevel, message: String)

  /** Gathers logs logged during execution of `action`.
    *
    * This method should be used only inside of tests. Any tests using it should
    * be ran with `parallelExecution` set to false, as global logger state has
    * to be modified to gather the logs.
    */
  def gatherLogs[R](action: => R): (R, Seq[TestLogMessage]) = {
    LoggingServiceManager.dropPendingLogs()
    if (LoggingServiceManager.isSetUp()) {
      throw new IllegalStateException(
        "gatherLogs called but another logging service has been already set " +
        "up, this would lead to conflicts"
      )
    }
    val printer = new TestPrinter
    val future = LoggingServiceManager.setup(
      LoggerMode.Local(Seq(printer)),
      LogLevel.Trace
    )
    Await.ready(future, 1.second)
    val result = action
    Thread.sleep(100)
    LoggingServiceManager.tearDown()
    (result, printer.getLoggedMessages)
  }

  /** Drops any logs that are pending due to the logging service not being set
    * up.
    *
    * This method should be used only inside of tests. Any tests using it should
    * be ran with `parallelExecution` set to false, as global logger state has
    * to be modified to gather the logs.
    */
  def dropLogs(): Unit = {
    LoggingServiceManager.dropPendingLogs()
  }
}
