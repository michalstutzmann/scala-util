package com.github.mwegrz.scalautil.mqtt

import java.util.UUID

import akka.NotUsed
import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import akka.stream.alpakka.mqtt.scaladsl.MqttFlow
import akka.stream.alpakka.mqtt.{ MqttConnectionSettings, MqttMessage, MqttQoS, MqttSourceSettings }
import akka.stream.scaladsl.{ BidiFlow, Flow, RestartFlow }
import akka.util.ByteString
import com.github.mwegrz.scalastructlog.KeyValueLogging
import com.typesafe.config.Config
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence
import com.github.mwegrz.scalautil.javaDurationToDuration

object MqttClient {
  def apply(config: Config)(implicit actorSystem: ActorSystem,
                            actorMaterializer: ActorMaterializer): MqttClient =
    new DefaultMqttClient(config)
}

trait MqttClient {
  def flow[A, B](topics: Map[String, MqttQoS], bufferSize: Int, qos: MqttQoS)(
      toBinary: A => Array[Byte],
      fromBinary: Array[Byte] => B): Flow[(String, A), (String, B), NotUsed]
}

class DefaultMqttClient private[mqtt] (config: Config)(implicit actorSystem: ActorSystem,
                                                       actorMaterializer: ActorMaterializer)
    extends MqttClient
    with KeyValueLogging {

  private val broker = config.getString("broker")
  private val username = config.getString("username")
  private val password = config.getString("password")
  private val clientId = if (config.hasPath("client-id")) config.getString("client-id") else ""
  private val cleanSession = config.getBoolean("clean-session")
  private val automaticReconnect = config.getBoolean("automatic-reconnect")
  private val keepAliveInterval = config.getDuration("keep-alive-interval")
  private val restartPolicyIdleTimeout = config.getDuration("restart-policy.idle-timeout")
  private val restartPolicyMinBackoff = config.getDuration("restart-policy.min-backoff")
  private val restartPolicyMaxBackoff = config.getDuration("restart-policy.max-backoff")
  private val restartPolicyRandomFactor = config.getDouble("restart-policy.random-factor")

  private def connectionSettings =
    MqttConnectionSettings(
      broker = broker,
      clientId = if (clientId.isEmpty) UUID.randomUUID().toString else clientId,
      persistence = new MemoryPersistence
    ).withAuth(username, password)
      .withCleanSession(cleanSession)
      .withAutomaticReconnect(automaticReconnect)
      .withKeepAliveInterval(keepAliveInterval)

  override def flow[A, B](topics: Map[String, MqttQoS], bufferSize: Int, qos: MqttQoS)(
      toBinary: A => Array[Byte],
      fromBinary: Array[Byte] => B): Flow[(String, A), (String, B), NotUsed] = {
    val settings = MqttSourceSettings(connectionSettings, topics)
    val downlink = Flow[(String, A)].map { case (t, e) => MqttMessage(t, ByteString(toBinary(e))) }
    val uplink = Flow[MqttMessage].map(a => (a.topic, fromBinary(a.payload.toArray)))
    val mqttFlow =
      RestartFlow.withBackoff(restartPolicyMinBackoff,
                              restartPolicyMaxBackoff,
                              restartPolicyRandomFactor) { () =>
        val flow = MqttFlow(settings, bufferSize, qos).idleTimeout(restartPolicyIdleTimeout)
        log.debug("Flow started/restarted")
        flow
      }

    BidiFlow
      .fromFlows(downlink, uplink)
      .join(mqttFlow)
  }
}
