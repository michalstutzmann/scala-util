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
import scodec.bits.ByteVector

import scala.concurrent.duration.Duration
import scala.concurrent.{ Await, ExecutionContext, Future }

trait TimeSeriesStore[Key, Value] {
  def addOrReplace: Sink[(Key, Instant, Value), Future[Done]]

  def addIfNotExists: Sink[(Key, Instant, Value), Future[Done]]

  def retrieveRange(sinceTime: Instant, untilTime: Instant): Source[(Key, Instant, Value), NotUsed]

  def retrieveRange(
      keys: Set[Key],
      sinceTime: Instant,
      untilTime: Instant
  ): Source[(Key, Instant, Value), NotUsed]

  def retrieveRange(keys: Set[Key], sinceTime: Instant): Source[(Key, Instant, Value), NotUsed]

  def retrieveLast(keys: Set[Key], count: Int): Source[(Key, Instant, Value), NotUsed]

  def retrieveLastUntil(keys: Set[Key], count: Int, untilTime: Instant): Source[(Key, Instant, Value), NotUsed]

  def retrieveKeys: Source[Key, NotUsed]

  def deleteKey(key: Key): Future[Done]

  def deleteRange(
      key: Key,
      sinceTime: Instant,
      untilTime: Instant
  ): Future[Done]
}

/*class InMemoryTimeSeriesStore[Key, Value](
    initial: Map[Key, SortedMap[Instant, Value]] = Map.empty[Key, SortedMap[Instant, Value]]
) extends TimeSeriesStore[Key, Value] {
  private var events = initial.withDefaultValue(SortedMap.empty[Instant, Value])

  override def addOrReplace: Sink[(Key, Instant, Value), Future[Done]] =
    Sink.foreach {
      case (key, time, value) =>
        events = events.updated(key, events(key).updated(time, value))
    }

  override def addIfNotExists: Sink[(Key, Instant, Value), Future[Done]] =
    Sink.foreach {
      case (key, time, value) =>
        events = {
          if (!events.contains(key) || events(key).contains(time)) {
            events.updated(key, events(key).updated(time, value))
          } else {
            events
          }
        }
    }

  override def retrieveRange(
      fromTime: Instant,
      untilTime: Instant
  ): Source[(Key, Instant, Value), NotUsed] =
    Source(events.foldLeft(List.empty[(Key, Instant, Value)]) {
      case (b, (a, s)) =>
        b ++ s.range(fromTime, untilTime).map(c => (a, c._1, c._2))
    })

  override def retrieveRange(
      keys: Set[Key],
      fromTime: Instant,
      untilTime: Instant
  ): Source[(Key, Instant, Value), NotUsed] = {
    def forKey(key: Key): Source[(Key, Instant, Value), NotUsed] = {
      Source(
        events(key)
          .range(fromTime, untilTime)
          .map { case (time, value) => (key, time, value) }
          .toList
      )
    }

    keys.map(forKey).foldLeft(Source.empty[(Key, Instant, Value)])((a, b) => a.concat(b))
  }

  override def retrieveRange(
      keys: Set[Key],
      fromTime: Instant
  ): Source[(Key, Instant, Value), NotUsed] = {
    def forKey(key: Key): Source[(Key, Instant, Value), NotUsed] = {
      Source(
        events(key)
          .rangeImpl(Some(fromTime), None)
          .map { case (time, value) => (key, time, value) }
          .toList
      )
    }

    keys.map(forKey).foldLeft(Source.empty[(Key, Instant, Value)])((a, b) => a.concat(b))
  }

  override def retrieveLast(keys: Set[Key], count: Int): Source[(Key, Instant, Value), NotUsed] = {
    def forKey(key: Key): Source[(Key, Instant, Value), NotUsed] = {
      Source(events(key).takeRight(count).map { case (time, value) => (key, time, value) }.toList)
    }

    keys.map(forKey).foldLeft(Source.empty[(Key, Instant, Value)])((a, b) => a.concat(b))
  }
}*/

