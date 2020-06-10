package com.github.mwegrz.scalautil.store

import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ ActorRef, Behavior }
import akka.persistence.typed.scaladsl.{ Effect, EventSourcedBehavior, RetentionCriteria }
import akka.persistence.typed.{ ExpectingReply, PersistenceId }

trait ValueStore

object ValueStoreActor {
  sealed trait Command[Value]

  final case class Update[Value](
      requestId: Long,
      value: Value,
      override val replyTo: ActorRef[Unit]
  ) extends Command[Value]
      with ExpectingReply[Unit]

  final case class Retrieve[Value](
      requestId: Long,
      override val replyTo: ActorRef[Option[Value]]
  ) extends Command[Value]
      with ExpectingReply[Option[Value]]

  sealed trait Event[Value]

  final case class Updated[Value](value: Value) extends Event[Value]

  object State {
    def empty[Value]: State[Value] = State(value = None)
  }

  final case class State[Value](value: Option[Value])

  def behavior[Value](
      initialState: State[Value],
      name: String
  ): Behavior[Command[Value]] =
    Behaviors.setup { _ =>
      val commandHandler: (State[Value], Command[Value]) => Effect[Event[Value], State[Value]] = { (state, command) =>
        command match {
          case Retrieve(_, replyTo) =>
            Effect.none.thenRun(_ => replyTo.tell(state.value))

          case Update(_, value, replyTo) =>
            Effect
              .persist(Updated(value))
              .thenRun(_ => replyTo.tell(()))
        }
      }

      val eventHandler: (State[Value], Event[Value]) => State[Value] = { (state, event) =>
        event match {
          case Updated(value) => state.copy(value = Some(value))
        }
      }

      EventSourcedBehavior[Command[Value], Event[Value], State[Value]](
        persistenceId = PersistenceId(name),
        emptyState = initialState,
        commandHandler = commandHandler,
        eventHandler = eventHandler
      ).withRetention(
        RetentionCriteria.snapshotEvery(numberOfEvents = 1000, keepNSnapshots = 1).withDeleteEventsOnSnapshot
      )
    }
}
