package com.github.mwegrz.scalautil.akka.http

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

class HttpServer private (config: Config, httpApis: Map[String, HttpApi])(implicit actorSystem: ActorSystem,
                                                                          actorMaterializer: ActorMaterializer,
                                                                          executor: ExecutionContext)
    extends Shutdownable
    with KeyValueLogging {
  import HttpServer.{ meaninglessRejection, generateRequestId }

  private val host = config.getString("host")
  private val port = config.getInt("port")

  private val route = redirectToNoTrailingSlashIfPresent(StatusCodes.MovedPermanently) {
    httpApis
      .foldLeft(pathEndOrSingleSlash {
        reject(meaninglessRejection)
      }) {
        case (r, (name, a)) =>
          pathPrefix(name) {
            val reqId = generateRequestId()
            a.route(reqId)
          } ~ r
      }
  }

  private val bindingFuture = Http().bindAndHandle(route, host, port)

  log.debug("Initialized")

  override def shutdown(): Unit = bindingFuture.flatMap(_.unbind()).map(_ => log.debug("Shut down"))
}

object HttpServer {
  private val meaninglessRejection = new Rejection {}

  def apply(config: Config, httpApis: Map[String, HttpApi])(implicit actorSystem: ActorSystem,
                                                            actorMaterializer: ActorMaterializer,
                                                            executor: ExecutionContext): HttpServer =
    new HttpServer(config.withReferenceDefaults("http-server"), httpApis)

  private def generateRequestId(): String = UUID.randomUUID().toString
}

trait HttpApi {
  def route(requestId: String): Route
}
