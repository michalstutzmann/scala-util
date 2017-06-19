package com.github.mwegrz.scalautil.spray.json

import java.time.temporal.ChronoUnit
import java.time.{ ZonedDateTime, Duration, Instant, LocalDateTime }
import org.threeten.extra.Interval
import spray.json.{ JsNumber, JsString, JsValue, RootJsonFormat }

trait JavaTimeJsonProtocol {
  import JavaTimeJsonProtocol._

  implicit object DurationJsonFormat extends RootJsonFormat[Duration] {
    override def write(obj: Duration) =
      JsString(obj.toString)

    override def read(value: JsValue) = Duration.parse(stringValueToString(value))
  }

  implicit object InstantTimeJsonFormat extends RootJsonFormat[Instant] {
    override def write(obj: Instant) =
      JsString(obj.truncatedTo(ChronoUnit.MILLIS).toString)

    override def read(value: JsValue) = value match {
      case v: JsNumber => Instant.ofEpochMilli(v.value.longValue())
      case v: JsString => Instant.parse(stringValueToString(value))
      case _ => throw new IllegalArgumentException("Only Int and String values are supported")
    }
  }

  implicit object LocalDateTimeJsonFormat extends RootJsonFormat[LocalDateTime] {
    override def write(obj: LocalDateTime) =
      JsString(obj.toString)

    override def read(value: JsValue) = LocalDateTime.parse(stringValueToString(value))
  }

  implicit object ZonedDateTimeJsonFormat extends RootJsonFormat[ZonedDateTime] {
    override def write(obj: ZonedDateTime) =
      JsString(obj.toString)

    override def read(value: JsValue) = ZonedDateTime.parse(stringValueToString(value))
  }

  implicit object IntervalJsonFormat extends RootJsonFormat[Interval] {
    override def write(obj: Interval) =
      JsString(obj.toString)

    override def read(value: JsValue) = Interval.parse(stringValueToString(value))
  }
}

object JavaTimeJsonProtocol {
  def stringValueToString(value: JsValue) = value.toString().stripPrefix("\"").stripSuffix("\"")
}
