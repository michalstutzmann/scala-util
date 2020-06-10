package com.github.mwegrz.scalautil.store

import java.nio.ByteBuffer
import java.time.Instant

import akka.{ Done, NotUsed }
import akka.actor.{ ActorRefFactory, ExtendedActorSystem, Props }
import akka.persistence.{ PersistentActor, RecoveryCompleted, SnapshotOffer }
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.{ Sink, Source }
import akka.util.Timeout
import com.github.mwegrz.app.Shutdownable
import com.github.mwegrz.scalastructlog.KeyValueLogging
import com.github.mwegrz.scalautil.akka.serialization.ResourceAvroSerializer
import com.github.mwegrz.scalautil.serialization.Serde
import com.sksamuel.avro4s._
import com.github.mwegrz.scalautil.avro4s.codecs._
import com.github.mwegrz.scalautil.cassandra.CassandraClient
import com.typesafe.config.Config
import scodec.bits.ByteVector

import scala.collection.immutable.SortedMap
import scala.concurrent.{ Await, ExecutionContext, Future }
import scala.concurrent.duration._

object KeyValueStore {
  final case class KeyExistsException[Key](key: Key) extends IllegalArgumentException
  final case class ForbiddenException[Key](key: Key) extends IllegalArgumentException
  final case class InvalidValueException[Value](value: Value) extends IllegalArgumentException
}

trait KeyValueStore[Key, Value] {
  def add(value: Value): Future[Option[(Key, Value)]]

  def update(key: Key, value: Value): Future[Option[Value]]

  def add(key: Key, value: Value): Future[Unit]

  def retrieve(key: Key): Future[Option[Value]]

  def retrieveAll: Future[SortedMap[Key, Value]]

  def retrievePage(cursor: Option[Key], count: Int): Future[SortedMap[Key, Value]]

  def delete(key: Key): Future[Option[Value]]
}

class CassandraKeyValueStore[Key, Value](cassandraClient: CassandraClient, config: Config)(implicit
    executionContext: ExecutionContext,
    actorMaterializer: ActorMaterializer,
    keySerde: Serde[Key],
    valueSerde: Serde[Value]
) extends KeyValueStore[Key, Value]
    with KeyValueLogging {
  private val keyspace = config.getString("keyspace")
  private val table = config.getString("table")

  Await.ready(createTableIfNotExists(), Duration.Inf)

  override def add(value: Value): Future[Option[(Key, Value)]] =
    Future.failed(new UnsupportedOperationException)

  override def update(key: Key, value: Value): Future[Option[Value]] =
    add(key, value).map(_ => Some(value))

  override def add(key: Key, value: Value): Future[Unit] = {
    Source
      .single((key, value))
      .runWith(
        cassandraClient
          .createSink[(Key, Value)](
            s"""INSERT
           |INTO $keyspace.$table(
           |  key,
           |  value
           |) VALUES (?, ?)""".stripMargin
          ) {
            case ((key, value), s) =>
              s.bind(
                ByteBuffer.wrap(keySerde.valueToBytes(key).toArray),
                ByteBuffer.wrap(valueSerde.valueToBytes(value).toArray)
              )
          }
      )
      .map(_ => ())
  }

  override def retrieve(key: Key): Future[Option[Value]] = {
    val query = s"""SELECT value
                   |FROM $keyspace.$table
                   |WHERE key = ?""".stripMargin

    cassandraClient
      .createSource(
        query,
        List(ByteBuffer.wrap(keySerde.valueToBytes(key).toArray))
      )
      .map { row =>
        valueSerde.bytesToValue(ByteVector(row.getBytes("value").array()))
      }
      .runFold(List.empty[Value]) {
        case (rows, row) => row :: rows
      }
      .map {
        case Nil         => None
        case head :: Nil => Some(head)
      }
  }

  override def retrieveAll: Future[SortedMap[Key, Value]] =
    Future.failed(new UnsupportedOperationException)

  override def retrievePage(cursor: Option[Key], count: Int): Future[SortedMap[Key, Value]] =
    Future.failed(new UnsupportedOperationException)

  override def delete(key: Key): Future[Option[Value]] = {
    retrieve(key).flatMap(value =>
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
        .map(_ => value)
    )
  }

  private def createTableIfNotExists(): Future[Done] = {
    log.debug("Creating table if not exists", ("keyspace" -> keyspace, "table" -> table))
    cassandraClient.execute(s"""|CREATE TABLE IF NOT EXISTS $keyspace.$table (
                                |  key blob,
                                |  value blob,
                                |  PRIMARY KEY (key)
                                |)""".stripMargin)
  }

  log.debug("Initialized")
}

object ActorKeyValueStore {
  private implicit val keyBytesOrdering: Ordering[ByteVector] =
    Ordering.by((_: ByteVector).toIterable)

  object Add {
    class AkkaSerializer(extendedActorSystem: ExtendedActorSystem)
        extends ResourceAvroSerializer[Add](extendedActorSystem, currentVersion = 1)
  }

  final case class Add(key: ByteVector, value: ByteVector)

  final case class Retrieve(key: ByteVector)

  case object RetrieveAll

  case class RetrievePage(key: Option[ByteVector], count: Int)

  object Delete {
    class AkkaSerializer(extendedActorSystem: ExtendedActorSystem)
        extends ResourceAvroSerializer[Delete](extendedActorSystem, currentVersion = 1)
  }

  final case class Delete(key: ByteVector)

  object State {
    def zero: State =
      State(SortedMap.empty[ByteVector, ByteVector])

    class AkkaSerializer(extendedActorSystem: ExtendedActorSystem)
        extends ResourceAvroSerializer[Delete](extendedActorSystem, currentVersion = 1)
  }

