package org.enso.downloader.http

/** Represents a HTTP header. */
case class Header(name: String, value: String) {

  /** Checks if this header instance corresponds to a `headerName`.
    *
    * The check is case-insensitive.
    */
  def is(headerName: String): Boolean =
    name.toLowerCase == headerName.toLowerCase
}
