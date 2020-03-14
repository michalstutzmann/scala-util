package com.github.mwegrz.scalautil.kafka

import akka.{ Done, NotUsed }
import akka.actor.ActorSystem
import akka.kafka.ConsumerMessage.{ CommittableMessage, CommittableOffset }
import akka.kafka.scaladsl.{ Consumer, Producer }
import akka.kafka.{ ConsumerSettings, ProducerMessage, ProducerSettings, Subscriptions }
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.{ BidiFlow, Flow, Keep, Sink, Source }
import com.github.mwegrz.scalautil.akka.kafka.scaladsl.{ KafkaCommitableFlow, KafkaFlow }
import com.typesafe.config.Config
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.apache.kafka.clients.producer.ProducerRecord
import org.apache.kafka.common.serialization.{ ByteArrayDeserializer, ByteArraySerializer }
import com.github.mwegrz.scalautil.ConfigOps

import scala.concurrent.Future

object KafkaClient {
  def apply(
      config: Config
  )(implicit actorSystem: ActorSystem, actorMaterializer: ActorMaterializer): KafkaClient =
    new DefaultKafkaClient(config)
}

trait KafkaClient {
  def source[A](topic: String)(fromBinary: (Array[Byte], Array[Byte]) => A): Source[A, NotUsed]

  def committableSource[A](topic: String)(
      fromBinary: (Array[Byte], Array[Byte]) => A
  ): Source[(A, CommittableOffset), NotUsed]

  def sink[A](topic: String)(toBinary: A => (Array[Byte], Array[Byte])): Sink[A, Future[Done]]

  def committableSink[A](topic: String)(
      toBinary: A => (Array[Byte], Array[Byte])
  ): Sink[(A, CommittableOffset), Future[Done]]

  def flow[A, B](inTopic: String, outTopics: Set[String])(
      toBinary: A => (Array[Byte], Array[Byte]),
      fromBinary: (Array[Byte], Array[Byte]) => B
  ): Flow[A, B, NotUsed]

  def committableFlow[A, B](inTopic: String, outTopics: Set[String])(
      toBinary: A => (Array[Byte], Array[Byte]),
      fromBinary: (Array[Byte], Array[Byte]) => B
  ): Flow[(A, CommittableOffset), (B, CommittableOffset), NotUsed]
}

class DefaultKafkaClient private[kafka] (config: Config)(
    implicit actorSystem: ActorSystem,
    actorMaterializer: ActorMaterializer
) extends KafkaClient {
  private implicit val producerSettings: ProducerSettings[Array[Byte], Array[Byte]] =
    ProducerSettings(
      config.getConfig("producer").withReferenceDefaults("akka.kafka.producer"),
      new ByteArraySerializer,
      new ByteArraySerializer
    )
  private implicit val consumerSettings: ConsumerSettings[Array[Byte], Array[Byte]] =
    ConsumerSettings(
      config.getConfig("consumer").withReferenceDefaults("akka.kafka.consumer"),
      new ByteArrayDeserializer,
      new ByteArrayDeserializer
    )

  override def source[A](
      topic: String
  )(fromBinary: (Array[Byte], Array[Byte]) => A): Source[A, NotUsed] =
    Consumer
      .plainSource(consumerSettings, Subscriptions.topics(topic))
      .map { r => fromBinary(r.key(), r.value()) }
      .mapMaterializedValue(_ => NotUsed)

  def committableSource[A](
      topic: String
  )(fromBinary: (Array[Byte], Array[Byte]) => A): Source[(A, CommittableOffset), NotUsed] =
    Consumer
      .committableSource(consumerSettings, Subscriptions.topics(topic))
      .map { m =>
        val b = fromBinary(m.record.key(), m.record.value())
        (b, m.committableOffset)
      }
      .mapMaterializedValue(_ => NotUsed)

  override def sink[A](
      topic: String
  )(toBinary: A => (Array[Byte], Array[Byte])): Sink[A, Future[Done]] =
    Flow[A]
      .map { a =>
        val (key, value) = toBinary(a)
        new ProducerRecord[Array[Byte], Array[Byte]](
          topic,
          key,
          value
        )
      }
      .toMat(Producer.plainSink(producerSettings))(Keep.right)

  def committableSink[A](
      topic: String
  )(toBinary: A => (Array[Byte], Array[Byte])): Sink[(A, CommittableOffset), Future[Done]] =
    Flow[(A, CommittableOffset)]
      .map {
        case (a, offset) =>
          val (key, value) = toBinary(a)
          ProducerMessage.Message(
            new ProducerRecord[Array[Byte], Array[Byte]](
              topic,
              key,
              value
            ),
            offset
          )
      }
      .toMat(Producer.commitableSink(producerSettings))(Keep.right)

  override def flow[A, B](inTopic: String, outTopics: Set[String])(
      toBinary: A => (Array[Byte], Array[Byte]),
      fromBinary: (Array[Byte], Array[Byte]) => B
  ): Flow[A, B, NotUsed] = {
    val kafkaFlow = KafkaFlow(producerSettings, consumerSettings, Subscriptions.topics(outTopics))

    val downlink =
      Flow[A].map { a =>
        val (key, value) = toBinary(a)
        new ProducerRecord[Array[Byte], Array[Byte]](
          inTopic,
          key,
          value
        )
      }

    val uplink: Flow[ConsumerRecord[Array[Byte], Array[Byte]], B, NotUsed] =
      Flow[ConsumerRecord[Array[Byte], Array[Byte]]].map { r =>
        val b = fromBinary(r.key(), r.value())
        b
      }

    val bidiFlow = BidiFlow.fromFlows(downlink, uplink)
    bidiFlow.joinMat(kafkaFlow)(Keep.left)
  }

  override def committableFlow[A, B](inTopic: String, outTopics: Set[String])(
      toBinary: A => (Array[Byte], Array[Byte]),
      fromBinary: (Array[Byte], Array[Byte]) => B
  ): Flow[(A, CommittableOffset), (B, CommittableOffset), NotUsed] = {
    val kafkaFlow =
      KafkaCommitableFlow(producerSettings, consumerSettings, Subscriptions.topics(outTopics))

    val downlink =
      Flow[(A, CommittableOffset)].map {
        case (a, offset) =>
          val (key, value) = toBinary(a)
          ProducerMessage.Message(
            new ProducerRecord[Array[Byte], Array[Byte]](
              inTopic,
              key,
              value
            ),
            offset
          )
      }

    val uplink: Flow[CommittableMessage[Array[Byte], Array[Byte]], (B, CommittableOffset), NotUsed] =
      Flow[CommittableMessage[Array[Byte], Array[Byte]]].map { m =>
        val b = fromBinary(m.record.key(), m.record.value())
        (b, m.committableOffset)
      }

    val bidiFlow = BidiFlow.fromFlows(downlink, uplink)
    bidiFlow.joinMat(kafkaFlow)(Keep.left)
  }
}
