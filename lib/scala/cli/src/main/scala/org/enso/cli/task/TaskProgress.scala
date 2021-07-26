package org.enso.cli.task

import java.util.concurrent.LinkedTransferQueue

import scala.util.{Failure, Try}

/** Represents a long-running background task.
  */
trait TaskProgress[A] {

  /** Adds a progress listener to this task.
    *
    * Even if the task is already finished, the [[ProgressListener.done]]
    * method should be fired with the result. This way, `done` is fired
    * exactly once for each attached listener. There are no guarantees on how
    * often [[ProgressListener.progressUpdate]] is called.
    */
  def addProgressListener(listener: ProgressListener[A]): Unit

  /** Waits for the task to finish and returns its value, throwing any
    * exceptions that were reported.
    */
  def force(): A = TaskProgress.waitForTask(this).get

  /** Specifies unit associated with progress of this task. */
  def unit: ProgressUnit = ProgressUnit.Unspecified

  /** Alters the task by transforming its result with a function `f` that may
    * fail.
    *
    * The progress of applying `f` is not monitored - it is meant for functions
    * that do not take a very long time in comparison with the base task.
    *
    * @param f the function that can transform the original result
    * @tparam B the type that `f` returns wrapped in a [[Try]]
    * @return a new [[TaskProgress]] that will succeed if the original one
    *         succeeded and the transformation succeeded too
    */
  def flatMap[B](f: A => Try[B]): TaskProgress[B] =
    new MappedTask(this, f)

  /** Alters the task by transforming its result with a function `f`.
    *
    * The progress of applying `f` is not monitored - it is meant for functions
    * that do not take a very long time in comparison with the base task.
    *
    * If an exception is thrown by `f`, the altered task returns a [[Failure]]
    * with that exception.
    *
    * @param f the function that can transform the original result
    * @tparam B resulting type of `f`
    * @return a new [[TaskProgress]] that will succeed if the original one
    *         succeeded and the transformation succeeded too
    */
  def map[B](f: A => B): TaskProgress[B] = flatMap(a => Try(f(a)))
}

object TaskProgress {

  /** Creates a task that fails immediately.
    *
    * Useful for reporting early errors (before a background thread has even
    * been started for the task).
    *
    * @param throwable the error to complete the task with
    * @tparam A type of the task that is failed
    */
  def immediateFailure[A](throwable: Throwable): TaskProgress[A] =
    new TaskProgress[A] {
      override def addProgressListener(
        listener: ProgressListener[A]
      ): Unit = {
        listener.done(Failure(throwable))
      }
    }

  /** Performs action immediately and returns its result or an exception if one
    * was thrown when performing it.
    *
    * Should be used only for actions that complete quickly and are too simple
    * to report progress.
    */
  def runImmediately[A](action: => A): TaskProgress[A] =
    fromTry(Try(action))

  /** Lifts an already computed [[Try]] instance to [[TaskProgress]].
    *
    * Should be used only for actions that complete quickly and are too simple
    * to report progress.
    */
  def fromTry[A](action: Try[A]): TaskProgress[A] = {
    new TaskProgress[A] {
      override def addProgressListener(
        listener: ProgressListener[A]
      ): Unit = {
        listener.done(action)
      }
    }
  }

  /** Blocks and waits for the task to complete.
    */
  def waitForTask[A](task: TaskProgress[A]): Try[A] = {
    val queue = new LinkedTransferQueue[Try[A]]()
    task.addProgressListener(new ProgressListener[A] {
      override def progressUpdate(done: Long, total: Option[Long]): Unit = {}
      override def done(result: Try[A]): Unit =
        queue.put(result)
    })

    queue.take()
  }
}
