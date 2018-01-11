package com.github.mwegrz.scalautil.timeseriesstore

import java.nio.ByteBuffer
import java.time.Instant

import akka.stream.ActorMaterializer
import akka.{ Done, NotUsed }
import akka.stream.scaladsl.{ Sink, Source }
import com.github.mwegrz.scalastructlog.KeyValueLogging
import com.github.mwegrz.scalautil.cassandra.CassandraClient
import com.typesafe.config.Config

import scala.collection.SortedMap
import scala.concurrent.duration.Duration
import scala.concurrent.{ Await, ExecutionContext, Future }

trait TimeSeriesStore[A, B] {
  def sink: Sink[(A, Instant, B), Future[Done]]

  def source(keys: Set[A], fromTime: Instant, untilTime: Instant): Source[(A, B), NotUsed]
}

class InMemoryTimeSeriesStore[A, B](initial: Map[A, SortedMap[Instant, B]] = Map.empty[A, SortedMap[Instant, B]])
    extends TimeSeriesStore[A, B] {
  private var events = initial.withDefaultValue(SortedMap.empty[Instant, B])

  override def sink: Sink[(A, Instant, B), Future[Done]] =
    Sink.foreach { case (key, time, value) => events = events.updated(key, events(key).updated(time, value)) }

  override def source(keys: Set[A], fromTime: Instant, untilTime: Instant): Source[(A, B), NotUsed] = {
    def forKey(key: A): Source[(A, B), NotUsed] = {
      Source(events(key).range(fromTime, untilTime).map(a => (key, a._2)).toList)
    }

    keys.map(forKey).foldLeft(Source.empty[(A, B)])((a, b) => a.concat(b))
  }
}

class CassandraTimeSeriesStore[A, B](cassandraClient: CassandraClient, config: Config)(
    aToBinary: A => Array[Byte],
    bToBinary: B => Array[Byte],
    binaryToB: Array[Byte] => B)(implicit executionContext: ExecutionContext, actorMaterializer: ActorMaterializer)
    extends TimeSeriesStore[A, B]
    with KeyValueLogging {
  private val keyspace = config.getString("keyspace")
  private val table = config.getString("table")
  private val rowTtl = config.getDuration("row-ttl")

  Await.ready(createTableIfNotExists(), Duration.Inf)

  override def sink: Sink[(A, Instant, B), Future[Done]] = {
    cassandraClient
      .createSink[(A, Instant, B)](s"""INSERT
                        |INTO $keyspace.$table(
                        |  key,
                        |  time,
                        |  value
                        |) VALUES (?, ?, ?) USING TTL ${rowTtl.getSeconds}""".stripMargin) {
        case ((key, time, value), s) =>
          s.bind(
            ByteBuffer.wrap(aToBinary(key)),
            time,
            ByteBuffer.wrap(bToBinary(value))
          )
      }
  }

  override def source(keys: Set[A], fromTime: Instant, toTime: Instant): Source[(A, B), NotUsed] = {
    def forKey(key: A): Source[(A, B), NotUsed] = {
      val query = s"""SELECT value
                     |FROM $keyspace.$table
                     |WHERE key = ? AND time > ? AND time <= ?""".stripMargin

      cassandraClient
        .createSource(
          query,
          List(ByteBuffer.wrap(aToBinary(key)), fromTime, toTime)
        )
        .map { row =>
          (key, binaryToB(row.getBytes("value").array()))
        }
    }

    keys.map(forKey).foldLeft(Source.empty[(A, B)]) { (a, b) =>
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
