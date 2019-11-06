package com.github.mwegrz.scalautil.smtp

import akka.actor.{ ActorSystem, ExtendedActorSystem }
import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ ActorRef, Behavior }
import akka.persistence.typed.PersistenceId
import com.sksamuel.avro4s._
import com.github.mwegrz.scalautil.avro4s.codecs._
import akka.persistence.typed.scaladsl.{ Effect, EventSourcedBehavior, RetentionCriteria }
import com.github.mwegrz.scalautil.akka.serialization.ResourceAvroSerializer
import com.github.mwegrz.scalautil.serialization.Serde
import com.typesafe.config.Config
import org.apache.avro.Schema

import scala.concurrent.ExecutionContext
import scala.util.{ Failure, Success }

object SmtpClientActor {
  final case class State()

  sealed trait Command
  final case class SendEmail(requestId: Long, email: Email, replyTo: ActorRef[State]) extends Command

  sealed trait Event

  object EmailSent {
    implicit def serde(implicit actorSystem: ActorSystem): Serde[EmailSent] =
      new AkkaSerializer(actorSystem.asInstanceOf[ExtendedActorSystem])

    class AkkaSerializer(extendedActorSystem: ExtendedActorSystem)
        extends ResourceAvroSerializer[EmailSent](extendedActorSystem, currentVersion = 1)

    lazy val avroSchema: Schema = AvroSchema[EmailSent]
  }

  final case class EmailSent(email: Email) extends Event

  def behavior(config: Config, name: String): Behavior[Command] = Behaviors.setup { context =>
    implicit val executionContext: ExecutionContext = context.executionContext
    val log = context.log
    val smtpClient = SmtpClient(config)

    val commandHandler: (State, Command) => Effect[Event, State] = { (state, command) =>
      command match {
        case SendEmail(_, email, replyTo) =>
          smtpClient.sendEmail(email).onComplete {
            case Success(value)     => log.info(s"E-mail sent: $email")
            case Failure(exception) => log.warning(exception, s"Could not sent e-mail: $email")
          }
          //Effect.persist(EmailSent(email)).thenRun(replyTo.tell)
          Effect.none
      }
    }

    val eventHandler: (State, Event) => State = { (state, event) =>
      event match {
        case EmailSent(email) => state
      }
    }

    EventSourcedBehavior[Command, Event, State](
      persistenceId = PersistenceId(name),
      emptyState = State(),
      commandHandler = commandHandler,
      eventHandler = eventHandler
    ).withRetention(
      RetentionCriteria.snapshotEvery(numberOfEvents = 100, keepNSnapshots = 2).withDeleteEventsOnSnapshot
    )
  }
}
