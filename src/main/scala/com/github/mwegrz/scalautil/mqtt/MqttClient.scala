package com.github.mwegrz.scalautil.mqtt

import akka.{Done, NotUsed}
import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import akka.stream.alpakka.mqtt.scaladsl.{MqttFlow, MqttSink, MqttSource}
import akka.stream.alpakka.mqtt.{MqttConnectionSettings, MqttMessage, MqttQoS, MqttSourceSettings}
import akka.stream.scaladsl.{BidiFlow, Flow, RestartFlow, RestartSink, RestartSource, Sink, Source}
import akka.util.ByteString
import com.github.mwegrz.scalastructlog.KeyValueLogging
import com.typesafe.config.Config
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence
import com.github.mwegrz.scalautil.javaDurationToDuration

import scala.concurrent.Future

object MqttClient {
  def apply(config: Config)(implicit actorSystem: ActorSystem, actorMaterializer: ActorMaterializer): MqttClient =
    new DefaultMqttClient(config)
}

trait MqttClient {
  def source[A](topics: Map[String, MqttQoS], bufferSize: Int)(
      fromBinary: Array[Byte] => A): Source[(String, A), NotUsed]

  def sink[A](qos: MqttQoS)(toBinary: A => Array[Byte]): Sink[(String, A), NotUsed]

  def flow[A, B](topics: Map[String, MqttQoS], bufferSize: Int, qos: MqttQoS)(
      toBinary: A => Array[Byte],
      fromBinary: Array[Byte] => B): Flow[(String, A), (String, B), NotUsed]
}

class DefaultMqttClient private[mqtt] (config: Config)(implicit actorSystem: ActorSystem,
                                                       actorMaterializer: ActorMaterializer)
    extends MqttClient with KeyValueLogging {

  private val broker = config.getString("broker")
  private val username = config.getString("username")
  private val password = config.getString("password")
  private val clientId = config.getString("client-id")
  private val restartMinBackoff = config.getDuration("restart.min-backoff")
  private val restartMaxBackoff = config.getDuration("restart.max-backoff")
  private val restartRandomFactor = config.getDouble("restart.random-factor")

  private val connectionSettings =
    MqttConnectionSettings(broker, clientId, new MemoryPersistence)
      .withAuth(username, password)

  override def source[A](topics: Map[String, MqttQoS], bufferSize: Int)(
      fromBinary: Array[Byte] => A): Source[(String, A), NotUsed] = {
    val settings = MqttSourceSettings(connectionSettings, topics)
    val mqttSource = RestartSource.withBackoff(restartMinBackoff, restartMaxBackoff, restartRandomFactor) { () =>
      log.info("Starting source")
      MqttSource(settings, bufferSize)
    }
    mqttSource.map(m => (m.topic, fromBinary(m.payload.toArray)))
  }

  override def sink[A](qos: MqttQoS)(toBinary: A => Array[Byte]): Sink[(String, A), NotUsed] = {
    val mqttSink = RestartSink.withBackoff(restartMinBackoff, restartMaxBackoff, restartRandomFactor) { () =>
      log.info("Starting sink")
      MqttSink(connectionSettings, qos)
    }
    mqttSink.contramap { case (t, e) => MqttMessage(t, ByteString(toBinary(e))) }
  }

  override def flow[A, B](topics: Map[String, MqttQoS], bufferSize: Int, qos: MqttQoS)(
      toBinary: A => Array[Byte],
      fromBinary: Array[Byte] => B): Flow[(String, A), (String, B), NotUsed] = {
    val settings = MqttSourceSettings(connectionSettings, topics)
    val downlink = Flow[(String, A)].map { case (t, e) => MqttMessage(t, ByteString(toBinary(e))) }
    val uplink = Flow[MqttMessage].map(a => (a.topic, fromBinary(a.payload.toArray)))
    val mqttFlow = RestartFlow.withBackoff(restartMinBackoff, restartMaxBackoff, restartRandomFactor) { () =>
      log.info("Starting flow")
      MqttFlow(settings, bufferSize, qos)
    }

    BidiFlow
      .fromFlows(downlink, uplink)
      .join(mqttFlow)
  }
}
