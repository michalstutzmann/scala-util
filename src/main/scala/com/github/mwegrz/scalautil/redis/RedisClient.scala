package com.github.mwegrz.scalautil.redis

import java.nio.ByteBuffer

import akka.NotUsed
import akka.actor.ActorSystem
import akka.stream.{ ActorMaterializer, OverflowStrategy }
import akka.stream.scaladsl.Source
import com.github.mwegrz.scalautil.serialization.Serde
import com.typesafe.config.Config
import com.github.mwegrz.scalautil.ConfigOps
import io.lettuce.core.codec.RedisCodec
import io.lettuce.core.pubsub.RedisPubSubListener
import scodec.bits.ByteVector

import scala.concurrent.ExecutionContext

object RedisClient {
  def apply(config: Config)(
      implicit actorSystem: ActorSystem,
      actorMaterializer: ActorMaterializer,
      executionContext: ExecutionContext
  ): RedisClient =
    new DefaultRedisClient(config.withReferenceDefaults("redis-client"))
}

trait RedisClient {
  def subscribe[Key, Value](
      channel: Key
  )(implicit keySerde: Serde[Key], valueSerde: Serde[Value]): Source[(Key, Value), NotUsed]
}

class DefaultRedisClient(config: Config) extends RedisClient {
  private val url = config.getString("url")
  private val bufferSize = config.getInt("buffer-size")

  private val underlying: io.lettuce.core.RedisClient = io.lettuce.core.RedisClient.create(url)

  override def subscribe[Key, Value](
      pattern: Key
  )(implicit keySerde: Serde[Key], valueSerde: Serde[Value]): Source[(Key, Value), NotUsed] = {
    Source.queue[(Key, Value)](bufferSize, OverflowStrategy.dropNew).mapMaterializedValue { source =>
      val codec = new RedisCodec[Key, Value]() {
        override def decodeKey(bytes: ByteBuffer): Key = keySerde.bytesToValue(ByteVector(bytes))

        override def decodeValue(bytes: ByteBuffer): Value = valueSerde.bytesToValue(ByteVector(bytes))

        override def encodeKey(key: Key): ByteBuffer = keySerde.valueToBytes(key).toByteBuffer

        override def encodeValue(value: Value): ByteBuffer = valueSerde.valueToBytes(value).toByteBuffer
      }
      val connection = underlying.connectPubSub[Key, Value](codec).sync()
      connection.getStatefulConnection.addListener(new RedisPubSubListener[Key, Value]() {
        override def message(channel: Key, message: Value): Unit = ()

        override def message(pattern: Key, channel: Key, message: Value): Unit = source.offer((channel, message))

        override def subscribed(channel: Key, count: Long): Unit = ()

        override def psubscribed(pattern: Key, count: Long): Unit = ()

        override def unsubscribed(channel: Key, count: Long): Unit = ()

        override def punsubscribed(pattern: Key, count: Long): Unit = ()
      })
      connection.psubscribe(pattern)
      NotUsed
    }
  }
}
