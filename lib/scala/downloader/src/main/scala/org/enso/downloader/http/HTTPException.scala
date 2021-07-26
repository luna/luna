package org.enso.downloader.http

/** Indicates an error when processing a HTTP request. */
class HTTPException(message: String) extends RuntimeException(message)

object HTTPException {

  /** A helper constructor for [[HTTPException]]. */
  def apply(message: String): HTTPException = new HTTPException(message)
}

/** Indicates that the HTTP request failed with 404 status. */
case class ResourceNotFound()
    extends HTTPException("The server has responded with 404 status.")
