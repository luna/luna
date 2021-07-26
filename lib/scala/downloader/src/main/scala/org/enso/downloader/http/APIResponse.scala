package org.enso.downloader.http

/** Contains the response contents as a string alongside with the headers
  * included in the response.
  *
  * @param content the response decoded as a string
  * @param headers sequence of headers included in the response
  * @param statusCode the response status code, indicating whether the request
  *                   has succeeded or failed
  */
case class APIResponse(content: String, headers: Seq[Header], statusCode: Int)
