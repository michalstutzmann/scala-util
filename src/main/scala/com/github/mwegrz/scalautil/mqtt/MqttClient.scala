package com.github.mwegrz.scalautil.mqtt

import java.util.UUID

import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import akka.stream.alpakka.mqtt.streaming._
import akka.stream.alpakka.mqtt.streaming.scaladsl.{ ActorMqttClientSession, Mqtt }
import akka.stream.scaladsl.Tcp.OutgoingConnection
import akka.stream.scaladsl.{ Flow, Keep, Source, Tcp }
import akka.util.ByteString
import com.github.mwegrz.scalastructlog.KeyValueLogging
import com.typesafe.config.Config

import scala.collection.immutable.Iterable
import scala.concurrent.{ ExecutionContext, Future, Promise }
import scala.util.{ Failure, Success, Try }

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

  final case class Connected(outgoingConnection: OutgoingConnection)
}

trait MqttClient {
  import MqttClient._

  def createFlow[A, B](topics: Map[String, Qos], bufferSize: Int, qos: Qos)(
      toBinary: A => Array[Byte],
      fromBinary: Array[Byte] => B
  ): Flow[(String, A), (String, B), Future[Connected]]
}

class DefaultMqttClient private[mqtt] (config: Config)(
    implicit actorSystem: ActorSystem,
    actorMaterializer: ActorMaterializer,
    executionContext: ExecutionContext
) extends MqttClient
    with KeyValueLogging {
  import MqttClient._
  private val availableProcessors = Runtime.getRuntime.availableProcessors

  private val host = config.getString("host")
  private val port = config.getInt("port")
  private val username = config.getString("username")
  private val password = config.getString("password")
  private val clientId = if (config.hasPath("client-id")) config.getString("client-id") else ""
  private val parallelism = if (config.hasPath("parallelism")) config.getInt("parallelism") else availableProcessors

  override def createFlow[A, B](topics: Map[String, Qos], bufferSize: Int, qos: Qos)(
      toBinary: A => Array[Byte],
      fromBinary: Array[Byte] => B
  ): Flow[(String, A), (String, B), Future[Connected]] = {
    val connection = Tcp().outgoingConnection(host, port)
    val session = ActorMqttClientSession(MqttSessionSettings())
    val uuid = UUID.randomUUID()
    val clientSessionFlow: Flow[Command[() => Unit], Either[MqttCodec.DecodeError, Event[() => Unit]], Future[
      Tcp.OutgoingConnection
    ]] =
      Mqtt
        .clientSessionFlow(session, ByteString(uuid.toString))
        .joinMat(connection)(Keep.right)
    val connectCommand = Connect(
      if (clientId.isEmpty) uuid.toString else clientId,
      ConnectFlags.CleanSession,
      username,
      password
    )
    val connAckPromise = Promise[Unit]
    val subAckPromise = if (topics.nonEmpty) Promise[Unit] else Promise.successful(())

    Flow[(String, A)]
      .mapAsync(parallelism) {
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
              Command[() => Unit](
                Subscribe(topics.map { case (name, qos) => (name, qos.toControlPacketFlags) }.toSeq)
              )
            )
          } else {
            Iterable(Command[() => Unit](connectCommand))
          }
        )
      )
      .viaMat(clientSessionFlow)(Keep.right)
      .filter {
        case Right(Event(_: ConnAck, _)) =>
          connAckPromise.complete(Success(()))
          false
        case Right(Event(_: SubAck, _)) if topics.nonEmpty =>
          subAckPromise.complete(Success(()))
          false
        case Right(Event(_: PubAck, Some(ack))) =>
          ack()
          false
        case _ => true
      }
      .collect {
        case Right(Event(p: Publish, _)) =>
          (p.topicName, fromBinary(p.payload.toArray))
      }
      .watchTermination() { (outgoingConnection, f) =>
        f.onComplete {
          case Success(_) =>
            session.shutdown()
            log.debug("Flow completed")
          case Failure(exception) =>
            session.shutdown()
            throw log.error("Flow failed", exception)
        }

        for {
          value <- outgoingConnection
          _ <- connAckPromise.future
          _ <- subAckPromise.future
        } yield {
          log.debug("Flow created")
          Connected(value)
        }
      }
  }
}
