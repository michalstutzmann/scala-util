package com.github.mwegrz.scalautil.disruptivetechnologies

import java.time.Instant

import cats.syntax.functor._
import io.circe.{ Decoder, HCursor }
import io.circe.generic.auto._
import io.circe.generic.extras.semiauto._
import com.github.mwegrz.scalautil.circe.codecs._

object Event {
  object EventType {
    case object `touch` extends EventType
    case object `temperature` extends EventType
    case object `objectPresent` extends EventType
    case object `humidity` extends EventType
    case object `objectPresentCount` extends EventType
    case object `touchCount` extends EventType
    case object `waterPresent` extends EventType
    case object `batteryStatus` extends EventType
    case object `networkStatus` extends EventType
    case object `labelsChanged` extends EventType
    case object `connectionStatus` extends EventType
    case object `ethernetStatus` extends EventType
    case object `cellularStatus` extends EventType

    implicit val decoder: Decoder[EventType] = deriveEnumerationDecoder[EventType]
  }

  sealed trait EventType

  implicit val circeDecoder: Decoder[Event] = (c: HCursor) =>
    for {
      eventId <- c.downField("eventId").as[String]
      targetName <- c.downField("targetName").as[String]
      eventType <- c.downField("eventType").as[EventType]
      data <- c.downField("data").downField(eventType.toString).as[Data](Data.circeDecoder(eventType))
    } yield {
      Event(eventId, targetName, data)
    }

  private def extractProjectAndDeviceId(targetName: String): (ProjectId, DeviceId) = {
    val regex = "projects/(.+)/devices/(.+)".r("project-id", "device-id")
    val result = regex.findFirstMatchIn(targetName).get
    (ProjectId(result.group("project-id")), DeviceId(result.group("device-id")))
  }
}

final case class Event(eventId: String, targetName: String, data: Data) {
  import Event._

  lazy val (projectId, deviceId) = extractProjectAndDeviceId(targetName)
}

object Data {
  def circeDecoder(eventType: Event.EventType): Decoder[Data] =
    eventType match {
      case Event.EventType.`touch`              => Decoder[Touch].widen
      case Event.EventType.`temperature`        => Decoder[Temperature].widen
      case Event.EventType.`objectPresent`      => Decoder[ObjectPresent].widen
      case Event.EventType.`humidity`           => Decoder[Humidity].widen
      case Event.EventType.`objectPresentCount` => Decoder[ObjectPresentCount].widen
      case Event.EventType.`touchCount`         => Decoder[TouchCount].widen
      case Event.EventType.`waterPresent`       => Decoder[WaterPresent].widen
      case Event.EventType.`batteryStatus`      => Decoder[BatteryStatus].widen
      case Event.EventType.`networkStatus`      => Decoder[NetworkStatus].widen
      case Event.EventType.`labelsChanged`      => Decoder[LabelsChanged].widen
      case Event.EventType.`connectionStatus`   => Decoder[ConnectionStatus].widen
      case Event.EventType.`ethernetStatus`     => Decoder[EthernetStatus].widen
      case Event.EventType.`cellularStatus`     => Decoder[CellularStatus].widen
    }
}

sealed trait Data

final case class Touch(updateTime: Instant) extends Data

final case class Temperature(value: com.github.mwegrz.scalautil.quantities.Temperature, updateTime: Instant)
    extends Data

final case class ObjectPresent(state: State, updateTime: Instant) extends Data

final case class Humidity(
    temperature: com.github.mwegrz.scalautil.quantities.Temperature,
    relativeHumidity: com.github.mwegrz.scalautil.quantities.Humidity,
    updateTime: Instant
) extends Data

final case class ObjectPresentCount(total: Int, updateTime: Instant) extends Data

final case class TouchCount(total: Int, updateTime: Instant) extends Data

final case class WaterPresent(state: State, updateTime: Instant) extends Data

final case class BatteryStatus(percentage: Double, updateTime: Instant) extends Data

object NetworkStatus {
  final case class CloudConnector(id: String, signalStrength: Double)

  object TransmissionMode {
    case object LOW_POWER_STANDARD_MODE extends TransmissionMode
    case object HIGH_POWER_BOOST_MODE extends TransmissionMode

    implicit val decoder: Decoder[TransmissionMode] = deriveEnumerationDecoder[TransmissionMode]
  }

  sealed trait TransmissionMode
}

final case class NetworkStatus(
    signalStrength: Double,
    updateTime: Instant,
    cloudConnectors: List[NetworkStatus.CloudConnector],
    transmissionMode: NetworkStatus.TransmissionMode
) extends Data

object ConnectionStatus {
  object Connection {
    case object CELLULAR extends Connection
    case object ETHERNET extends Connection

    implicit val decoder: Decoder[Connection] = deriveEnumerationDecoder[Connection]
  }

  sealed trait Connection
}

final case class ConnectionStatus(
    connection: ConnectionStatus.Connection,
    available: List[ConnectionStatus.Connection],
    updateTime: Instant
) extends Data

final case class EthernetStatus(
    macAddress: String,
    ipAddress: String,
    errors: List[String],
    updateTime: Instant
) extends Data

final case class CellularStatus(
    signalStrength: Double,
    errors: List[String],
    updateTime: Instant
) extends Data

final case class LabelsChanged(
    added: List[Label],
    modified: List[Label],
    removed: List[Label],
    updateTime: Instant
) extends Data
