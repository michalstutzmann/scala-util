package com.github.mwegrz.scalautil.store

import akka.actor.{ ActorRefFactory, Props }
import akka.persistence.{ PersistentActor, RecoveryCompleted, SnapshotOffer }
import akka.util.Timeout
import com.github.mwegrz.app.Shutdownable

import scala.concurrent.{ ExecutionContext, Future }
import scala.concurrent.duration._

trait IndexedStore1[Value, Key1] {
  def store(value: Value): Future[Unit]

  def retrieveByKey1(key: Key1): Future[Set[Value]]

  def removeByKey1(key: Key1): Future[Unit]
}

object ActorIndexedStore1 {
  private case class Store[Value](value: Value)

  private case class RetrieveByKey1[Key1](key: Key1)

  private case class RemoveByKey1[Key1](key: Key1)

  private object State {
    def zero[Value, Key1](indexDef1: Value => Key1): State[Value, Key1] =
      State(Set.empty, indexDef1)
  }

  private case class State[Value, Key1](values: Set[Value], indexDef1: Value => Key1) {
    private val index1 = values.groupBy(indexDef1)

    def updated(value: Value): State[Value, Key1] = copy(values = values + value)

    def getByB(key: Key1): Set[Value] = index1.getOrElse(key, Set.empty)

    def deleteByB(key: Key1): State[Value, Key1] = copy(values = values -- index1.getOrElse(key, Set.empty))
  }

  private object EventSourcedActor {
    def props[Value, Key1](persistenceId: String, indexDef1: Value => Key1): Props =
      Props(new EventSourcedActor[Value, Key1](persistenceId, indexDef1))
  }

  private class EventSourcedActor[Value, Key1](override val persistenceId: String,
                                               indexDef1: Value => Key1,
                                               snapshotInterval: Int = 1000)
      extends PersistentActor {
    private var state = State.zero[Value, Key1](indexDef1)

    override val receiveRecover: Receive = {
      case SnapshotOffer(_, snapshot: State[Value, Key1]) => state = snapshot

      case RecoveryCompleted => ()

      case value: Value @unchecked => updateState(value)
    }

    override val receiveCommand: Receive = {
      case Store(value: Value @unchecked) =>
        persist(value) { event =>
          updateState(event)
          saveSnapshotIfNeeded()
        }
      case RetrieveByKey1(key: Key1 @unchecked) => sender() ! state.getByB(key)

      case RemoveByKey1(key: Key1 @unchecked) => sender() ! ()
    }

    private def updateState(value: Value): Unit = state = state.updated(value)

    private def saveSnapshotIfNeeded(): Unit =
      if (lastSequenceNr % snapshotInterval == 0 && lastSequenceNr != 0)
        saveSnapshot(state)
  }

}

class ActorIndexedStore1[Value, Key1](persistenceId: String, indexDef1: Value => Key1)(
    implicit executionContext: ExecutionContext,
    actorRefFactory: ActorRefFactory)
    extends IndexedStore1[Value, Key1]
    with Shutdownable {

  import ActorIndexedStore1._
  import akka.pattern.ask

  private implicit val askTimeout: Timeout = Timeout(10.seconds)

  private val actor =
    actorRefFactory.actorOf(EventSourcedActor.props[Value, Key1](persistenceId, indexDef1))

  override def store(value: Value): Future[Unit] = (actor ? Store(value)).mapTo[Unit]

  override def retrieveByKey1(key: Key1): Future[Set[Value]] =
    (actor ? RetrieveByKey1(key)).mapTo[Set[Value]]

  override def removeByKey1(key: Key1): Future[Unit] =
    (actor ? RemoveByKey1(key)).mapTo[Unit]

  override def shutdown(): Unit = actorRefFactory.stop(actor)
}
