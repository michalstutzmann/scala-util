package com.github.mwegrz.scalautil.mqtt

import akka.NotUsed
import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import akka.stream.alpakka.mqtt.scaladsl.{ MqttFlow, MqttSink, MqttSource }
import akka.stream.alpakka.mqtt.{ MqttConnectionSettings, MqttMessage, MqttQoS, MqttSourceSettings }
import akka.stream.scaladsl.{ BidiFlow, Flow, RestartFlow, RestartSink, RestartSource, Sink, Source }
import akka.util.ByteString
import com.typesafe.config.Config
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence
import com.github.mwegrz.scalautil.javaDurationToDuration

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
    extends MqttClient {

  private val broker = config.getString("broker")
  private val username = config.getString("username")
  private val password = config.getString("password")
  private val clientId = config.getString("client-id")
  private val restartPolicyIdleTimeout = config.getDuration("restart-policy.idle-timeout")
  private val restartPolicyMinBackoff = config.getDuration("restart-policy.min-backoff")
  private val restartPolicyMaxBackoff = config.getDuration("restart-policy.max-backoff")
  private val restartPolicyRandomFactor = config.getDouble("restart-policy.random-factor")

  private val connectionSettings =
    MqttConnectionSettings(broker, clientId, new MemoryPersistence)
      .withAuth(username, password)
      .withCleanSession(cleanSession = true)

  override def source[A](topics: Map[String, MqttQoS], bufferSize: Int)(
      fromBinary: Array[Byte] => A): Source[(String, A), NotUsed] = {
    val settings = MqttSourceSettings(connectionSettings, topics)
    val mqttSource =
      RestartSource.withBackoff(restartPolicyMinBackoff, restartPolicyMaxBackoff, restartPolicyRandomFactor) { () =>
        MqttSource(settings, bufferSize).idleTimeout(restartPolicyIdleTimeout)
      }
    mqttSource.map(m => (m.topic, fromBinary(m.payload.toArray)))
  }

  override def sink[A](qos: MqttQoS)(toBinary: A => Array[Byte]): Sink[(String, A), NotUsed] = {
    val mqttSink =
      RestartSink.withBackoff(restartPolicyMinBackoff, restartPolicyMaxBackoff, restartPolicyRandomFactor) { () =>
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
    val mqttFlow =
      RestartFlow.withBackoff(restartPolicyMinBackoff, restartPolicyMaxBackoff, restartPolicyRandomFactor) { () =>
        MqttFlow(settings, bufferSize, qos).idleTimeout(restartPolicyIdleTimeout)
      }

    BidiFlow
      .fromFlows(downlink, uplink)
      .join(mqttFlow)
  }
}
