package com.github.mwegrz.scalautil.sse

import akka.NotUsed
import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.headers.{ Authorization, HttpCredentials }
import akka.http.scaladsl.model.{ HttpRequest, Uri }
import akka.http.scaladsl.settings.ConnectionPoolSettings
import akka.stream.ActorMaterializer
import akka.stream.alpakka.sse.scaladsl.EventSource
import akka.stream.scaladsl.Source
import com.github.mwegrz.scalautil.akka.stream.alpakka.sse.scaladsl.NotRetryingEventSource

import scala.concurrent.{ ExecutionContext, Future }

object SseClient {
  def apply(
      implicit actorSystem: ActorSystem,
      actorMaterializer: ActorMaterializer,
      executionContext: ExecutionContext
  ): SseClient =
    new DefaultSseClient
}

trait SseClient {
  def createSource(
      uri: Uri,
      initialLastEventId: Option[String],
      authorize: => Future[HttpCredentials],
      reconnect: Boolean
  ): Source[SseEvent, NotUsed]
}

class DefaultSseClient(
    implicit actorSystem: ActorSystem,
    actorMaterializer: ActorMaterializer,
    executionContext: ExecutionContext
) extends SseClient {
  private val connectionPoolSettings = ConnectionPoolSettings(actorSystem)

  override def createSource(
      uri: Uri,
      initialLastEventId: Option[String],
      authorize: => Future[HttpCredentials],
      reconnect: Boolean
  ): Source[SseEvent, NotUsed] = {
    def send(request: HttpRequest) =
      authorize.flatMap { accessToken =>
        Http()
          .singleRequest(
            request = request.copy(headers = Authorization(accessToken) :: request.headers.toList),
            settings = connectionPoolSettings
          )
      }

    if (reconnect) {
      EventSource(uri, send, initialLastEventId)
    } else {
      NotRetryingEventSource(uri, send, initialLastEventId)
    }
  }
}