  final case class State(values: SortedMap[ByteVector, ByteVector]) {
    def add(key: ByteVector, value: ByteVector): State =
      copy(values = values + ((key, value)))

    def retrieveAll: SortedMap[ByteVector, ByteVector] = values

    def retrieve(key: ByteVector): Option[ByteVector] = values.get(key)

    def retrievePage(key: Option[ByteVector], count: Int): Map[ByteVector, ByteVector] =
      key
        .fold(values) { k => values.from(k) }
        .take(count)

    def delete(key: ByteVector): State = copy(values = values - key)
  }

  private object EventSourcedActor {
    def props(persistenceId: String): Props =
      Props(new EventSourcedActor(persistenceId))
  }

  private class EventSourcedActor(override val persistenceId: String, snapshotInterval: Int = 1000)
      extends PersistentActor {
    private var state = State.zero

    override val receiveRecover: Receive = {
      case SnapshotOffer(_, snapshot: State) => state = snapshot

      case RecoveryCompleted => ()

      case Delete(key: ByteVector) => state = state.delete(key)

      case Add(key: ByteVector, value: ByteVector) => state = state.add(key, value)
    }

    override val receiveCommand: Receive = {
      case event @ Add(key: ByteVector, value: ByteVector) =>
        persist(event) { _ =>
          state = state.add(key, value)
          saveSnapshotIfNeeded()
          sender() ! (())
        }

      case RetrieveAll => sender() ! state.retrieveAll

      case RetrievePage(key: Option[ByteVector], count: Int) =>
        sender() ! state.retrievePage(key, count)

      case Retrieve(key: ByteVector) => sender() ! state.retrieve(key)

      case event @ Delete(key: ByteVector) =>
        persist(event) { _ =>
          val value = state.retrieve(key)
          state = state.delete(key)
          saveSnapshotIfNeeded()
          sender() ! value
        }
    }

    private def saveSnapshotIfNeeded(): Unit =
      if (lastSequenceNr % snapshotInterval == 0 && lastSequenceNr != 0)
        saveSnapshot(state)
  }

}

class InMemoryKeyValueStore[Key: Ordering, Value](initialValues: Map[Key, Value]) extends KeyValueStore[Key, Value] {
  private var valuesByKey = SortedMap(initialValues.toSeq: _*)

  override def add(value: Value): Future[Option[(Key, Value)]] = ???

  override def update(key: Key, value: Value): Future[Option[Value]] = ???

  override def add(key: Key, value: Value): Future[Unit] =
    Future.successful {
      valuesByKey = valuesByKey.updated(key, value)
    }

  override def retrieveAll: Future[SortedMap[Key, Value]] = Future.successful { valuesByKey }

  override def retrieve(key: Key): Future[Option[Value]] =
    Future.successful {
      valuesByKey.get(key)
    }

  override def retrievePage(key: Option[Key], count: Int): Future[SortedMap[Key, Value]] =
    Future.successful {
      key
        .fold(valuesByKey) { k => valuesByKey.from(k) }
        .take(count)
    }

  override def delete(key: Key): Future[Option[Value]] =
    Future.successful {
      val value = valuesByKey.get(key)
      valuesByKey = valuesByKey - key
      value
    }
}

class ActorKeyValueStore[Key: Ordering, Value](persistenceId: String)(implicit
    executionContext: ExecutionContext,
    actorRefFactory: ActorRefFactory,
    keySerde: Serde[Key],
    valueSerde: Serde[Value]
) extends KeyValueStore[Key, Value]
    with Shutdownable {

  import ActorKeyValueStore._
  import akka.pattern.ask

  private implicit val askTimeout: Timeout = Timeout(10.seconds)

  private val actor =
    actorRefFactory.actorOf(EventSourcedActor.props(persistenceId))

  def store: Sink[(Key, Value), NotUsed] =
    Sink
      .foldAsync[Unit, (Key, Value)](()) {
        case (_, (key, value)) =>
          add(key, value)
      }
      .mapMaterializedValue(_ => NotUsed)

  override def add(value: Value): Future[Option[(Key, Value)]] = ???

  override def update(key: Key, value: Value): Future[Option[Value]] =
    delete(key).flatMap(_ => add(key, value)).map(_ => Some(value))

  override def add(key: Key, value: Value): Future[Unit] =
    (actor ? Add(
      keySerde.valueToBytes(key),
      valueSerde.valueToBytes(value)
    )).mapTo[Unit]

  override def retrieve(key: Key): Future[Option[Value]] =
    (actor ? Retrieve(keySerde.valueToBytes(key)))
      .mapTo[Option[ByteVector]]
      .map(_.map(value => valueSerde.bytesToValue(value)))

  override def retrieveAll: Future[SortedMap[Key, Value]] =
    (actor ? RetrieveAll)
      .mapTo[SortedMap[ByteVector, ByteVector]]
      .map(_.map {
        case (binaryKey, binaryValue) =>
          (keySerde.bytesToValue(binaryKey), valueSerde.bytesToValue(binaryValue))
      })

  override def retrievePage(cursor: Option[Key], count: Int): Future[SortedMap[Key, Value]] =
    (actor ? RetrievePage(cursor.map(keySerde.valueToBytes), count))
      .mapTo[SortedMap[ByteVector, ByteVector]]
      .map(_.map {
        case (binaryKey, binaryValue) =>
          (keySerde.bytesToValue(binaryKey), valueSerde.bytesToValue(binaryValue))
      })

  override def delete(key: Key): Future[Option[Value]] =
    (actor ? Delete(keySerde.valueToBytes(key))).mapTo[Option[Value]]

  override def shutdown(): Unit = actorRefFactory.stop(actor)
}
