package com.github.mwegrz.scalautil.mqtt

import java.util.UUID

import akka.NotUsed
import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import akka.stream.alpakka.mqtt.streaming._
import akka.stream.alpakka.mqtt.streaming.scaladsl.{ ActorMqttClientSession, Mqtt }
import akka.stream.scaladsl.{ Flow, RestartFlow, Source, Tcp }
import akka.util.ByteString
import com.github.mwegrz.scalastructlog.KeyValueLogging
import com.typesafe.config.Config

import scala.collection.immutable.Iterable
import com.github.mwegrz.scalautil.javaDurationToDuration

import scala.concurrent.{ ExecutionContext, Promise }
import scala.util.Try

object MqttClient {
  def apply(config: Config)(
      implicit actorSystem: ActorSystem,
      actorMaterializer: ActorMaterializer,
      executionContext: ExecutionContext
  ): MqttClient =
    new DefaultMqttClient(config)

  object Qos {
    case object AtLeastOnce extends Qos {
      override def toControlPacketFlags: ControlPacketFlags =
        ControlPacketFlags.QoSAtLeastOnceDelivery
    }
    case object AtMostOnce extends Qos {
      override def toControlPacketFlags: ControlPacketFlags =
        ControlPacketFlags.QoSAtMostOnceDelivery
    }
    case object ExactlyOnce extends Qos {
      override def toControlPacketFlags: ControlPacketFlags =
        ControlPacketFlags.QoSExactlyOnceDelivery
    }
  }

  trait Qos {
    private[mqtt] def toControlPacketFlags: ControlPacketFlags
  }
}

trait MqttClient {
  import MqttClient._

  def createFlow[A, B](topics: Map[String, Qos], bufferSize: Int, qos: Qos)(
      toBinary: A => Array[Byte],
      fromBinary: Array[Byte] => B
  ): Flow[(String, A), (String, B), NotUsed]
}

class DefaultMqttClient private[mqtt] (config: Config)(
    implicit actorSystem: ActorSystem,
    actorMaterializer: ActorMaterializer,
    executionContext: ExecutionContext
) extends MqttClient
    with KeyValueLogging {
  import MqttClient._

  private val host = config.getString("host")
  private val port = config.getInt("port")
  private val username = config.getString("username")
  private val password = config.getString("password")
  private val clientId = if (config.hasPath("client-id")) config.getString("client-id") else ""
  private val restartPolicyMinBackoff = config.getDuration("restart-policy.min-backoff")
  private val restartPolicyMaxBackoff = config.getDuration("restart-policy.max-backoff")
  private val restartPolicyRandomFactor = config.getDouble("restart-policy.random-factor")
  private val restartPolicyMaxRestarts = config.getInt("restart-policy.max-restarts")

  override def createFlow[A, B](topics: Map[String, Qos], bufferSize: Int, qos: Qos)(
      toBinary: A => Array[Byte],
      fromBinary: Array[Byte] => B
  ): Flow[(String, A), (String, B), NotUsed] = {
    val connection = Tcp().outgoingConnection(host, port)
    val session = ActorMqttClientSession(MqttSessionSettings())
    val clientSessionFlow: Flow[Command[() => Unit], Either[MqttCodec.DecodeError, Event[() => Unit]], NotUsed] =
      Mqtt
        .clientSessionFlow(session)
        .join(connection)
    val connectCommand = Connect(
      if (clientId.isEmpty) UUID.randomUUID().toString else clientId,
      ConnectFlags.CleanSession,
      username,
      password
    )

    val flow = Flow[(String, A)]
      .mapAsyncUnordered(2) {
        case (topic, msg) =>
          val promise = Promise[None.type]()
          session ! Command(
            Publish(qos.toControlPacketFlags, topic, ByteString(toBinary(msg))),
            () => promise.complete(Try(None))
          )
          promise.future
      }
      .mapConcat(_ => Nil)
      .prepend(
        Source(
          if (topics.nonEmpty) {
            Iterable(
              Command[() => Unit](connectCommand),
              Command[() => Unit](Subscribe(topics.mapValues(_.toControlPacketFlags).toSeq))
            )
          } else {
            Iterable(Command[() => Unit](connectCommand))
          }
        )
      )
      .via(clientSessionFlow)
      .filter {
        case Right(Event(_: PubAck, Some(ack))) =>
          ack()
          false
        case _ => true
      }
      .collect {
        case Right(Event(p: Publish, _)) =>
          (p.topicName, fromBinary(p.payload.toArray))
      }

    RestartFlow.withBackoff(
      minBackoff = restartPolicyMinBackoff,
      maxBackoff = restartPolicyMaxBackoff,
      randomFactor = restartPolicyRandomFactor,
      maxRestarts = restartPolicyMaxRestarts
    ) { () =>
      flow
        .watchTermination() { (_, f) =>
          f.recover {
              case t: Throwable =>
                log.error("Flow encountered a failure and has been restarted", t)
            }
            .foreach { _ =>
              log.warning("Flow completed and has been restarted")
            }
        }
    }
  }
}
