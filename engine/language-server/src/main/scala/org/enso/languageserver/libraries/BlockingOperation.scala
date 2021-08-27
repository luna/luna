package org.enso.languageserver.libraries

import java.util.concurrent.Executors
import scala.concurrent.{ExecutionContext, Future}

/** A helper for running blocking operations in a separate execution context, so
  * that they do not starve the Actor System.
  */
object BlockingOperation {

  /** Runs the operation in a separate execution context, returning a Future
    * that completes once the operation is done.
    */
  def run[A](operation: => A): Future[A] =
    Future(operation)(blockingExecutionContext)

  private lazy val blockingExecutionContext =
    ExecutionContext.fromExecutor(Executors.newCachedThreadPool())
}
