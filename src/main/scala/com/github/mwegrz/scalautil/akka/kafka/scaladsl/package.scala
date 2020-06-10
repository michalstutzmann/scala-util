package com.github.mwegrz.scalautil.akka.kafka

import java.util.UUID

import akka.NotUsed
import akka.actor.ActorSystem
import akka.kafka.ConsumerMessage.{ Committable, CommittableMessage }
import akka.kafka.ProducerMessage.Message
import akka.kafka.{ ConsumerSettings, ProducerSettings, Subscriptions }
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.{ BidiFlow, Flow, Keep, Sink, Source }
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.apache.kafka.clients.producer.ProducerRecord

import scala.concurrent.Future
import scala.concurrent.duration.FiniteDuration

package object scaladsl {
  type KafkaFlow[K1, V1, K2, V2] = Flow[ProducerRecord[K1, V1], ConsumerRecord[K2, V2], NotUsed]

  type KafkaCommitableFlow[K1, V1, K2, V2] =
    Flow[Message[K1, V1, Committable], CommittableMessage[K2, V2], NotUsed]

  def byteMessageFlow[A, B](
      inTopic: String,
      outTopic: String
  )(toBinary: A => (Array[Byte], Array[Byte]), fromBinary: (Array[Byte], Array[Byte]) => B)(implicit
      producerSettings: ProducerSettings[Array[Byte], Array[Byte]],
      consumerSettings: ConsumerSettings[Array[Byte], Array[Byte]],
      actorSystem: ActorSystem,
      actorMaterializer: ActorMaterializer
  ): Flow[A, B, NotUsed] = {
    val kafkaFlow =
      KafkaCommitableFlow(producerSettings, consumerSettings, Subscriptions.topics(outTopic))

    val downlink: Flow[A, Message[Array[Byte], Array[Byte], Committable], NotUsed] = ???
    val uplink: Flow[CommittableMessage[Array[Byte], Array[Byte]], B, NotUsed] = ???

    val bidiFlow = BidiFlow.fromFlows(downlink, uplink)
    bidiFlow.joinMat(kafkaFlow)(Keep.right)
  }

  def ask[A, B](requestTopic: String, responseTopic: String)(
      arg: A
  )(toBinary: A => Array[Byte], fromBinary: Array[Byte] => B)(implicit
      producerSettings: ProducerSettings[Array[Byte], Array[Byte]],
      consumerSettings: ConsumerSettings[Array[Byte], Array[Byte]],
      actorSystem: ActorSystem,
      actorMaterializer: ActorMaterializer,
      timeout: FiniteDuration
  ): Future[B] = {
    val flow = byteMessageFlow[(String, A), (String, B)](requestTopic, responseTopic)(
      toBinary = _ => (Array.empty[Byte], Array.empty[Byte]),
      fromBinary = (_, _) => ("", null.asInstanceOf[B])
    )
    val reqId = UUID.randomUUID().toString
    Source
      .single((reqId, arg))
      .via(flow)
      .filter(_._1 == reqId)
      .map(_._2)
      .initialTimeout(timeout)
      .toMat(Sink.head)(Keep.right)
      .run()
  }
}
