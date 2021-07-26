package org.enso.downloader.http

import akka.http.scaladsl.model.HttpRequest

/** Wraps an underlying HTTP request implementation to make the outside API
  * independent of the internal implementation.
  */
case class HTTPRequest(requestImpl: HttpRequest)
