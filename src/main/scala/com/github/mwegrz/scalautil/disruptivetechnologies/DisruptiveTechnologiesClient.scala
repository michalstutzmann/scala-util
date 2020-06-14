package com.github.mwegrz.scalautil.disruptivetechnologies

import java.time.Instant

import akka.NotUsed
import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.marshalling.{ Marshal, ToEntityMarshaller }
import akka.http.scaladsl.model.headers.{ Authorization, OAuth2BearerToken }
import akka.http.scaladsl.model.{ HttpMethods, HttpRequest, RequestEntity, StatusCodes, Uri }
import akka.http.scaladsl.server.Route
import akka.http.scaladsl.settings.ConnectionPoolSettings
import akka.http.scaladsl.unmarshalling.{ FromEntityUnmarshaller, FromResponseUnmarshaller, Unmarshal }
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.{ Sink, Source }
import akka.util.Timeout
import com.github.mwegrz.scalastructlog.KeyValueLogging
import com.github.mwegrz.scalautil.ConfigOps
import com.github.mwegrz.scalautil.akka.http.server.directives.routes.{ MultiDocument, Resource, SingleDocument }
import com.github.mwegrz.scalautil.oauth2.Oauth2Client
import com.typesafe.config.Config
import io.circe.generic.auto._
import com.github.mwegrz.scalautil.circe.codecs._
import com.github.mwegrz.scalautil.sse.SseClient
import io.circe.parser._
import com.github.mwegrz.scalautil.akka.http.circe.JsonApiErrorAccumulatingCirceSupport.{ marshaller, unmarshaller }
import com.github.mwegrz.scalautil.akka.stream.scaladsl.{ FlowHub, FlowOps, PolicyRestartSource, RestartPolicy }

import scala.concurrent.{ ExecutionContext, Future }
import scala.util.{ Failure, Success, Try }
import scala.concurrent.duration._

object DisruptiveTechnologiesClient {
  def apply(config: Config)(implicit
      actorSystem: ActorSystem,
      actorMaterializer: ActorMaterializer,
      executionContext: ExecutionContext,
      oauth2Client: DisruptiveTechnologiesOauth2Client,
      sseClient: SseClient,
      restartPolicy: RestartPolicy
  ): DisruptiveTechnologiesClient =
    new DisruptiveTechnologiesClient(config.withReferenceDefaults("disruptive-technologies-client"))
}

class DisruptiveTechnologiesClient private (config: Config)(implicit
    actorSystem: ActorSystem,
    actorMaterializer: ActorMaterializer,
    executionContext: ExecutionContext,
    oauthClient: Oauth2Client,
    sseClient: SseClient,
    restartPolicy: RestartPolicy
) extends KeyValueLogging {
  private val baseUri = Uri(config.getString("base-uri"))
  private val http = Http(actorSystem)
  private val connectionPoolSettings = ConnectionPoolSettings(actorSystem)

  lazy val liveEventPushRouteWithSource: (Route, Source[Event, NotUsed]) = {
    val eventFlowHub = new FlowHub[Event](drain = true)
    val eventSink: Sink[Event, NotUsed] = eventFlowHub.flow.toSink
    val eventSource: Source[Event, NotUsed] = eventFlowHub.flow.toSource
    ???
  }

  def liveEventSource(
      projectId: ProjectId
  ): Source[Try[Event], NotUsed] = {
    val uri = baseUri.copy(path = baseUri.path / "projects" / projectId.toString / "devices:stream")

    PolicyRestartSource.withBackoff { () =>
      val eventSource =
        sseClient.createSource(uri, None, obtainAccessToken, reconnect = false)
      eventSource
        .map { sse =>
          parse(sse.data).toTry
            .flatMap { data =>
              data.as[LiveEventResponse].toTry
            }
            .recoverWith { throwable => Failure(new Exception(s"Decoding failed: ${sse.data}", throwable)) }
            .map(_.result.event)
        }
    }
  }

  def eventHistorySource(
      projectId: ProjectId,
      deviceId: DeviceId,
      startTime: Option[Instant],
      endTime: Option[Instant]
  )(implicit pageSize: Option[PageSize] = None, timeout: Timeout): Source[Event, NotUsed] = {
    Source.fromFutureSource(
      eventHistory(projectId, deviceId, startTime, endTime, None).map {
        case EventHistoryResponse(events, None | Some(PageToken(""))) => Source(events)
        case EventHistoryResponse(events, Some(nextPageToken)) =>
          Source(events)
            .concat(
              Source
                .unfoldAsync(Option(nextPageToken)) {
                  case None | Some(PageToken("")) => Future.successful(None)
                  case Some(pageToken) =>
                    eventHistory(projectId, deviceId, startTime, endTime, Some(pageToken)).map {
                      case EventHistoryResponse(events, nextPageToken) => Some((nextPageToken, events))
                    }
                }
                .flatMapConcat(Source(_))
            )
      }
    )
  }.mapMaterializedValue(_ => NotUsed)

  def eventHistory(
      projectId: ProjectId,
      deviceId: DeviceId,
      startTime: Option[Instant],
      endTime: Option[Instant],
      pageToken: Option[PageToken]
  )(implicit pageSize: Option[PageSize] = None, timeout: Timeout): Future[EventHistoryResponse] = {
    val uri = baseUri
      .copy(path = baseUri.path / "projects" / projectId.toString / "devices" / deviceId.value / "events")

    val uriQuery = Uri.Query(
      uri
        .query()
        .toMap
        .updated("start_time", startTime.map(_.toString).getOrElse(""))
        .updated("end_time", endTime.map(_.toString).getOrElse(""))
        .updated("page_size", pageSize.map(_.value.toString).getOrElse(""))
        .updated("page_token", pageToken.map(_.value).getOrElse(""))
        .filter(_._2.nonEmpty)
    )

    obtainAccessToken.flatMap { accessToken =>
      val request =
        HttpRequest(method = HttpMethods.GET, uri.withQuery(uriQuery)).copy(headers = List(Authorization(accessToken)))
      Http()
        .singleRequest(
          request = request,
          settings = connectionPoolSettings
        )
        .flatMap { response =>
          if (response.status != StatusCodes.OK) {
            Future.failed(new IllegalArgumentException("Unsuccessful response"))
          } else {
            response.toStrict(timeout.duration).flatMap { value => Unmarshal(value).to[EventHistoryResponse] }
          }
        }
    }
  }

  private def obtainAccessToken =
    oauthClient.obtainToken.map(_.accessToken).map(OAuth2BearerToken)
}
