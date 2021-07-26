package org.enso.downloader.http

import akka.http.scaladsl.model.HttpHeader.ParsingResult
import akka.http.scaladsl.model._
import org.enso.downloader.http

/** A simple immutable builder for HTTP requests.
  *
  * It contains very limited functionality that is needed by the APIs used in
  * the launcher. It can be easily extended if necessary.
  */
case class HTTPRequestBuilder private (
  uri: Uri,
  headers: Vector[(String, String)]
) {

  /** Builds a GET request with the specified settings.
    */
  def GET: HTTPRequest = build(HttpMethods.GET)

  /** Adds an additional header that will be included in the request.
    *
    * @param name name of the header
    * @param value the header value
    */
  def addHeader(name: String, value: String): HTTPRequestBuilder =
    copy(headers = headers.appended((name, value)))

  private def build(
    method: HttpMethod
  ): HTTPRequest = {
    val httpHeaders = headers.map { case (name, value) =>
      HttpHeader.parse(name, value) match {
        case ParsingResult.Ok(header, errors) if errors.isEmpty =>
          header
        case havingErrors =>
          throw new IllegalStateException(
            s"Internal error: " +
            s"Invalid value for header $name: ${havingErrors.errors}."
          )
      }
    }
    http.HTTPRequest(
      HttpRequest(method = method, uri = uri, headers = httpHeaders)
    )
  }
}

object HTTPRequestBuilder {

  /** Creates a request builder that will send the request for the given URI.
    */
  def fromURI(uri: Uri): HTTPRequestBuilder =
    new HTTPRequestBuilder(uri, Vector.empty)

  /** Tries to parse the URI provided as a [[String]] and returns a request
    * builder that will send the request to the given `uri`.
    */
  def fromURIString(uri: String): HTTPRequestBuilder =
    fromURI(URIBuilder.fromUri(uri).build())
}
