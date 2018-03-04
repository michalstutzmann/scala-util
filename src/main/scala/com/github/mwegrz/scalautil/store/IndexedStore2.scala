package com.github.mwegrz.scalautil.store

import akka.actor.{ ActorRefFactory, Props }
import akka.persistence.{ PersistentActor, RecoveryCompleted, SnapshotOffer }
import akka.util.Timeout
import com.github.mwegrz.app.Shutdownable

import scala.concurrent.{ ExecutionContext, Future }
import scala.concurrent.duration._

trait IndexedStore2[Value, Key1, Key2] {
  def store(value: Value): Future[Unit]

  def retrieveByKey1(key: Key1): Future[Set[Value]]

  def retrieveByKey2(key: Key2): Future[Set[Value]]

  def removeByKey1(key: Key1): Future[Unit]

  def removeByKey2(key: Key2): Future[Unit]
}

object ActorIndexedStore2 {
  private case class Store[Value](value: Value)

  private case class RetrieveByKey1[Key1](key: Key1)

  private case class RetrieveByKey2[Key2](key: Key2)

  private case class RemoveByKey1[Key1](key: Key1)

  private case class RemoveByKey2[Key2](key: Key2)

  private object State {
    def zero[Value, Key1, Key2](indexDef1: Value => Key1, indexDef2: Value => Key2): State[Value, Key1, Key2] =
      State(Set.empty, indexDef1, indexDef2)
  }

  private case class State[Value, Key1, Key2](values: Set[Value], indexDef1: Value => Key1, indexDef2: Value => Key2) {
    private val index1 = values.groupBy(indexDef1)
    private val index2 = values.groupBy(indexDef2)

    def updated(value: Value): State[Value, Key1, Key2] = copy(values = values + value)

    def getByB(key: Key1): Set[Value] = index1.getOrElse(key, Set.empty)

    def getByC(key: Key2): Set[Value] = index2.getOrElse(key, Set.empty)

    def deleteByB(key: Key1): State[Value, Key1, Key2] = copy(values = values -- index1.getOrElse(key, Set.empty))

    def deleteByC(key: Key2): State[Value, Key1, Key2] = copy(values = values -- index2.getOrElse(key, Set.empty))
  }

  private object EventSourcedActor {
    def props[Value, Key1, Key2](persistenceId: String, indexDef1: Value => Key1, indexDef2: Value => Key2): Props =
      Props(new EventSourcedActor[Value, Key1, Key2](persistenceId, indexDef1, indexDef2))
  }

  private class EventSourcedActor[Value, Key1, Key2](override val persistenceId: String,
                                                     indexDef1: Value => Key1,
                                                     indexDef2: Value => Key2,
                                                     snapshotInterval: Int = 1000)
      extends PersistentActor {
    private var state = State.zero[Value, Key1, Key2](indexDef1, indexDef2)

    override val receiveRecover: Receive = {
      case SnapshotOffer(_, snapshot: State[Value, Key1, Key2]) => state = snapshot

      case RecoveryCompleted => ()

      case event: Value @unchecked => updateState(event)
    }

    override val receiveCommand: Receive = {
      case Store(value: Value @unchecked) =>
        persist(value) { event =>
          updateState(event)
          saveSnapshotIfNeeded()
        }
      case RetrieveByKey1(key: Key1 @unchecked) => sender() ! state.getByB(key)

      case RetrieveByKey2(key: Key2 @unchecked) => sender() ! state.getByC(key)

      case RemoveByKey1(key: Key1 @unchecked) => sender() ! ()

      case RemoveByKey2(key: Key1 @unchecked) => sender() ! ()
    }

    private def updateState(event: Value): Unit = state = state.updated(event)

    private def saveSnapshotIfNeeded(): Unit =
      if (lastSequenceNr % snapshotInterval == 0 && lastSequenceNr != 0)
        saveSnapshot(state)
  }

}

class ActorIndexedStore2[Value, Key1, Key2](persistenceId: String, indexDef1: Value => Key1, indexDef2: Value => Key2)(
    implicit executionContext: ExecutionContext,
    actorRefFactory: ActorRefFactory)
    extends IndexedStore2[Value, Key1, Key2]
    with Shutdownable {

  import ActorIndexedStore2._
  import akka.pattern.ask

  private implicit val askTimeout: Timeout = Timeout(100.milliseconds)

  private val actor =
    actorRefFactory.actorOf(EventSourcedActor.props[Value, Key1, Key2](persistenceId, indexDef1, indexDef2))

  override def store(value: Value): Future[Unit] = (actor ? Store(value)).mapTo[Unit]

  override def retrieveByKey1(key: Key1): Future[Set[Value]] =
    (actor ? RetrieveByKey1(key)).mapTo[Set[Value]]

  override def retrieveByKey2(key: Key2): Future[Set[Value]] =
    (actor ? RetrieveByKey2(key)).mapTo[Set[Value]]

  override def removeByKey1(key: Key1): Future[Unit] =
    (actor ? RemoveByKey1(key)).mapTo[Unit]

  override def removeByKey2(key: Key2): Future[Unit] =
    (actor ? RemoveByKey1(key)).mapTo[Unit]

  override def shutdown(): Unit = actorRefFactory.stop(actor)
}
