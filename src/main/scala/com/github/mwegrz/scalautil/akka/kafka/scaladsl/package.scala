package com.github.mwegrz.scalautil.akka.kafka

import akka.NotUsed
import akka.kafka.ConsumerMessage.{ Committable, CommittableMessage }
import akka.kafka.ProducerMessage.Message
import akka.stream.scaladsl.Flow

package object scaladsl {
  type KafkaFlow[K1, V1, K2, V2] = Flow[Message[K1, V1, Committable], CommittableMessage[K2, V2], NotUsed]
}
