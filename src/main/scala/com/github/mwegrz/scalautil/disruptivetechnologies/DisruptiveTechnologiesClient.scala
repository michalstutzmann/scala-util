package com.github.mwegrz.scalautil.disruptivetechnologies

import java.time.Instant

import akka.NotUsed
import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.marshalling.{ Marshal, ToEntityMarshaller }
import akka.http.scaladsl.model.headers.{ Authorization, OAuth2BearerToken }
import akka.http.scaladsl.model.{ HttpMethods, HttpRequest, RequestEntity, StatusCodes, Uri }
import akka.http.scaladsl.settings.ConnectionPoolSettings
import akka.http.scaladsl.unmarshalling.{ FromEntityUnmarshaller, FromResponseUnmarshaller, Unmarshal }
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.Source
import com.github.mwegrz.scalautil.ConfigOps
import com.github.mwegrz.scalautil.akka.http.server.directives.routes.{ MultiDocument, Resource, SingleDocument }
import com.github.mwegrz.scalautil.oauth2.Oauth2Client
import com.typesafe.config.Config
import io.circe.generic.auto._
import com.github.mwegrz.scalautil.circe.codecs._
import com.github.mwegrz.scalautil.sse.SseClient
import io.circe.parser._
import com.github.mwegrz.scalautil.akka.http.circe.JsonApiErrorAccumulatingCirceSupport.{ marshaller, unmarshaller }

import scala.concurrent.{ ExecutionContext, Future }

object DisruptiveTechnologiesClient {
  def apply(config: Config)(
      implicit actorSystem: ActorSystem,
      actorMaterializer: ActorMaterializer,
      executionContext: ExecutionContext,
      oauth2Client: DisruptiveTechnologiesOauth2Client,
      sseClient: SseClient
  ): DisruptiveTechnologiesClient =
    new DisruptiveTechnologiesClient(config.withReferenceDefaults("disruptive-technologies-client"))
}

class DisruptiveTechnologiesClient private (config: Config)(
    implicit actorSystem: ActorSystem,
    actorMaterializer: ActorMaterializer,
    executionContext: ExecutionContext,
    oauthClient: Oauth2Client,
    sseClient: SseClient
) {
  private val baseUri = Uri(config.getString("base-uri"))
  private val http = Http(actorSystem)
  private val connectionPoolSettings = ConnectionPoolSettings(actorSystem)

  private def liveEventSource(
      projectId: ProjectId
  ) = {
    val uri = baseUri.copy(path = baseUri.path / "projects" / projectId.toString / "devices:stream")

    val eventSource =
      sseClient.createSource(uri, None, obtainAccessToken, reconnect = true)
    eventSource.map(sse => parse(sse.data).toTry.map(_.as[Event]).flatMap(_.toTry))
  }

  /*private def eventHistory(
      projectId: ProjectId,
      deviceId: DeviceId,
      startTime: Option[Instant],
      endTime: Option[Instant],
      pageSize: Option[PageSize],
      pageToken: Option[PageToken]
  ) = {
    val uriQuery = Uri.Query(
      uri
        .query()
        .toMap
        .updated("start_time", startTime.map(_.toString))
        .updated("end_time", endTime.map(_.toString))
        .updated("page_size", pageSize.map(_.value))
        .updated("page_token", pageToken.map(_.value))
        .filter(_._2.nonEmpty)
    )

    val updatedUri = baseUri
      .copy(path = baseUri.path / "projects" / projectId.toString / "devices" / deviceId.value / "events")

    val request = HttpRequest(method = HttpMethods.GET, uri)
    obtainAccessToken.flatMap { accessToken =>
      Http()
        .singleRequest(
          request = request.copy(headers = Authorization(accessToken) :: request.headers.toList),
          settings = connectionPoolSettings
        )
        .flatMap { e =>
          if (e.status != StatusCodes.OK) {
            throw new IllegalArgumentException("Unsuccessful response")
          } else {
            Unmarshal(e).to[Event]
          }
        }
    }
  }*/

  private def obtainAccessToken =
    oauthClient.obtainToken.map(_.accessToken).map(OAuth2BearerToken)
}
