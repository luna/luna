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
  headers: Vector[(String, String)],
  httpEntity: RequestEntity
) {

  /** Builds a GET request with the specified settings. */
  def GET: HTTPRequest = build(HttpMethods.GET)

  /** Builds a POST request with the specified settings. */
  def POST: HTTPRequest = build(HttpMethods.POST)

  /** Adds an additional header that will be included in the request.
    *
    * @param name name of the header
    * @param value the header value
    */
  def addHeader(name: String, value: String): HTTPRequestBuilder =
    copy(headers = headers.appended((name, value)))

  /** Sets the [[RequestEntity]] for the request.
    *
    * It can be used for example to specify form data to send for a POST
    * request.
    */
  def setEntity(entity: RequestEntity): HTTPRequestBuilder =
    copy(httpEntity = entity)

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
      HttpRequest(
        method  = method,
        uri     = uri,
        headers = httpHeaders,
        entity  = httpEntity
      )
    )
  }
}

object HTTPRequestBuilder {

  /** Creates a request builder that will send the request for the given URI.
    */
  def fromURI(uri: Uri): HTTPRequestBuilder =
    new HTTPRequestBuilder(uri, Vector.empty, HttpEntity.Empty)

  /** Tries to parse the URI provided as a [[String]] and returns a request
    * builder that will send the request to the given `uri`.
    */
  def fromURIString(uri: String): HTTPRequestBuilder =
    fromURI(URIBuilder.fromUri(uri).build())
}
