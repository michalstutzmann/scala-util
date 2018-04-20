package com.github.mwegrz.scalautil.oauth2.auth0

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.marshalling.Marshal
import akka.http.scaladsl.model.{ HttpMethods, HttpRequest, RequestEntity, Uri }
import akka.http.scaladsl.unmarshalling.Unmarshal
import akka.stream.ActorMaterializer
import com.github.mwegrz.scalautil.ConfigOps
import com.github.mwegrz.scalautil.oauth2.{ Oauth2Client, RetrieveToken, TokenRetrieved }
import com.typesafe.config.Config
import de.heikoseeberger.akkahttpcirce.ErrorAccumulatingCirceSupport.{ marshaller, unmarshaller }
import io.circe.generic.extras.Configuration
import io.circe.generic.extras.auto._

import scala.concurrent.{ ExecutionContext, Future }

object Auth0Oauth2Client {
  def apply(config: Config)(implicit actorSystem: ActorSystem,
                            actorMaterializer: ActorMaterializer,
                            executionContext: ExecutionContext): Auth0Oauth2Client =
    new Auth0Oauth2Client(config.withReferenceDefaults("auth0-oauth2-client"))

  private implicit val circeConfiguration: Configuration = Configuration.default.withSnakeCaseMemberNames.withDefaults
}

class Auth0Oauth2Client private (config: Config)(implicit actorSystem: ActorSystem,
                                                 actorMaterializer: ActorMaterializer,
                                                 executionContext: ExecutionContext)
    extends Oauth2Client {
  import Auth0Oauth2Client._

  private val baseUri = Uri(config.getString("base-uri"))
  private val audience = config.getString("audience")
  private val clientId = config.getString("client-id")
  private val clientSecret = config.getString("client-secret")

  private val http = Http(actorSystem)

  override def retrieveToken: Future[TokenRetrieved] = {
    val uri = baseUri.copy(path = baseUri.path / "oauth" / "token")
    val requestBody = RetrieveToken(audience, "client_credentials", clientId, clientSecret)

    Marshal(requestBody).to[RequestEntity] flatMap { entity =>
      val request = HttpRequest(method = HttpMethods.POST, uri = uri, entity = entity)
      http
        .singleRequest(request)
        .flatMap { response =>
          Unmarshal(response).to[TokenRetrieved]
        }
    }
  }
}
