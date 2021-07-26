package org.enso.languageserver

import java.time.{Clock, Instant, ZoneId, ZoneOffset}

/** Test clock which time flow can be controlled.
  *
  * @param instant the initial point in time
  */
case class TestClock(var instant: Instant = Instant.EPOCH) extends Clock {

  private val UTC = ZoneOffset.UTC

  /** @inheritdoc */
  override def getZone: ZoneId = UTC

  /** @inheritdoc */
  override def withZone(zone: ZoneId): Clock =
    TestClock(instant)

  /** Move time forward by the specified amount of seconds.
    *
    * @param seconds the amount of seconds
    */
  def moveTimeForward(seconds: Long): Unit = {
    instant = instant.plusSeconds(seconds)
  }
}
