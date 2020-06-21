package com.github.mwegrz.scalautil.disruptivetechnologies

import java.time.Instant

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.{ FormData, HttpMethods, HttpRequest, Uri }
import akka.http.scaladsl.unmarshalling.Unmarshal
import akka.stream.ActorMaterializer
import com.github.mwegrz.scalautil.ConfigOps
import com.github.mwegrz.scalautil.oauth2.TokenObtained
import com.typesafe.config.Config
import de.heikoseeberger.akkahttpcirce.ErrorAccumulatingCirceSupport.unmarshaller
import io.circe.generic.extras.Configuration
import io.circe.generic.extras.auto._
import pdi.jwt.{ JwtAlgorithm, JwtCirce, JwtClaim, JwtHeader }

import scala.concurrent.{ ExecutionContext, Future }

object DisruptiveTechnologiesOauth2Client {
  def apply(config: Config)(implicit
      actorSystem: ActorSystem,
      actorMaterializer: ActorMaterializer,
      executionContext: ExecutionContext
  ): DisruptiveTechnologiesOauth2Client =
    new DisruptiveTechnologiesOauth2Client(config.withReferenceDefaults("disruptive-technologies.oauth2-client"))

  private implicit val circeConfiguration: Configuration =
    Configuration.default.withSnakeCaseMemberNames.withDefaults
}

class DisruptiveTechnologiesOauth2Client private (config: Config)(implicit
    actorSystem: ActorSystem,
    actorMaterializer: ActorMaterializer,
    executionContext: ExecutionContext
) {
  import DisruptiveTechnologiesOauth2Client._

  private val baseUri = Uri(config.getString("base-uri"))
  private val tokenUri = baseUri.copy(
    path = baseUri.path / "token"
  )

  private val http = Http(actorSystem)

  def obtainToken(serviceAccount: ServiceAccount): Future[TokenObtained] = {
    val jwt = {
      val epochSeconds = Instant.now.toEpochMilli / 1000
      val claim = JwtClaim()
        .issuedAt(epochSeconds)
        .expiresAt(epochSeconds + 3600)
        .to(tokenUri.toString)
        .by(serviceAccount.email)
      val header = JwtHeader(Some(JwtAlgorithm.HS256), Some(JwtHeader.DEFAULT_TYPE), None, Some(serviceAccount.keyId))
      JwtCirce.encode(header, claim, serviceAccount.secret)
    }

    val request =
      HttpRequest(
        method = HttpMethods.POST,
        uri = tokenUri,
        entity = FormData("assertion" -> jwt, "grant_type" -> "urn:ietf:params:oauth:grant-type:jwt-bearer").toEntity
      )

    http
      .singleRequest(request)
      .flatMap { a => Unmarshal(a).to[TokenObtained] }
  }
}
