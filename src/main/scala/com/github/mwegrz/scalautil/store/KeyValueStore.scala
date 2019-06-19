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
import com.github.mwegrz.scalautil.avro4s.codecs._
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
        .fold(values) { k =>
          values.from(k)
        }
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
          state = state.delete(key)
          saveSnapshotIfNeeded()
          sender() ! (())
        }
    }

    private def saveSnapshotIfNeeded(): Unit =
      if (lastSequenceNr % snapshotInterval == 0 && lastSequenceNr != 0)
        saveSnapshot(state)
  }

}

class InMemoryKeyValueStore[Key: Ordering, Value](initialValues: Map[Key, Value]) extends KeyValueStore[Key, Value] {
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
    (actor ? Add(
      ByteVector(keySerde.valueToBinary(key)),
      ByteVector(valueSerde.valueToBinary(value))
    )).mapTo[Unit]

  override def retrieve(key: Key): Future[Option[Value]] =
    (actor ? Retrieve(ByteVector(keySerde.valueToBinary(key))))
      .mapTo[Option[ByteVector]]
      .map(_.map(value => valueSerde.binaryToValue(value.toArray)))

  override def retrieveAll: Future[SortedMap[Key, Value]] =
    (actor ? RetrieveAll)
      .mapTo[SortedMap[ByteVector, ByteVector]]
      .map(_.map {
        case (binaryKey, binaryValue) =>
          (keySerde.binaryToValue(binaryKey.toArray), valueSerde.binaryToValue(binaryValue.toArray))
      })

  override def retrievePage(cursor: Option[Key], count: Int): Future[SortedMap[Key, Value]] =
    (actor ? RetrievePage(cursor.map(keySerde.valueToBinary).map(ByteVector(_)), count))
      .mapTo[SortedMap[ByteVector, ByteVector]]
      .map(_.map {
        case (binaryKey, binaryValue) =>
          (keySerde.binaryToValue(binaryKey.toArray), valueSerde.binaryToValue(binaryValue.toArray))
      })

  override def delete(key: Key): Future[Unit] =
    (actor ? Delete(ByteVector(keySerde.valueToBinary(key)))).mapTo[Unit]

  override def shutdown(): Unit = actorRefFactory.stop(actor)
}
