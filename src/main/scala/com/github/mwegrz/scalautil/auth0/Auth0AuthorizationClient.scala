package com.github.mwegrz.scalautil.auth0

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.marshalling.Marshal
import akka.http.scaladsl.model.{ HttpMethods, HttpRequest, RequestEntity, Uri }
import akka.http.scaladsl.unmarshalling.Unmarshal
import akka.stream.ActorMaterializer
import com.typesafe.config.Config
import io.circe.generic.extras.auto._
import com.github.mwegrz.scalautil.circe.codecs._
import de.heikoseeberger.akkahttpcirce.ErrorAccumulatingCirceSupport.{ marshaller, unmarshaller }
import io.circe.generic.extras.Configuration
import com.github.mwegrz.scalautil.{ ConfigOps, javaDurationToDuration }

import scala.concurrent.{ ExecutionContext, Future }

object Auth0AuthorizationClient {
  def apply(config: Config)(implicit actorSystem: ActorSystem,
                            actorMaterializer: ActorMaterializer,
                            executionContext: ExecutionContext): Auth0AuthorizationClient =
    new Auth0AuthorizationClient(config.withReferenceDefaults("auth0-authorization-client"))

  private implicit val jsonConfiguration: Configuration = Configuration.default.withSnakeCaseMemberNames.withDefaults

  private case class RetrieveClientCredentials(
      audience: String,
      grantType: String,
      clientId: String,
      clientSecret: String
  )

  case class ClientCredentials(
      accessToken: String,
      tokenType: String,
      expiresIn: Long
  )
}

class Auth0AuthorizationClient private (config: Config)(implicit actorSystem: ActorSystem,
                                                        actorMaterializer: ActorMaterializer,
                                                        executionContext: ExecutionContext) {
  import Auth0AuthorizationClient._

  private val baseUri = Uri(config.getString("base-uri"))

  private val http = Http(actorSystem)

  def retrieveClientCredentials(audience: String,
                                clientId: ClientId,
                                clientSecret: String): Future[ClientCredentials] = {
    val uri = baseUri.copy(path = baseUri.path / "oauth" / "token")
    val requestBody = RetrieveClientCredentials(audience, "client_credentials", clientId.value, clientSecret)

    Marshal(requestBody).to[RequestEntity] flatMap { entity =>
      val request = HttpRequest(method = HttpMethods.POST, uri = uri, entity = entity)
      http
        .singleRequest(request)
        .flatMap { a =>
          Unmarshal(a).to[ClientCredentials]
        }
    }
  }
}
