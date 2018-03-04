package com.github.mwegrz.scalautil.store

import akka.actor.{ ActorRefFactory, Props }
import akka.persistence.{ PersistentActor, RecoveryCompleted, SnapshotOffer }
import akka.util.Timeout
import com.github.mwegrz.app.Shutdownable

import scala.concurrent.{ ExecutionContext, Future }
import scala.concurrent.duration._

trait IndexedStore3[Value, Key1, Key2, Key3] {
  def store(value: Value): Future[Unit]

  def retrieveByKey1(key: Key1): Future[Set[Value]]

  def retrieveByKey2(key: Key2): Future[Set[Value]]

  def retrieveByKey3(key: Key3): Future[Set[Value]]

  def removeByKey1(key: Key1): Future[Unit]

  def removeByKey2(key: Key2): Future[Unit]

  def removeByKey3(key: Key3): Future[Unit]
}

object ActorIndexedStore3 {
  sealed trait Event

  private sealed trait Command

  private case class Store[Value](value: Value) extends Command

  private case class RetrieveByKey1[Key1](key: Key1) extends Command

  private case class RetrieveByKey2[Key2](key: Key2) extends Command

  private case class RetrieveByKey3[Key3](key: Key3) extends Command

  private case class RemoveByKey1[Key1](key: Key1) extends Command

  private case class RemoveByKey2[Key2](key: Key2) extends Command

  private case class RemoveByKey3[Key3](key: Key3) extends Command

  private object State {
    def zero[Value, Key1, Key2, Key3](indexDef1: Value => Key1,
                                      indexDef2: Value => Key2,
                                      indexDef3: Value => Key3): State[Value, Key1, Key2, Key3] =
      State(Set.empty, indexDef1, indexDef2, indexDef3)
  }

  private case class State[Value, Key1, Key2, Key3](values: Set[Value],
                                                    indexDef1: Value => Key1,
                                                    indexDef2: Value => Key2,
                                                    indexDef3: Value => Key3) {
    private val index1 = values.groupBy(indexDef1)
    private val index2 = values.groupBy(indexDef2)
    private val index3 = values.groupBy(indexDef3)

    def updated(value: Value): State[Value, Key1, Key2, Key3] = copy(values = values + value)

    def getByB(key: Key1): Set[Value] = index1.getOrElse(key, Set.empty)

    def getByC(key: Key2): Set[Value] = index2.getOrElse(key, Set.empty)

    def getByD(key: Key3): Set[Value] = index3.getOrElse(key, Set.empty)

    def deleteByB(key: Key1): State[Value, Key1, Key2, Key3] = copy(values = values -- index1.getOrElse(key, Set.empty))

    def deleteByC(key: Key2): State[Value, Key1, Key2, Key3] = copy(values = values -- index2.getOrElse(key, Set.empty))

    def deleteByD(key: Key3): State[Value, Key1, Key2, Key3] = copy(values = values -- index3.getOrElse(key, Set.empty))
  }

  private object EventSourcedActor {
    def props[Value, Key1, Key2, Key3](persistenceId: String,
                                       indexDef1: Value => Key1,
                                       indexDef2: Value => Key2,
                                       indexDef3: Value => Key3): Props =
      Props(new EventSourcedActor[Value, Key1, Key2, Key3](persistenceId, indexDef1, indexDef2, indexDef3))
  }

  private class EventSourcedActor[Value, Key1, Key2, Key3](override val persistenceId: String,
                                                           indexDef1: Value => Key1,
                                                           indexDef2: Value => Key2,
                                                           indexDef3: Value => Key3,
                                                           snapshotInterval: Int = 1000)
      extends PersistentActor {
    private var state = State.zero[Value, Key1, Key2, Key3](indexDef1, indexDef2, indexDef3)

    override val receiveRecover: Receive = {
      case SnapshotOffer(_, snapshot: State[Value, Key1, Key2, Key3]) => state = snapshot

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

      case RetrieveByKey2(key: Key2 @unchecked) => sender() ! state.getByC(key)

      case RetrieveByKey3(key: Key3 @unchecked) => sender() ! state.getByD(key)

      case RemoveByKey1(key: Key1 @unchecked) => sender() ! ()

      case RemoveByKey2(key: Key1 @unchecked) => sender() ! ()

      case RemoveByKey3(key: Key1 @unchecked) => sender() ! ()
    }

    private def updateState(value: Value): Unit = state = state.updated(value)

    private def saveSnapshotIfNeeded(): Unit =
      if (lastSequenceNr % snapshotInterval == 0 && lastSequenceNr != 0)
        saveSnapshot(state)
  }

}

class ActorIndexedStore3[Value, Key1, Key2, Key3](
    persistenceId: String,
    indexDef1: Value => Key1,
    indexDef2: Value => Key2,
    indexDef3: Value => Key3)(implicit executionContext: ExecutionContext, actorRefFactory: ActorRefFactory)
    extends IndexedStore3[Value, Key1, Key2, Key3]
    with Shutdownable {

  import ActorIndexedStore3._
  import akka.pattern.ask

  private implicit val askTimeout: Timeout = Timeout(100.milliseconds)

  private val actor =
    actorRefFactory.actorOf(
      EventSourcedActor.props[Value, Key1, Key2, Key3](persistenceId, indexDef1, indexDef2, indexDef3))

  override def store(value: Value): Future[Unit] = (actor ? Store(value)).mapTo[Unit]

  override def retrieveByKey1(key: Key1): Future[Set[Value]] =
    (actor ? RetrieveByKey1(key)).mapTo[Set[Value]]

  override def retrieveByKey2(key: Key2): Future[Set[Value]] =
    (actor ? RetrieveByKey2(key)).mapTo[Set[Value]]

  override def retrieveByKey3(key: Key3): Future[Set[Value]] =
    (actor ? RetrieveByKey3(key)).mapTo[Set[Value]]

  override def removeByKey1(key: Key1): Future[Unit] =
    (actor ? RemoveByKey1(key)).mapTo[Unit]

  override def removeByKey2(key: Key2): Future[Unit] =
    (actor ? RemoveByKey1(key)).mapTo[Unit]

  override def removeByKey3(key: Key3): Future[Unit] =
    (actor ? RemoveByKey1(key)).mapTo[Unit]

  override def shutdown(): Unit = actorRefFactory.stop(actor)
}
