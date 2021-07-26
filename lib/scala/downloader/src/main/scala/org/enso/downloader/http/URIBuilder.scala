package org.enso.downloader.http

import akka.http.scaladsl.model.Uri

/** A simple immutable builder for URIs based on URLs.
  *
  * It contains very limited functionality that is needed by the APIs used in
  * the launcher. It can be easily extended if necessary.
  */
case class URIBuilder private (uri: Uri) {

  /** Resolve a segment over the path in the URI.
    *
    * For example adding `bar` to `http://example.com/foo` will result in
    * `http://example.com/foo/bar`.
    */
  def addPathSegment(segment: String): URIBuilder = {
    val part = "/" + segment
    copy(uri.withPath(uri.path + part))
  }

  /** Add a query parameter to the URI.
    *
    * The query is appended at the end.
    */
  def addQuery(key: String, value: String): URIBuilder =
    copy(uri.withQuery(uri.query().+:((key, value))))

  /** Build the URI represented by this builder. */
  def build(): Uri = uri
}

object URIBuilder {

  /** Create a builder basing from a hostname.
    *
    * A builder created by `fromHost("example.com")` represents
    * `https://example.com/`.
    */
  def fromHost(host: String): URIBuilder =
    new URIBuilder(Uri.from(scheme = "https", host = host))

  /** Creates a builder from an arbitrary [[Uri]] instance. */
  def fromUri(uri: Uri): URIBuilder =
    new URIBuilder(uri)

  /** Creates a builder from an arbitrary URI represented as string.
    *
    * If the string is invalid, it throws
    * [[akka.http.scaladsl.model.IllegalUriException]].
    */
  def fromUri(uri: String): URIBuilder =
    new URIBuilder(Uri.parseAbsolute(uri))

  /** A simple DSL for the URIBuilder. */
  implicit class URIBuilderSyntax(builder: URIBuilder) {

    /** Extends the URI with an additional path segment. */
    def /(part: String): URIBuilder =
      builder.addPathSegment(part)

    /** Adds a query to the URI. */
    def ?(query: (String, String)): URIBuilder =
      builder.addQuery(query._1, query._2)
  }
}
