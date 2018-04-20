package com.github.mwegrz.scalautil.store

import akka.actor.{ ActorRefFactory, Props }
import akka.persistence.{ PersistentActor, RecoveryCompleted, SnapshotOffer }
import akka.util.Timeout
import com.github.mwegrz.app.Shutdownable
import com.github.mwegrz.scalautil.akka.serialization.AvroSerializer
import com.sksamuel.avro4s._
import org.apache.avro.Schema

import scala.collection.immutable.SortedMap
import scala.concurrent.{ ExecutionContext, Future }
import scala.concurrent.duration._

trait KeyValueStore[Key, Value] {
  def store(key: Key, value: Value): Future[Unit]

  def retrieve(key: Key): Future[Option[Value]]

  def retrieveAll: Future[SortedMap[Key, Value]]

  def retrievePage(key: Option[Key], count: Int): Future[SortedMap[Key, Value]]

  def removeAll(): Future[Unit]

  def remove(key: Key): Future[Unit]
}

object ActorKeyValueStore {
  class StoreSerializer extends AvroSerializer[Store] {
    override protected lazy val currentVersion: Int = 1

    override protected def versionToWriterSchema: PartialFunction[Int, Schema] = {
      case `currentVersion` => SchemaFor[Store]()
    }

    override def identifier: Int = 666
  }

  private implicit val keyBytesOrdering: Ordering[KeyBytes] = Ordering.by((_: Array[Byte]).toIterable)

  private type KeyBytes = Array[Byte]

  private type ValueBytes = Array[Byte]

  case class Store(key: KeyBytes, value: ValueBytes)

  case object RetrieveAll

  case class RetrievePage(key: Option[KeyBytes], count: Int)

  case class Retrieve(key: KeyBytes)

  case object RemoveAll

  case class Remove(key: KeyBytes)

  private object State {
    def zero: State =
      State(SortedMap.empty[KeyBytes, ValueBytes])
  }

  private case class State(values: SortedMap[KeyBytes, ValueBytes]) {
    def store(key: KeyBytes, value: ValueBytes): State = copy(values = values + ((key, value)))

    def retrieveAll: SortedMap[KeyBytes, ValueBytes] = values

    def retrieve(key: KeyBytes): Option[ValueBytes] = values.get(key)

    def retrievePage(key: Option[KeyBytes], count: Int): SortedMap[KeyBytes, ValueBytes] =
      key
        .fold(values) { k =>
          values.from(k)
        }
        .take(count)

    def removeAll: State = copy(values = SortedMap.empty[KeyBytes, ValueBytes])

    def removeByKey1(key: KeyBytes): State = copy(values = values - key)
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

      case Remove(key: KeyBytes) => state = state.removeByKey1(key)

      case Store(key: KeyBytes, value: ValueBytes) => state = state.store(key, value)
    }

    override val receiveCommand: Receive = {
      case event @ Store(key: KeyBytes, value: ValueBytes) =>
        persist(event) { _ =>
          state = state.store(key, value)
          saveSnapshotIfNeeded()
          sender() ! ()
        }

      case RetrieveAll => sender() ! state.retrieveAll

      case RetrievePage(key: Option[KeyBytes], count: Int) => sender() ! state.retrievePage(key, count)

      case Retrieve(key: KeyBytes) => sender() ! state.retrieve(key)

      case event @ RemoveAll =>
        persist(event) { _ =>
          state = state.removeAll
          saveSnapshotIfNeeded()
          sender() ! ()
        }

      case event @ Remove(key: KeyBytes) =>
        persist(event) { _ =>
          state = state.removeByKey1(key)
          saveSnapshotIfNeeded()
          sender() ! ()
        }
    }

    private def saveSnapshotIfNeeded(): Unit =
      if (lastSequenceNr % snapshotInterval == 0 && lastSequenceNr != 0)
        saveSnapshot(state)
  }

}

class InMemoryKeyValueStore[Key: Ordering, Value](initialValues: Map[Key, Value]) extends KeyValueStore[Key, Value] {
  private var valuesByKey = SortedMap(initialValues.toSeq: _*)

  override def store(key: Key, value: Value): Future[Unit] = Future.successful {
    valuesByKey = valuesByKey.updated(key, value)
  }

  override def retrieveAll: Future[SortedMap[Key, Value]] = Future.successful { valuesByKey }

  override def retrieve(key: Key): Future[Option[Value]] = Future.successful { valuesByKey.get(key) }

  override def retrievePage(key: Option[Key], count: Int): Future[SortedMap[Key, Value]] = Future.successful {
    key
      .fold(valuesByKey) { k =>
        valuesByKey.from(k)
      }
      .take(count)
  }

  override def removeAll(): Future[Unit] = Future.successful { valuesByKey = SortedMap.empty[Key, Value] }

  override def remove(key: Key): Future[Unit] = Future.successful { valuesByKey = valuesByKey - key }
}

class ActorKeyValueStore[Key: Ordering, Value](persistenceId: String)(
    keyToBinary: Key => Array[Byte],
    valueToBinary: Value => Array[Byte],
    binaryToKey: Array[Byte] => Key,
    binaryToValue: Array[Byte] => Value)(implicit executionContext: ExecutionContext, actorRefFactory: ActorRefFactory)
    extends KeyValueStore[Key, Value]
    with Shutdownable {

  import ActorKeyValueStore._
  import akka.pattern.ask

  private implicit val askTimeout: Timeout = Timeout(10.seconds)

  private val actor =
    actorRefFactory.actorOf(EventSourcedActor.props(persistenceId))

  override def store(key: Key, value: Value): Future[Unit] =
    (actor ? Store(keyToBinary(key), valueToBinary(value))).mapTo[Unit]

  override def retrieveAll: Future[SortedMap[Key, Value]] =
    (actor ? RetrieveAll)
      .mapTo[SortedMap[Array[Byte], Array[Byte]]]
      .map(_.map { case (binaryKey, binaryValue) => (binaryToKey(binaryKey), binaryToValue(binaryValue)) })

  override def retrievePage(key: Option[Key], count: Int): Future[SortedMap[Key, Value]] =
    (actor ? RetrievePage(key.map(keyToBinary), count))
      .mapTo[SortedMap[Array[Byte], Array[Byte]]]
      .map(_.map { case (binaryKey, binaryValue) => (binaryToKey(binaryKey), binaryToValue(binaryValue)) })

  override def retrieve(key: Key): Future[Option[Value]] =
    (actor ? Retrieve(keyToBinary(key)))
      .mapTo[Option[Array[Byte]]]
      .map(_.map(binaryToValue))

  override def removeAll(): Future[Unit] =
    (actor ? RemoveAll).mapTo[Unit]

  override def remove(key: Key): Future[Unit] =
    (actor ? Remove(keyToBinary(key))).mapTo[Unit]

  override def shutdown(): Unit = actorRefFactory.stop(actor)
}
