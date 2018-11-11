package com.github.mwegrz.scalautil.akka.http

import java.time.Instant
import java.util.UUID

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server._
import akka.http.scaladsl.server.directives.RouteDirectives.reject
import akka.stream.ActorMaterializer
import com.github.mwegrz.app.Shutdownable
import com.github.mwegrz.scalastructlog.KeyValueLogging
import com.typesafe.config.Config
import com.github.mwegrz.scalautil.ConfigOps

import scala.concurrent.ExecutionContext

object HttpServer {
  def apply(config: Config, httpApis: Set[HttpApi])(implicit actorSystem: ActorSystem,
                                                    actorMaterializer: ActorMaterializer,
                                                    executor: ExecutionContext): HttpServer =
    new HttpServer(config.withReferenceDefaults("http-server"), httpApis)

  private def generateRequestId(): String = UUID.randomUUID().toString
}

class HttpServer private (config: Config, httpApis: Set[HttpApi])(
    implicit actorSystem: ActorSystem,
    actorMaterializer: ActorMaterializer,
    executor: ExecutionContext)
    extends Shutdownable
    with KeyValueLogging {
  import HttpServer.generateRequestId

  private val basePath =
    if (config.hasPath("base-path")) Some(config.getString("base-path")).map(separateOnSlashes)
    else None
  private val host = config.getString("host")
  private val port = config.getInt("port")

  private val path: Route = {
    val requestId = generateRequestId()
    val time = Instant.now()
    httpApis
      .foldLeft(pathEndOrSingleSlash(reject)) {
        case (r, api) =>
          api.route(requestId, time) ~ r
      }
  }

  private[http] val route = redirectToNoTrailingSlashIfPresent(StatusCodes.MovedPermanently) {
    extractRequestContext { context =>
      log.info("Received request", "request" -> context.request)
      basePath match {
        case Some(value) => pathPrefix(value)(path)
        case None        => path
      }
    }
  }

  private val bindingFuture = Http().bindAndHandle(route, host, port)

  log.debug("Initialized")

  bindingFuture
    .map(_.localAddress)
    .map(localAddress =>
      log.info(s"Bound to ${localAddress.getHostString}:${localAddress.getPort}"))

  override def shutdown(): Unit = bindingFuture.flatMap(_.unbind()).map(_ => log.debug("Shut down"))
}

trait HttpApi {
  def route(requestId: String, time: Instant): Route
}
