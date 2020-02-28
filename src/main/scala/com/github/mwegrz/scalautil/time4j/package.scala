package com.github.mwegrz.scalautil

import java.time.Instant

import net.time4j.Moment
import net.time4j.scale.TimeScale

package object time4j {
  def gpsMillisecondsToInstant(gpsMilliseconds: Long): Instant = {
    Moment
      .of(
        (BigDecimal(gpsMilliseconds).setScale(3, BigDecimal.RoundingMode.HALF_UP) / 1000).toLong,
        Math.floorMod(gpsMilliseconds, 1000).toInt * 1000000,
        TimeScale.GPS
      )
      .toTemporalAccessor
  }

  def instantToGpsMilliseconds(instant: Instant): Long = {
    val moment = Moment.from(instant)
    val seconds = moment.getElapsedTime(TimeScale.GPS)
    val nanoseconds = moment.getNanosecond(TimeScale.GPS)
    seconds * 1000 + (BigDecimal(nanoseconds).setScale(6, BigDecimal.RoundingMode.HALF_UP) / 1000000).toLong
  }
}