class CassandraTimeSeriesStore[Key, Value](cassandraClient: CassandraClient, config: Config)(implicit
    executionContext: ExecutionContext,
    actorMaterializer: ActorMaterializer,
    keySerde: Serde[Key],
    valueSerde: Serde[Value]
) extends TimeSeriesStore[Key, Value]
    with KeyValueLogging {
  private val keyspace = config.getString("keyspace")
  private val table = config.getString("table")

  Await.ready(createTableIfNotExists(), Duration.Inf)

  override def addOrReplace: Sink[(Key, Instant, Value), Future[Done]] = {
    cassandraClient
      .createSink[(Key, Instant, Value)](
        s"""INSERT
           |INTO $keyspace.$table(
           |  key,
           |  time,
           |  value
           |) VALUES (?, ?, ?)""".stripMargin
      ) {
        case ((key, time, value), s) =>
          s.bind(
            ByteBuffer.wrap(keySerde.valueToBytes(key).toArray),
            time,
            ByteBuffer.wrap(valueSerde.valueToBytes(value).toArray)
          )
      }
  }

  override def addIfNotExists: Sink[(Key, Instant, Value), Future[Done]] = {
    cassandraClient
      .createSink[(Key, Instant, Value)](
        s"""INSERT
           |INTO $keyspace.$table(
           |  key,
           |  time,
           |  value
           |) VALUES (?, ?, ?) IF NOT EXISTS""".stripMargin
      ) {
        case ((key, time, value), s) =>
          s.bind(
            ByteBuffer.wrap(keySerde.valueToBytes(key).toArray),
            time,
            ByteBuffer.wrap(valueSerde.valueToBytes(value).toArray)
          )
      }
  }

  override def retrieveRange(
      sinceTime: Instant,
      untilTime: Instant
  ): Source[(Key, Instant, Value), NotUsed] = {
    val query = s"""SELECT key, time, value
                   |FROM $keyspace.$table
                   |WHERE time > ? AND time <= ? ALLOW FILTERING""".stripMargin

    cassandraClient
      .createSource(
        query,
        List(sinceTime, untilTime)
      )
      .map { row =>
        (
          keySerde.bytesToValue(ByteVector(row.getBytes("key").array())),
          row.get("time", classOf[Instant]),
          valueSerde.bytesToValue(ByteVector(row.getBytes("value").array()))
        )
      }
  }

  override def retrieveRange(
      keys: Set[Key],
      sinceTime: Instant,
      untilTime: Instant
  ): Source[(Key, Instant, Value), NotUsed] = {
    def forKey(key: Key): Source[(Key, Instant, Value), NotUsed] = {
      val query = s"""SELECT time, value
                     |FROM $keyspace.$table
                     |WHERE key = ? AND time > ? AND time <= ?
                     |ORDER BY time ASC""".stripMargin

      cassandraClient
        .createSource(
          query,
          List(ByteBuffer.wrap(keySerde.valueToBytes(key).toArray), sinceTime, untilTime)
        )
        .map { row =>
          (
            key,
            row.get("time", classOf[Instant]),
            valueSerde.bytesToValue(ByteVector(row.getBytes("value").array()))
          )
        }
    }

    keys.map(forKey).foldLeft(Source.empty[(Key, Instant, Value)]) { (a, b) => a.concat(b) }
  }

  override def retrieveRange(
      keys: Set[Key],
      sinceTime: Instant
  ): Source[(Key, Instant, Value), NotUsed] = {
    def forKey(key: Key): Source[(Key, Instant, Value), NotUsed] = {
      val query = s"""SELECT time, value
                     |FROM $keyspace.$table
                     |WHERE key = ? AND time > ?
                     |ORDER BY time ASC""".stripMargin

      cassandraClient
        .createSource(
          query,
          List(ByteBuffer.wrap(keySerde.valueToBytes(key).toArray), sinceTime)
        )
        .map { row =>
          (
            key,
            row.get("time", classOf[Instant]),
            valueSerde.bytesToValue(ByteVector(row.getBytes("value").array()))
          )
        }
    }

    keys.map(forKey).foldLeft(Source.empty[(Key, Instant, Value)]) { (a, b) => a.concat(b) }
  }

  override def retrieveLast(keys: Set[Key], count: Int): Source[(Key, Instant, Value), NotUsed] = {
    def forKey(key: Key): Source[(Key, Instant, Value), NotUsed] = {
      val query = s"""SELECT time, value
                     |FROM $keyspace.$table
                     |WHERE key = ?
                     |LIMIT ?""".stripMargin

      cassandraClient
        .createSource(
          query,
          List(ByteBuffer.wrap(keySerde.valueToBytes(key).toArray), count.asInstanceOf[AnyRef])
        )
        .map { row =>
          (
            key,
            row.get("time", classOf[Instant]),
            valueSerde.bytesToValue(ByteVector(row.getBytes("value").array()))
          )
        }
        .fold(List.empty[(Key, Instant, Value)]) {
          case (rows, row) => row :: rows
        }
        .mapConcat(identity)
    }

    keys.map(forKey).foldLeft(Source.empty[(Key, Instant, Value)]) { (a, b) => a.concat(b) }
  }

  override def retrieveLastUntil(
      keys: Set[Key],
      count: Int,
      untilTime: Instant
  ): Source[(Key, Instant, Value), NotUsed] = {
    def forKey(key: Key): Source[(Key, Instant, Value), NotUsed] = {
      val query = s"""SELECT time, value
                     |FROM $keyspace.$table
                     |WHERE key = ? AND time < ?
                     |LIMIT ?""".stripMargin

      cassandraClient
        .createSource(
          query,
          List(ByteBuffer.wrap(keySerde.valueToBytes(key).toArray), untilTime, count.asInstanceOf[AnyRef])
        )
        .map { row =>
          (
            key,
            row.get("time", classOf[Instant]),
            valueSerde.bytesToValue(ByteVector(row.getBytes("value").array()))
          )
        }
        .fold(List.empty[(Key, Instant, Value)]) {
          case (rows, row) => row :: rows
        }
        .mapConcat(identity)
    }

    keys.map(forKey).foldLeft(Source.empty[(Key, Instant, Value)]) { (a, b) => a.concat(b) }
  }

  override def retrieveKeys: Source[Key, NotUsed] = {
    val query = s"""SELECT DISTINCT key
                   |FROM $keyspace.$table ALLOW FILTERING""".stripMargin

    cassandraClient
      .createSource(
        query,
        Nil
      )
      .map { row => keySerde.bytesToValue(ByteVector(row.getBytes("key").array())) }
      .fold(List.empty[Key]) {
        case (rows, row) => row :: rows
      }
      .mapConcat(identity)
  }

  override def deleteKey(key: Key): Future[Done] =
    Source
      .single(key)
      .runWith(
        cassandraClient
          .createSink[Key](
            s"""DELETE
           |FROM $keyspace.$table
           |WHERE key = ?""".stripMargin
          ) {
            case (key, s) =>
              s.bind(
                ByteBuffer.wrap(keySerde.valueToBytes(key).toArray)
              )
          }
      )

  def deleteRange(
      key: Key,
      sinceTime: Instant,
      untilTime: Instant
  ): Future[Done] =
    Source
      .single(key)
      .runWith(
        cassandraClient
          .createSink[Key](
            s"""DELETE
               |FROM $keyspace.$table
               |WHERE key = ? AND """.stripMargin
          ) {
            case (key, s) =>
              s.bind(
                ByteBuffer.wrap(keySerde.valueToBytes(key).toArray)
              )
          }
      )

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
