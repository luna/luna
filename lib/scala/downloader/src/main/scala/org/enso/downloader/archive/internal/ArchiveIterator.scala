package org.enso.downloader.archive.internal

import org.apache.commons.compress.archivers.{ArchiveEntry, ArchiveInputStream}

/** Wraps an [[ArchiveInputStream]] to get an [[Iterator]] which produces
  * non-null archive entries.
  */
case class ArchiveIterator(
  archiveInputStream: ArchiveInputStream
) extends Iterator[ArchiveEntry] {

  /** @inheritdoc
    */
  override def hasNext: Boolean = {
    findNext()
    nextEntry.isDefined
  }

  /** @inheritdoc
    */
  override def next(): ArchiveEntry = {
    findNext()
    nextEntry match {
      case Some(value) =>
        nextEntry = None
        value
      case None =>
        throw new NoSuchElementException("No more entries in the iterator.")
    }
  }

  private var nextEntry: Option[ArchiveEntry] = None
  private var finished: Boolean               = false

  /** Tries to move to the next entry. If it is `null` then it means the
    * [[archiveInputStream]] has run out of entries.
    */
  private def findNext(): Unit = {
    if (nextEntry.isEmpty && !finished) {
      val nextCandidate = archiveInputStream.getNextEntry
      if (nextCandidate == null) {
        finished = true
      } else {
        nextEntry = Some(nextCandidate)
      }
    }
  }
}
