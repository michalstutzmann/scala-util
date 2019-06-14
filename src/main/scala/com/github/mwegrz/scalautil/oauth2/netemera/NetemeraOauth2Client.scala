package com.github.mwegrz.scalautil.oauth2.netemera

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.headers.BasicHttpCredentials
import akka.http.scaladsl.model.{ FormData, HttpMethods, HttpRequest, Uri }
import akka.http.scaladsl.unmarshalling.Unmarshal
import akka.stream.ActorMaterializer
import com.github.mwegrz.scalautil.oauth2.{ GrantType, Oauth2Client, TokenObtained }
import com.typesafe.config.Config
import de.heikoseeberger.akkahttpcirce.ErrorAccumulatingCirceSupport.unmarshaller
import com.github.mwegrz.scalautil.ConfigOps
import io.circe.generic.extras.Configuration
import io.circe.generic.extras.auto._

import scala.concurrent.{ ExecutionContext, Future }

object NetemeraOauth2Client {
  def apply(config: Config)(
      implicit actorSystem: ActorSystem,
      actorMaterializer: ActorMaterializer,
      executionContext: ExecutionContext
  ): NetemeraOauth2Client =
    new NetemeraOauth2Client(config.withReferenceDefaults("netemera-oauth2-client"))

  private implicit val circeConfiguration: Configuration =
    Configuration.default.withSnakeCaseMemberNames.withDefaults
}

class NetemeraOauth2Client private (config: Config)(
    implicit actorSystem: ActorSystem,
    actorMaterializer: ActorMaterializer,
    executionContext: ExecutionContext
) extends Oauth2Client {
  import NetemeraOauth2Client._

  private val baseUri = Uri(config.getString("base-uri"))
  private val audience = config.getString("audience")

  private val credentials = {
    val clientId = config.getString("client-id")
    val clientSecret = config.getString("client-secret")
    BasicHttpCredentials(clientId, clientSecret)
  }

  private val http = Http(actorSystem)

  override def obtainToken: Future[TokenObtained] = {
    val uri = baseUri.copy(path = baseUri.path / "api" / "v2" / "oauth2" / "token")

    val request =
      HttpRequest(
        method = HttpMethods.POST,
        uri = uri,
        entity = FormData("grant_type" -> GrantType.ClientCredentials.value, "audience" -> audience).toEntity
      ).addCredentials(credentials)

    http
      .singleRequest(request)
      .flatMap { a =>
        Unmarshal(a).to[TokenObtained]
      }
  }
}
