//package com.github.mwegrz.scalautil.pulsar
//
//import java.time.Instant
//
//import akka.NotUsed
//import akka.actor.ActorSystem
//import akka.stream.ActorMaterializer
//import akka.stream.scaladsl.{ RestartSink, RestartSource, Sink, Source }
//import com.github.mwegrz.scalastructlog.KeyValueLogging
//import com.typesafe.config.Config
//import com.github.mwegrz.scalautil.javaDurationToDuration
//import com.github.mwegrz.scalautil.serialization.Serde
//import com.sksamuel.pulsar4s.{ MessageId, Subscription, Topic }
//import org.apache.pulsar.client.api.ReaderConfiguration
//
//object PulsarClient {
//  def apply(config: Config)(implicit actorSystem: ActorSystem, actorMaterializer: ActorMaterializer): PulsarClient =
//    new DefaultPulsarClient(config)
//}
//
//trait PulsarClient {
//  def source[Value](topic: Topic, messageId: MessageId)(
//      implicit valueSerde: Serde[Value]): Source[Message[Value], NotUsed]
//
//  def sink[Value](implicit valueSerde: Serde[Value]): Sink[(Topic, Message[Value]), NotUsed]
//}
//
//class DefaultPulsarClient private[pulsar] (config: Config)(implicit actorSystem: ActorSystem,
//                                                           actorMaterializer: ActorMaterializer)
//    extends PulsarClient
//    with KeyValueLogging {
//
//  private val url = config.getString("url")
//  private val restartPolicyIdleTimeout = config.getDuration("restart-policy.idle-timeout")
//  private val restartPolicyMinBackoff = config.getDuration("restart-policy.min-backoff")
//  private val restartPolicyMaxBackoff = config.getDuration("restart-policy.max-backoff")
//  private val restartPolicyRandomFactor = config.getDouble("restart-policy.random-factor")
//
//  private val client = com.sksamuel.pulsar4s.PulsarClient(url)
//
//  override def source[Value](topic: Topic, messageId: MessageId)(
//      implicit valueSerde: Serde[Value]): Source[Message[Value], NotUsed] = {
//    val createReader = () => client.reader(topic, Subscription.generate, messageId)
//    val pulsarSource =
//      RestartSource.withBackoff(restartPolicyMinBackoff, restartPolicyMaxBackoff, restartPolicyRandomFactor) { () =>
//        val source = Source.fromGraph(new PulsarSourceStage(createReader)).idleTimeout(restartPolicyIdleTimeout)
//        log.warning("Source started/restarted")
//        source
//      }
//    pulsarSource.map(m =>
//      Message(Instant.ofEpochMilli(m.eventTime), m.messageId, m.key, valueSerde.binaryToValue(m.data)))
//  }
//
//  override def sink[Value](implicit valueSerde: Serde[Value]): Sink[(Topic, Message[Value]), NotUsed] = {
//    val createProducer = (topic: Topic) => client.producer(topic)
//    val pulsarSink =
//      RestartSink.withBackoff(restartPolicyMinBackoff, restartPolicyMaxBackoff, restartPolicyRandomFactor) { () =>
//        val sink = Sink.fromGraph(new PulsarSinkStage(createProducer))
//        log.warning("Sink started/restarted")
//        sink
//      }
//    pulsarSink.contramap {
//      case (topic, message) =>
//        (topic,
//         com.sksamuel.pulsar4s.Message(
//           key = message.key,
//           data = valueSerde.valueToBinary(message.value),
//           properties = Map.empty,
//           messageId = message.id.map(MessageId(_)),
//           publishTime = Instant.now.toEpochMilli,
//           eventTime = message.time.toEpochMilli
//         ))
//    }
//  }
//}
