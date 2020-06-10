package com.github.mwegrz.scalautil.sms.hostedsms

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.marshalling.Marshal
import akka.http.scaladsl.model._
import akka.http.scaladsl.settings.ConnectionPoolSettings
import akka.stream.ActorMaterializer
import com.github.mwegrz.scalastructlog.KeyValueLogging
import com.github.mwegrz.scalautil.ConfigOps
import com.github.mwegrz.scalautil.akka.http.circe.JsonApiErrorAccumulatingCirceSupport.marshaller
import com.github.mwegrz.scalautil.sms.{ Sms, SmsClient }
import com.typesafe.config.Config
import io.circe.generic.auto._

import scala.concurrent.{ ExecutionContext, Future }
import scala.util.{ Failure, Success }

object HostedSmsClient {
  def apply(config: Config)(implicit
      actorSystem: ActorSystem,
      actorMaterializer: ActorMaterializer,
      executionContext: ExecutionContext
  ): HostedSmsClient =
    new HostedSmsClient(config.withReferenceDefaults("hosted-sms-client"))

  final case class Request()
}

class HostedSmsClient private (config: Config)(implicit
    actorSystem: ActorSystem,
    actorMaterializer: ActorMaterializer,
    executionContext: ExecutionContext
) extends SmsClient
    with KeyValueLogging {
  private val baseUri = Uri(config.getString("base-uri"))
  private val userEmail = config.getString("user-email")
  private val password = config.getString("password")
  private val http = Http(actorSystem)
  private val connectionPoolSettings = ConnectionPoolSettings(actorSystem)

  override def send(sms: Sms): Future[Unit] = {
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
        "v" -> "0"
      ).toEntity
    )
    val sending = http
      .singleRequest(
        request = request,
        settings = connectionPoolSettings
      )
      .map { response =>
        response.discardEntityBytes()
        if (response.status != StatusCodes.OK) {
          throw new IllegalArgumentException(s"Unsuccessful response: $response")
        } else {
          ()
        }
      }

    log.debug("Sending SMS")

    sending.onComplete {
      case Success(_)         => log.debug("SMS sent", "sms" -> sms)
      case Failure(exception) => log.error("Could not send SMS", exception, "sms" -> sms)
    }
    sending
  }
}
