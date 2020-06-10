package com.github.mwegrz.scalautil.akka.kafka.scaladsl

import akka.actor.ActorSystem
import akka.kafka.{ ConsumerSettings, ProducerSettings, Subscription }
import akka.kafka.scaladsl.{ Consumer, Producer }
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.Flow

object KafkaCommitableFlow {
  def apply[K1, V1, K2, V2](
      producerSettings: ProducerSettings[K1, V1],
      consumerSettings: ConsumerSettings[K2, V2],
      subscription: Subscription
  )(implicit
      actorSystem: ActorSystem,
      actorMaterializer: ActorMaterializer
  ): KafkaCommitableFlow[K1, V1, K2, V2] = {
    val sink = Producer.commitableSink[K1, V1](producerSettings)
    val source = Consumer.committableSource[K2, V2](consumerSettings, subscription)
    Flow.fromSinkAndSource(sink, source)
  }
}
