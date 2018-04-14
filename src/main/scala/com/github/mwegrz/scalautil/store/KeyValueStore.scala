package com.github.mwegrz.scalautil.store

import akka.actor.{ ActorRefFactory, Props }
import akka.persistence.{ PersistentActor, RecoveryCompleted, SnapshotOffer }
import akka.util.Timeout
import com.github.mwegrz.app.Shutdownable

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
  private case class Store[Key, Value](key: Key, value: Value)

  private case object RetrieveAll

  private case class RetrievePage[Key](key: Option[Key], count: Int)

  private case class Retrieve[Key](key: Key)

  private case object RemoveAll

  private case class Remove[Key](key: Key)

  private object State {
    def zero[Key: Ordering, Value]: State[Key, Value] =
      State(SortedMap.empty[Key, Value])
  }

  private case class State[Key: Ordering, Value](values: SortedMap[Key, Value]) {
    def store(key: Key, value: Value): State[Key, Value] = copy(values = values + ((key, value)))

    def retrieveAll: SortedMap[Key, Value] = values

    def retrieve(key: Key): Option[Value] = values.get(key)

    def retrievePage(key: Option[Key], count: Int): SortedMap[Key, Value] =
      key
        .fold(values) { k =>
          values.from(k)
        }
        .take(count)

    def removeAll: State[Key, Value] = copy(values = SortedMap.empty[Key, Value])

    def removeByKey1(key: Key): State[Key, Value] = copy(values = values - key)
  }

  private object EventSourcedActor {
    def props[Key: Ordering, Value](persistenceId: String): Props =
      Props(new EventSourcedActor[Key, Value](persistenceId))
  }

  private class EventSourcedActor[Key: Ordering, Value](override val persistenceId: String,
                                                        snapshotInterval: Int = 1000)
      extends PersistentActor {
    private var state = State.zero[Key, Value]

    override val receiveRecover: Receive = {
      case SnapshotOffer(_, snapshot: State[Key, Value]) => state = snapshot

      case RecoveryCompleted => ()

      case Remove(key: Key @unchecked) => state = state.removeByKey1(key)

      case Store(key: Key @unchecked, value: Value @unchecked) => state = state.store(key, value)
    }

    override val receiveCommand: Receive = {
      case event @ Store(key: Key @unchecked, value: Value @unchecked) =>
        persist(event) { _ =>
          state = state.store(key, value)
          saveSnapshotIfNeeded()
          sender() ! ()
        }

      case RetrieveAll => sender() ! state.retrieveAll

      case RetrievePage(key: Option[Key] @unchecked, count: Int) => sender() ! state.retrievePage(key, count)

      case Retrieve(key: Key @unchecked) => sender() ! state.retrieve(key)

      case event @ RemoveAll =>
        persist(event) { _ =>
          state = state.removeAll
          saveSnapshotIfNeeded()
          sender() ! ()
        }

      case event @ Remove(key: Key @unchecked) =>
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

class ActorKeyValueStore[Key: Ordering, Value](persistenceId: String)(implicit executionContext: ExecutionContext,
                                                                      actorRefFactory: ActorRefFactory)
    extends KeyValueStore[Key, Value]
    with Shutdownable {

  import ActorKeyValueStore._
  import akka.pattern.ask

  private implicit val askTimeout: Timeout = Timeout(10.seconds)

  private val actor =
    actorRefFactory.actorOf(EventSourcedActor.props[Key, Value](persistenceId))

  override def store(key: Key, value: Value): Future[Unit] = (actor ? Store(key, value)).mapTo[Unit]

  override def retrieveAll: Future[SortedMap[Key, Value]] =
    (actor ? RetrieveAll).mapTo[SortedMap[Key, Value]]

  override def retrievePage(key: Option[Key], count: Int): Future[SortedMap[Key, Value]] =
    (actor ? RetrievePage(key, count)).mapTo[SortedMap[Key, Value]]

  override def retrieve(key: Key): Future[Option[Value]] =
    (actor ? Retrieve(key)).mapTo[Option[Value]]

  override def removeAll(): Future[Unit] =
    (actor ? RemoveAll).mapTo[Unit]

  override def remove(key: Key): Future[Unit] =
    (actor ? Remove(key)).mapTo[Unit]

  override def shutdown(): Unit = actorRefFactory.stop(actor)
}
