package com.github.mwegrz.scalautil.mqtt

import akka.Done
import akka.stream.alpakka.mqtt.scaladsl.MqttFlow
import akka.stream.alpakka.mqtt.{ MqttConnectionSettings, MqttMessage, MqttQoS, MqttSourceSettings }
import akka.stream.scaladsl.{ Flow, Keep, Sink, Source }
import com.github.mwegrz.app.Shutdownable
import com.github.mwegrz.scalastructlog.KeyValueLogging
import com.github.mwegrz.scalautil.akka.Akka
import com.github.mwegrz.scalautil.akka.streams.scaladsl.MergeBroadcastHub
import com.typesafe.config.Config
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence

import scala.concurrent.Future
import scala.collection.JavaConverters._

trait MqttClient extends Shutdownable {
  def flow: Flow[MqttMessage, MqttMessage, Future[Done]]

  def source: Source[MqttMessage, Future[Done]]

  def sink: Sink[MqttMessage, Future[Done]]
}

class DefaultMqttClient(config: Config)(implicit akka: Akka) extends MqttClient with KeyValueLogging {
  private val url = config.getString("url")
  private val id = config.getString("id")
  private val username = config.getString("username")
  private val password = config.getString("password")
  private val topics = config.getStringList("topics").asScala.toList

  import akka.actorMaterializer

  private val connectionSettings = MqttConnectionSettings(
    url,
    id,
    new MemoryPersistence
  ).withAuth(username, password).withCleanSession(true)

  private val sourceSettings = MqttSourceSettings(
    connectionSettings,
    topics.map(a => (a, MqttQoS.atMostOnce)).toMap
  )

  private val mqttFlow =
    MqttFlow(sourceSettings, bufferSize = 8, MqttQoS.AtLeastOnce)

  /*private val broadcastHub = BroadcastHub.sink[MqttMessage]

  private val mergeHub = MergeHub.source[MqttMessage]

  private val (mergeBroadcastFlow, killSwitch) = mergeHub
    .viaMat(KillSwitches.single)(Keep.both)
    .viaMat(mqttFlow)(Keep.both)
    .toMat(broadcastHub)(Keep.both)
    .mapMaterializedValue(a => (Flow.fromSinkAndSourceMat(a._1._1._1, a._2)((_, _) => a._1._2), a._1._1._2))
    .run()*/

  private val mergeBroadcastHub = new MergeBroadcastHub(mqttFlow)

  private val mergeBroadcastFlow = mergeBroadcastHub.flow

  log.debug("Initialized")

  override def flow: Flow[MqttMessage, MqttMessage, Future[Done]] =
    mergeBroadcastFlow

  override def source: Source[MqttMessage, Future[Done]] =
    Source.empty.viaMat(mergeBroadcastFlow)(Keep.right)

  override def sink: Sink[MqttMessage, Future[Done]] =
    mergeBroadcastFlow.toMat(Sink.ignore)(Keep.right)

  override def shutdown(): Unit = mergeBroadcastHub.shutdown()
}

object MqttClient {
  def apply(config: Config)(implicit akka: Akka): MqttClient =
    new DefaultMqttClient(config)
}
