package com.github.mwegrz.scalautil.store

import java.nio.ByteBuffer
import java.time.Instant

import akka.stream.ActorMaterializer
import akka.{ Done, NotUsed }
import akka.stream.scaladsl.{ Sink, Source }
import com.github.mwegrz.scalastructlog.KeyValueLogging
import com.github.mwegrz.scalautil.cassandra.CassandraClient
import com.github.mwegrz.scalautil.serialization.Serde
import com.typesafe.config.Config

import scala.collection.SortedMap
import scala.concurrent.duration.Duration
import scala.concurrent.{ Await, ExecutionContext, Future }

trait TimeSeriesStore[Key, Value] {
  def add: Sink[(Key, Instant, Value), Future[Done]]

  def retrieveRange(fromTime: Instant, untilTime: Instant): Source[(Key, Instant, Value), NotUsed]

  def retrieveRange(keys: Set[Key],
                    fromTime: Instant,
                    untilTime: Instant): Source[(Key, Instant, Value), NotUsed]

  def retrieveLast(keys: Set[Key], count: Int): Source[(Key, Instant, Value), NotUsed]
}

class InMemoryTimeSeriesStore[Key, Value](
    initial: Map[Key, SortedMap[Instant, Value]] = Map.empty[Key, SortedMap[Instant, Value]])
    extends TimeSeriesStore[Key, Value] {
  private var events = initial.withDefaultValue(SortedMap.empty[Instant, Value])

  override def add: Sink[(Key, Instant, Value), Future[Done]] =
    Sink.foreach {
      case (key, time, value) => events = events.updated(key, events(key).updated(time, value))
    }

  override def retrieveRange(fromTime: Instant,
                             untilTime: Instant): Source[(Key, Instant, Value), NotUsed] =
    Source(events.foldLeft(List.empty[(Key, Instant, Value)]) {
      case (b, (a, s)) =>
        b ++ s.range(fromTime, untilTime).map(c => (a, c._1, c._2))
    })

  override def retrieveRange(keys: Set[Key],
                             fromTime: Instant,
                             untilTime: Instant): Source[(Key, Instant, Value), NotUsed] = {
    def forKey(key: Key): Source[(Key, Instant, Value), NotUsed] = {
      Source(
        events(key)
          .range(fromTime, untilTime)
          .map { case (time, value) => (key, time, value) }
          .toList)
    }

    keys.map(forKey).foldLeft(Source.empty[(Key, Instant, Value)])((a, b) => a.concat(b))
  }

  override def retrieveLast(keys: Set[Key], count: Int): Source[(Key, Instant, Value), NotUsed] = {
    def forKey(key: Key): Source[(Key, Instant, Value), NotUsed] = {
      Source(events(key).takeRight(count).map { case (time, value) => (key, time, value) }.toList)
    }

    keys.map(forKey).foldLeft(Source.empty[(Key, Instant, Value)])((a, b) => a.concat(b))
  }
}

class CassandraTimeSeriesStore[Key, Value](cassandraClient: CassandraClient, config: Config)(
    implicit executionContext: ExecutionContext,
    actorMaterializer: ActorMaterializer,
    keySerde: Serde[Key],
    valueSerde: Serde[Value])
    extends TimeSeriesStore[Key, Value]
    with KeyValueLogging {
  private val keyspace = config.getString("keyspace")
  private val table = config.getString("table")
  private val rowTtl = config.getDuration("row-ttl")

  Await.ready(createTableIfNotExists(), Duration.Inf)

  override def add: Sink[(Key, Instant, Value), Future[Done]] = {
    cassandraClient
      .createSink[(Key, Instant, Value)](s"""INSERT
                        |INTO $keyspace.$table(
                        |  key,
                        |  time,
                        |  value
                        |) VALUES (?, ?, ?) USING TTL ${rowTtl.getSeconds}""".stripMargin) {
        case ((key, time, value), s) =>
          s.bind(
            ByteBuffer.wrap(keySerde.valueToBinary(key)),
            time,
            ByteBuffer.wrap(valueSerde.valueToBinary(value))
          )
      }
  }

  override def retrieveRange(fromTime: Instant,
                             toTime: Instant): Source[(Key, Instant, Value), NotUsed] = {
    val query = s"""SELECT key, time, value
                     |FROM $keyspace.$table
                     |WHERE time > ? AND time <= ? ALLOW FILTERING""".stripMargin

    cassandraClient
      .createSource(
        query,
        List(fromTime, toTime)
      )
      .map { row =>
        (keySerde.binaryToValue(row.getBytes("key").array()),
         row.get("time", classOf[Instant]),
         valueSerde.binaryToValue(row.getBytes("value").array()))
      }
  }

  override def retrieveRange(keys: Set[Key],
                             fromTime: Instant,
                             toTime: Instant): Source[(Key, Instant, Value), NotUsed] = {
    def forKey(key: Key): Source[(Key, Instant, Value), NotUsed] = {
      val query = s"""SELECT time, value
                     |FROM $keyspace.$table
                     |WHERE key = ? AND time > ? AND time <= ?
                     |ORDER BY time ASC""".stripMargin

      cassandraClient
        .createSource(
          query,
          List(ByteBuffer.wrap(keySerde.valueToBinary(key)), fromTime, toTime)
        )
        .map { row =>
          (key,
           row.get("time", classOf[Instant]),
           valueSerde.binaryToValue(row.getBytes("value").array()))
        }
    }

    keys.map(forKey).foldLeft(Source.empty[(Key, Instant, Value)]) { (a, b) =>
      a.concat(b)
    }
  }

  override def retrieveLast(keys: Set[Key], count: Int): Source[(Key, Instant, Value), NotUsed] = {
    def forKey(key: Key): Source[(Key, Instant, Value), NotUsed] = {
      val query = s"""SELECT time, value
                     |FROM $keyspace.$table
                     |WHERE key = ? LIMIT ?""".stripMargin

      cassandraClient
        .createSource(
          query,
          List(ByteBuffer.wrap(keySerde.valueToBinary(key)), count.asInstanceOf[AnyRef])
        )
        .map { row =>
          (key,
           row.get("time", classOf[Instant]),
           valueSerde.binaryToValue(row.getBytes("value").array()))
        }
    }

    keys.map(forKey).foldLeft(Source.empty[(Key, Instant, Value)]) { (a, b) =>
      a.concat(b)
    }
  }

  private def createTableIfNotExists(): Future[Done] = {
    log.debug("Creating table if not exists", ("keyspace" -> keyspace, "table" -> table))
    cassandraClient.execute(s"""CREATE TABLE IF NOT EXISTS $keyspace.$table (
                                 |  key blob,
                                 |  time timestamp,
                                 |  value blob,
                                 |  PRIMARY KEY (key, time)
                                 |) WITH CLUSTERING ORDER BY (time DESC)""".stripMargin)
  }

  log.debug("Initialized")
}
