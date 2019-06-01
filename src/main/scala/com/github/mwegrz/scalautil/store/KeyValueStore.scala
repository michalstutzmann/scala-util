package com.github.mwegrz.scalautil.store

import akka.NotUsed
import akka.actor.{ ActorRefFactory, ExtendedActorSystem, Props }
import akka.persistence.{ PersistentActor, RecoveryCompleted, SnapshotOffer }
import akka.stream.scaladsl.Sink
import akka.util.Timeout
import com.github.mwegrz.app.Shutdownable
import com.github.mwegrz.scalautil.akka.serialization.ResourceAvroSerializer
import com.github.mwegrz.scalautil.serialization.Serde
import com.sksamuel.avro4s._
import com.github.mwegrz.scalautil.circe.coding.{ byteArrayKeyDecoder, byteArrayKeyEncoder }
import com.github.mwegrz.scalautil.avro4s.coding._
import scodec.bits.ByteVector

import scala.collection.immutable.SortedMap
import scala.concurrent.{ ExecutionContext, Future }
import scala.concurrent.duration._

trait KeyValueStore[Key, Value] {
  def add(key: Key, value: Value): Future[Unit]

  def retrieve(key: Key): Future[Option[Value]]

  def retrieveAll: Future[SortedMap[Key, Value]]

  def retrievePage(cursor: Option[Key], count: Int): Future[SortedMap[Key, Value]]

  def delete(key: Key): Future[Unit]
}

object ActorKeyValueStore {
  private implicit val keyBytesOrdering: Ordering[ByteVector] =
    Ordering.by((_: ByteVector).toIterable)

  private type KeyBytes = Array[Byte]

  private type ValueBytes = Array[Byte]

  object Add {
    class AkkaSerializer(extendedActorSystem: ExtendedActorSystem)
        extends ResourceAvroSerializer[Add](extendedActorSystem, currentVersion = 1)
  }

  final case class Add(key: KeyBytes, value: ValueBytes)

  final case class Retrieve(key: KeyBytes)

  case object RetrieveAll

  case class RetrievePage(key: Option[KeyBytes], count: Int)

  object Delete {
    class AkkaSerializer(extendedActorSystem: ExtendedActorSystem)
        extends ResourceAvroSerializer[Delete](extendedActorSystem, currentVersion = 1)
  }

  final case class Delete(key: KeyBytes)

  object State {
    def zero: State =
      State(SortedMap.empty[ByteVector, ValueBytes])

    class AkkaSerializer(extendedActorSystem: ExtendedActorSystem)
        extends ResourceAvroSerializer[Delete](extendedActorSystem, currentVersion = 1)
  }

  final case class State(values: SortedMap[ByteVector, ValueBytes]) {
    def add(key: KeyBytes, value: ValueBytes): State =
      copy(values = values + ((ByteVector(key), value)))

    def retrieveAll: Map[KeyBytes, ValueBytes] = values.map {
      case (key, value) => (key.toArray, value)
    }

    def retrieve(key: KeyBytes): Option[ValueBytes] = values.get(ByteVector(key))

    def retrievePage(key: Option[KeyBytes], count: Int): Map[KeyBytes, ValueBytes] =
      key
        .fold(values) { k =>
          values.from(ByteVector(k))
        }
        .take(count)
        .map { case (key, value) => (key.toArray, value) }

    def delete(key: KeyBytes): State = copy(values = values - ByteVector(key))
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

      case Delete(key: KeyBytes) => state = state.delete(key)

      case Add(key: KeyBytes, value: ValueBytes) => state = state.add(key, value)
    }

    override val receiveCommand: Receive = {
      case event @ Add(key: KeyBytes, value: ValueBytes) =>
        persist(event) { _ =>
          state = state.add(key, value)
          saveSnapshotIfNeeded()
          sender() ! ()
        }

      case RetrieveAll => sender() ! state.retrieveAll

      case RetrievePage(key: Option[KeyBytes], count: Int) =>
        sender() ! state.retrievePage(key, count)

      case Retrieve(key: KeyBytes) => sender() ! state.retrieve(key)

      case event @ Delete(key: KeyBytes) =>
        persist(event) { _ =>
          state = state.delete(key)
          saveSnapshotIfNeeded()
          sender() ! ()
        }
    }

    private def saveSnapshotIfNeeded(): Unit =
      if (lastSequenceNr % snapshotInterval == 0 && lastSequenceNr != 0)
        saveSnapshot(state)
  }

}

class InMemoryKeyValueStore[Key: Ordering, Value](initialValues: Map[Key, Value])
    extends KeyValueStore[Key, Value] {
  private var valuesByKey = SortedMap(initialValues.toSeq: _*)

  override def add(key: Key, value: Value): Future[Unit] = Future.successful {
    valuesByKey = valuesByKey.updated(key, value)
  }

  override def retrieveAll: Future[SortedMap[Key, Value]] = Future.successful { valuesByKey }

  override def retrieve(key: Key): Future[Option[Value]] = Future.successful {
    valuesByKey.get(key)
  }

  override def retrievePage(key: Option[Key], count: Int): Future[SortedMap[Key, Value]] =
    Future.successful {
      key
        .fold(valuesByKey) { k =>
          valuesByKey.from(k)
        }
        .take(count)
    }

  override def delete(key: Key): Future[Unit] = Future.successful {
    valuesByKey = valuesByKey - key
  }
}

class ActorKeyValueStore[Key: Ordering, Value](persistenceId: String)(
    implicit executionContext: ExecutionContext,
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

  override def add(key: Key, value: Value): Future[Unit] =
    (actor ? Add(keySerde.valueToBinary(key), valueSerde.valueToBinary(value))).mapTo[Unit]

  override def retrieve(key: Key): Future[Option[Value]] =
    (actor ? Retrieve(keySerde.valueToBinary(key)))
      .mapTo[Option[Array[Byte]]]
      .map(_.map(valueSerde.binaryToValue))

  override def retrieveAll: Future[SortedMap[Key, Value]] =
    (actor ? RetrieveAll)
      .mapTo[SortedMap[Array[Byte], Array[Byte]]]
      .map(_.map {
        case (binaryKey, binaryValue) =>
          (keySerde.binaryToValue(binaryKey), valueSerde.binaryToValue(binaryValue))
      })

  override def retrievePage(cursor: Option[Key], count: Int): Future[SortedMap[Key, Value]] =
    (actor ? RetrievePage(cursor.map(keySerde.valueToBinary), count))
      .mapTo[SortedMap[Array[Byte], Array[Byte]]]
      .map(_.map {
        case (binaryKey, binaryValue) =>
          (keySerde.binaryToValue(binaryKey), valueSerde.binaryToValue(binaryValue))
      })

  override def delete(key: Key): Future[Unit] =
    (actor ? Delete(keySerde.valueToBinary(key))).mapTo[Unit]

  override def shutdown(): Unit = actorRefFactory.stop(actor)
}
