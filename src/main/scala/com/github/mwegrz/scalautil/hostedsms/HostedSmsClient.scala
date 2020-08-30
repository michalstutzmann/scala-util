package com.github.mwegrz.scalautil.hostedsms

import java.util.UUID

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.MediaTypes.`application/json`
import akka.http.scaladsl.model._
import akka.http.scaladsl.settings.ConnectionPoolSettings
import akka.stream.ActorMaterializer
import com.github.mwegrz.scalastructlog.KeyValueLogging
import com.github.mwegrz.scalautil.ConfigOps
import com.github.mwegrz.scalautil.mobile.Sms
import com.typesafe.config.Config
import akka.http.scaladsl.model.headers.Accept
import io.circe.generic.auto._
import com.github.mwegrz.scalautil.circe.codecs._
import io.circe.parser._
import akka.http.scaladsl.unmarshalling.Unmarshal
import com.github.mwegrz.scalautil.akka.http.circe.JsonApiErrorAccumulatingCirceSupport.unmarshaller

import scala.concurrent.{ ExecutionContext, Future }
import scala.util.{ Failure, Success }

object HostedSmsClient {
  def apply(config: Config)(implicit
      actorSystem: ActorSystem,
      actorMaterializer: ActorMaterializer,
      executionContext: ExecutionContext
  ): HostedSmsClient =
    new HostedSmsClient(config.withReferenceDefaults("hosted-sms.client"))

  private final case class Response(MessageId: Option[MessageId], ErrorMessage: Option[String])
}

class HostedSmsClient private (config: Config)(implicit
    actorSystem: ActorSystem,
    actorMaterializer: ActorMaterializer,
    executionContext: ExecutionContext
) extends KeyValueLogging {
  import HostedSmsClient._

  private val baseUri = Uri(config.getString("base-uri"))
  private val userEmail = config.getString("user-email")
  private val password = config.getString("password")
  private val http = Http(actorSystem)
  private val connectionPoolSettings = ConnectionPoolSettings(actorSystem)

  def send(sms: Sms): Future[MessageId] = {
    val uri = baseUri
    val request = HttpRequest(
      method = HttpMethods.POST,
      uri,
      // Form data in the following format: UserEmail=useremail%40dcs.pl&Password=correctpassword&Sender=TEST&Phone=48xxxxxxxxx&Message=TEST
      entity = FormData(
        "UserEmail" -> userEmail,
        "Password" -> password,
        "Sender" -> sms.sender,
        "Phone" -> sms.recipient.value.stripPrefix("+"),
        "Message" -> sms.message,
        "v" -> UUID.randomUUID.toString
      ).toEntity
    )
    val sending: Future[MessageId] = http
      .singleRequest(
        request = request.copy(headers = Accept(`application/json`) :: request.headers.toList),
        settings = connectionPoolSettings
      )
      .flatMap { e =>
        if (e.status == StatusCodes.OK) {
          Unmarshal(e)
            .to[Response]
            .map {
              case Response(Some(messageId), None) =>
                messageId
              case Response(None, Some(errorMessage)) =>
                throw new IllegalArgumentException(errorMessage)
            }
        } else {
          throw new IllegalArgumentException(s"Invalid HTTP status code: ${e.status.value}")
        }
      }

    log.debug("Sending", "sms" -> sms)

    sending.onComplete {
      case Success(messageId) => log.debug("Sent", ("sms" -> sms, "message-id" -> messageId))
      case Failure(exception) => log.error("Sending failed", exception, "sms" -> sms)
    }
    sending
  }
}
