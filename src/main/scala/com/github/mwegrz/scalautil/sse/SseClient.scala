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
import com.github.mwegrz.scalastructlog.KeyValueLogging
import com.github.mwegrz.scalautil.akka.stream.alpakka.sse.scaladsl.NotRetryingEventSource
import com.typesafe.config.Config
import com.github.mwegrz.scalautil.ConfigOps
import com.github.mwegrz.scalautil.akka.stream.scaladsl.{ PolicyRestartSource, RestartPolicy }

import scala.concurrent.{ ExecutionContext, Future }

object SseClient {
  def apply(config: Config)(implicit
      actorSystem: ActorSystem,
      actorMaterializer: ActorMaterializer,
      executionContext: ExecutionContext,
      restartPolicy: RestartPolicy
  ): SseClient =
    new DefaultSseClient(config.withReferenceDefaults("sse-client"))
}

trait SseClient {
  def createSource(
      uri: Uri,
      initialLastEventId: Option[String],
      authorize: => Future[HttpCredentials],
      reconnect: Boolean
  ): Source[SseEvent, NotUsed]
}

class DefaultSseClient private[sse] (config: Config)(implicit
    actorSystem: ActorSystem,
    actorMaterializer: ActorMaterializer,
    executionContext: ExecutionContext,
    restartPolicy: RestartPolicy
) extends SseClient
    with KeyValueLogging {
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

    val source = if (reconnect) {
      EventSource(uri, send, initialLastEventId)
    } else {
      NotRetryingEventSource(uri, send, initialLastEventId)
    }

    PolicyRestartSource.onFailuresWithBackoff { () =>
      source
        .watchTermination() { (_, f) =>
          f.recover {
              case t: Throwable =>
                log.error("Source encountered a failure and has been restarted", t)
            }
            .foreach { _ => log.debug("Source completed") }
        }
    }
  }
}
