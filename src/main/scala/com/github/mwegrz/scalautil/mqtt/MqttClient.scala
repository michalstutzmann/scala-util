package com.github.mwegrz.scalautil.mqtt

import akka.{ Done, NotUsed }
import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import akka.stream.alpakka.mqtt.scaladsl.{ MqttFlow, MqttSink, MqttSource }
import akka.stream.alpakka.mqtt.{ MqttConnectionSettings, MqttMessage, MqttQoS, MqttSourceSettings }
import akka.stream.scaladsl.{ Flow, Sink, Source, BidiFlow }
import akka.util.ByteString
import com.typesafe.config.Config
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence

import scala.concurrent.Future

object MqttClient {
  def apply(config: Config)(implicit actorSystem: ActorSystem, actorMaterializer: ActorMaterializer): MqttClient =
    new DefaultMqttClient(config)
}

trait MqttClient {
  def source[A](topics: Map[String, MqttQoS], bufferSize: Int)(
      fromBinary: Array[Byte] => A): Source[(String, A), Future[Done]]

  def sink[A](qos: MqttQoS)(toBinary: A => Array[Byte]): Sink[(String, A), Future[Done]]

  def flow[A, B](topics: Map[String, MqttQoS], bufferSize: Int, qos: MqttQoS)(
      toBinary: A => Array[Byte],
      fromBinary: Array[Byte] => B): Flow[(String, A), (String, B), NotUsed]
}

class DefaultMqttClient private[mqtt] (config: Config)(implicit actorSystem: ActorSystem,
                                                       actorMaterializer: ActorMaterializer)
    extends MqttClient {

  private val broker = config.getString("broker")
  private val clientId = config.getString("clientId")

  private val connectionSettings = MqttConnectionSettings(broker, clientId, new MemoryPersistence)

  override def source[A](topics: Map[String, MqttQoS], bufferSize: Int)(
      fromBinary: Array[Byte] => A): Source[(String, A), Future[Done]] = {
    val settings = MqttSourceSettings(connectionSettings, topics)
    MqttSource(settings, bufferSize).map(m => (m.topic, fromBinary(m.payload.toArray)))
  }

  override def sink[A](qos: MqttQoS)(toBinary: A => Array[Byte]): Sink[(String, A), Future[Done]] =
    MqttSink(connectionSettings, qos).contramap { case (t, e) => MqttMessage(t, ByteString(toBinary(e))) }

  override def flow[A, B](topics: Map[String, MqttQoS], bufferSize: Int, qos: MqttQoS)(
      toBinary: A => Array[Byte],
      fromBinary: Array[Byte] => B): Flow[(String, A), (String, B), NotUsed] = {
    val settings = MqttSourceSettings(connectionSettings, topics)
    val downlink = Flow[(String, A)].map { case (t, e) => MqttMessage(t, ByteString(toBinary(e))) }
    val uplink = Flow[MqttMessage].map(a => (a.topic, fromBinary(a.payload.toArray)))

    BidiFlow
      .fromFlows(downlink, uplink)
      .join(MqttFlow(settings, bufferSize, qos))
  }
}
