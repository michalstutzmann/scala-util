package com.github.mwegrz.scalautil.akka.http

import java.time.Instant
import java.util.UUID

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.model._
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server._
import akka.http.scaladsl.server.directives.RouteDirectives.reject
import akka.stream.ActorMaterializer
import com.github.mwegrz.app.Shutdownable
import com.github.mwegrz.scalastructlog.KeyValueLogging
import com.typesafe.config.Config
import com.github.mwegrz.scalautil.ConfigOps
import com.github.mwegrz.scalautil.ByteStringOps
import com.github.mwegrz.scalautil.akka.http.server.directives.CorsHandler

import scala.concurrent.ExecutionContext

object HttpServer {
  def apply(config: Config, httpApis: Set[HttpApi])(
      implicit actorSystem: ActorSystem,
      actorMaterializer: ActorMaterializer,
      executor: ExecutionContext
  ): HttpServer =
    new HttpServer(config.withReferenceDefaults("http-server"), httpApis)

  private def generateRequestId(): String = UUID.randomUUID().toString
}

class HttpServer private (config: Config, httpApis: Set[HttpApi])(
    implicit actorSystem: ActorSystem,
    actorMaterializer: ActorMaterializer,
    executor: ExecutionContext
) extends Shutdownable
    with KeyValueLogging
    with CorsHandler {

  import HttpServer.generateRequestId

  private val basePath =
    if (config.hasPath("base-path")) Some(config.getString("base-path")).map(separateOnSlashes)
    else None
  private val host = config.getString("host")
  private val port = config.getInt("port")

  private val path: Route = corsHandler {
    extractClientIP { clientIp =>
      extractRequestContext { context =>
        val requestId = generateRequestId()
        val time = Instant.now()
        context.request match {
          case HttpRequest(
              HttpMethod(method, _, _, _),
              uri,
              headers,
              HttpEntity.Strict(contentType, data),
              HttpProtocol(protocol)
              ) =>
            log.info(
              "Received request",
              (
                "id" -> requestId,
                "client-ip" -> clientIp.value,
                "method" -> method,
                "uri" -> uri,
                "protocol" -> protocol,
                "headers" -> headers.mkString(","),
                "content-type" -> contentType,
                "data" -> data.toByteVector.toBase64
              )
            )
          case HttpRequest(
              HttpMethod(method, _, _, _),
              uri,
              headers,
              HttpEntity.Default(contentType, contentLength, _),
              HttpProtocol(protocol)
              ) =>
            log.info(
              "Received request",
              (
                "id" -> requestId,
                "client-ip" -> clientIp.value,
                "method" -> method,
                "uri" -> uri,
                "protocol" -> protocol,
                "headers" -> headers.mkString(","),
                "content-type" -> contentType,
                "content-length" -> contentLength
              )
            )
        }
        httpApis
          .foldLeft(pathEndOrSingleSlash(reject)) {
            case (r, api) =>
              api.route(requestId, time) ~ r
          }
      }
    }
  }

  private[http] val route = redirectToNoTrailingSlashIfPresent(StatusCodes.MovedPermanently) {
    basePath match {
      case Some(value) => pathPrefix(value)(path)
      case None        => path
    }
  }

  private val bindingFuture = Http().bindAndHandle(route, host, port)

  log.debug("Initialized")

  bindingFuture
    .map(_.localAddress)
    .map(
      localAddress => log.info(s"Bound to ${localAddress.getHostString}:${localAddress.getPort}")
    )

  override def shutdown(): Unit = bindingFuture.flatMap(_.unbind()).map(_ => log.debug("Shut down"))
}

trait HttpApi {
  def route(requestId: String, time: Instant): Route
}
