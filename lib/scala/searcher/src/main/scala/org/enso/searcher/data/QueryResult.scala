package org.enso.searcher.data

/** A result of the query execution.
  *
  * @param ids the list of ids affected by the query
  * @param value the result value
  */
case class QueryResult[A](ids: Iterable[Long], value: A)
