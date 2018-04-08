package com.github.mwegrz.scalautil.store

import akka.actor.{ ActorRefFactory, Props }
import akka.persistence.{ PersistentActor, RecoveryCompleted, SnapshotOffer }
import akka.util.Timeout
import com.github.mwegrz.app.Shutdownable

import scala.concurrent.{ ExecutionContext, Future }
import scala.concurrent.duration._

trait KeyValueStore[Key, Value] {
  def store(key: Key, value: Value): Future[Unit]

  def retrieveAll: Future[Map[Key, Value]]

  def retrieveByKey(key: Key): Future[Option[Value]]

  def removeAll(): Future[Unit]

  def removeByKey(key: Key): Future[Unit]
}

object ActorKeyValueStore {
  private case class Store[Key, Value](key: Key, value: Value)

  private case object RetrieveAll

  private case class RetrieveByKey[Key](key: Key)

  private case object RemoveAll

  private case class RemoveByKey[Key](key: Key)

  private object State {
    def zero[Value, Key]: State[Value, Key] =
      State(Map.empty)
  }

  private case class State[Value, Key](values: Map[Key, Value]) {
    def store(key: Key, value: Value): State[Value, Key] = copy(values = values + ((key, value)))

    def retrieveAll: Map[Key, Value] = values

    def retrieveByKey(key: Key): Option[Value] = values.get(key)

    def removeAll: State[Value, Key] = copy(values = Map.empty)

    def removeByKey1(key: Key): State[Value, Key] = copy(values = values - key)
  }

  private object EventSourcedActor {
    def props[Value, Key](persistenceId: String): Props =
      Props(new EventSourcedActor[Value, Key](persistenceId))
  }

  private class EventSourcedActor[Value, Key](override val persistenceId: String, snapshotInterval: Int = 1000)
      extends PersistentActor {
    private var state = State.zero[Value, Key]

    override val receiveRecover: Receive = {
      case SnapshotOffer(_, snapshot: State[Value, Key]) => state = snapshot

      case RecoveryCompleted => ()

      case RemoveByKey(key: Key @unchecked) => state = state.removeByKey1(key)

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

      case RetrieveByKey(key: Key @unchecked) => sender() ! state.retrieveByKey(key)

      case event @ RemoveAll =>
        persist(event) { _ =>
          state = state.removeAll
          saveSnapshotIfNeeded()
          sender() ! ()
        }

      case event @ RemoveByKey(key: Key @unchecked) =>
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

class InMemoryKeyValueStore[Key, Value](initialValues: Map[Key, Value]) extends KeyValueStore[Key, Value] {
  private var valuesByKey = initialValues

  def store(key: Key, value: Value): Future[Unit] = Future.successful { valuesByKey = valuesByKey.updated(key, value) }

  def retrieveAll: Future[Map[Key, Value]] = Future.successful { valuesByKey.map(identity) }

  def retrieveByKey(key: Key): Future[Option[Value]] = Future.successful { valuesByKey.get(key) }

  def removeAll(): Future[Unit] = Future.successful { valuesByKey = Map.empty }

  def removeByKey(key: Key): Future[Unit] = Future.successful { valuesByKey = valuesByKey - key }
}

class ActorKeyValueStore[Key, Value](persistenceId: String)(implicit executionContext: ExecutionContext,
                                                            actorRefFactory: ActorRefFactory)
    extends KeyValueStore[Key, Value]
    with Shutdownable {

  import ActorKeyValueStore._
  import akka.pattern.ask

  private implicit val askTimeout: Timeout = Timeout(10.seconds)

  private val actor =
    actorRefFactory.actorOf(EventSourcedActor.props[Value, Key](persistenceId))

  override def store(key: Key, value: Value): Future[Unit] = (actor ? Store(key, value)).mapTo[Unit]

  override def retrieveAll: Future[Map[Key, Value]] =
    (actor ? RetrieveAll).mapTo[Map[Key, Value]]

  override def retrieveByKey(key: Key): Future[Option[Value]] =
    (actor ? RetrieveByKey(key)).mapTo[Option[Value]]

  override def removeAll(): Future[Unit] =
    (actor ? RemoveAll).mapTo[Unit]

  override def removeByKey(key: Key): Future[Unit] =
    (actor ? RemoveByKey(key)).mapTo[Unit]

  override def shutdown(): Unit = actorRefFactory.stop(actor)
}
