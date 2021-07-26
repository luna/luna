package org.enso.libraryupload.auth

import org.enso.downloader.http.HTTPRequestBuilder

/** Represents an authentication method that can be used to authenticate
  * requests to the library repository.
  */
trait Token {

  /** Alters the request adding any properties (like headers) necessary to
    * successfully authenticate.
    */
  def alterRequest(request: HTTPRequestBuilder): HTTPRequestBuilder
}

/** A simple authentication method that adds an `Auth-Token` header to the
  * request.
  */
case class SimpleHeaderToken(value: String) extends Token {

  /** @inheritdoc */
  override def alterRequest(request: HTTPRequestBuilder): HTTPRequestBuilder =
    request.addHeader("Auth-Token", value)
}

/** A dummy authentication method that does not do anything.
  *
  * It can be used for servers that do not require any authentication.
  */
case object NoAuthorization extends Token {

  /** @inheritdoc */
  override def alterRequest(request: HTTPRequestBuilder): HTTPRequestBuilder =
    request
}
