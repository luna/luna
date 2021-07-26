package org.enso.downloader.archive.internal

import java.io.InputStream

/** Represents a *mutable* progress status. */
trait ReadProgress {

  /** Specifies how many bytes have already been read.
    *
    * Querying this property over time may give different results as the task
    * progresses.
    */
  def alreadyRead(): Long

  /** Specifies how many bytes in total are expected, if known. */
  def total(): Option[Long]
}

/** A wrapper for an [[InputStream]] that tracks the read progresss.
  *
  * @param in the base stream to wrap
  * @param totalSize total amount of bytes that are expected to be available in
  *                  that stream
  * @param updated a callback that is called whenever progress is made
  */
class ProgressInputStream(
  in: InputStream,
  totalSize: Option[Long],
  updated: ReadProgress => Unit
) extends InputStream {
  private var bytesRead: Long = 0

  private val readProgress = new ReadProgress {
    override def alreadyRead(): Long   = bytesRead
    override def total(): Option[Long] = totalSize
  }

  /** Returns the [[ReadProgress]] instance that can be queried to check how
    * many bytes have been read already.
    */
  def progress: ReadProgress = readProgress

  /** @inheritdoc */
  override def available: Int =
    in.available()

  /** @inheritdoc */
  override def read: Int = {
    bytesRead += 1
    updated(readProgress)
    in.read()
  }

  /** @inheritdoc
    */
  override def read(b: Array[Byte]): Int = {
    val bytes = in.read(b)
    bytesRead += bytes
    updated(readProgress)
    bytes
  }

  /** @inheritdoc */
  override def read(b: Array[Byte], off: Int, len: Int): Int = {
    val bytes = in.read(b, off, len)
    bytesRead += bytes
    updated(readProgress)
    bytes
  }

  /** @inheritdoc */
  override def skip(n: Long): Long = {
    val skipped = in.skip(n)
    bytesRead += skipped
    updated(readProgress)
    skipped
  }

  /** @inheritdoc */
  override def close(): Unit =
    in.close()
}
