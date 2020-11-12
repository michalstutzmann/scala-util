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
import io.circe.syntax._
import io.circe.generic.auto._
import com.github.mwegrz.scalautil.circe.codecs._
import akka.http.scaladsl.unmarshalling.Unmarshal
import com.github.mwegrz.scalautil.akka.http.circe.JsonApiErrorAccumulatingCirceSupport.unmarshaller
import io.circe.{ Decoder, Encoder }
import cats.syntax.functor._

import scala.concurrent.{ ExecutionContext, Future }
import scala.util.control.NonFatal
import scala.util.{ Failure, Success }

object HostedSmsClient {
  def apply(config: Config)(implicit
      actorSystem: ActorSystem,
      actorMaterializer: ActorMaterializer,
      executionContext: ExecutionContext
  ): HostedSmsClient =
    new HostedSmsClient(config.withReferenceDefaults("hosted-sms.client"))

  private object Response {
    implicit val circeEncoder: Encoder[Response] = Encoder.instance {
      case macCommands @ SuccessfulResponse(_)       => macCommands.asJson
      case applicationData @ UnsuccessfulResponse(_) => applicationData.asJson
    }

    implicit val circeDecoder: Decoder[Response] =
      List[Decoder[Response]](
        Decoder[SuccessfulResponse].widen,
        Decoder[UnsuccessfulResponse].widen
      ).reduceLeft(_ or _)
  }

  private sealed trait Response
  private final case class SuccessfulResponse(MessageId: MessageId) extends Response
  private final case class UnsuccessfulResponse(ErrorMessage: String) extends Response
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
      .flatMap { httpResponse =>
        if (httpResponse.status == StatusCodes.OK) {
          Unmarshal(httpResponse)
            .to[Response]
            .recoverWith {
              case NonFatal(throwable) =>
                Future.failed(
                  new IllegalStateException(s"HTTP response unmarshalling failed: $httpResponse}", throwable)
                )
            }
            .map {
              case SuccessfulResponse(messageId) =>
                messageId
              case UnsuccessfulResponse(errorMessage) =>
                throw new IllegalArgumentException(errorMessage)
            }
        } else {
          throw new IllegalStateException(s"Invalid HTTP status code: ${httpResponse.status.value}")
        }
      }

    log.debug("Sending SMS", "sms" -> sms)

    sending.onComplete {
      case Success(messageId) => log.debug("SMS Sent", ("sms" -> sms, "message-id" -> messageId))
      case Failure(exception) => log.error("Sending SMS failed", exception, "sms" -> sms)
    }
    sending
  }
}
