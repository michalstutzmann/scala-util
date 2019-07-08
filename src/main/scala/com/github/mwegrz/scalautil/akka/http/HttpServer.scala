package com.github.mwegrz.scalautil.akka.http

import java.time.Instant
import java.util.UUID

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.model._
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.RouteResult.{ Complete, Rejected }
import akka.http.scaladsl.server._
import akka.http.scaladsl.server.directives.RouteDirectives.reject
import akka.http.scaladsl.model.headers._
import akka.stream.ActorMaterializer
import com.github.mwegrz.app.Shutdownable
import com.github.mwegrz.scalastructlog.KeyValueLogging
import com.typesafe.config.Config
import com.github.mwegrz.scalautil.ConfigOps
import com.github.mwegrz.scalautil.ByteStringOps
import ch.megard.akka.http.cors.scaladsl.CorsDirectives._

import scala.concurrent.ExecutionContext
import scala.util.{ Failure, Success, Try }
import com.github.mwegrz.scalautil.akka.http.server.directives.AroundDirectives._

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
    executionContext: ExecutionContext
) extends Shutdownable
    with KeyValueLogging {

  import HttpServer.generateRequestId

  def logRequest(request: HttpRequest): Try[RouteResult] => Unit = {
    val requestId = generateRequestId()
    val startTime = System.currentTimeMillis()
    val clientIp = request.headers.collectFirst {
      case `X-Forwarded-For`(Seq(address, _*)) => address
      case `Remote-Address`(address)           => address
      case `X-Real-Ip`(address)                => address
    }
    val (contentType, contentLength, data) = request.entity match {
      case HttpEntity.Strict(contentType, data)              => (contentType, data.size, Some(data))
      case HttpEntity.Default(contentType, contentLength, _) => (contentType, contentLength, None)
    }

    log.info(
      "Received request",
      (
        "request-id" -> requestId,
        "client-ip" -> clientIp.getOrElse("null"),
        "method" -> request.method.value,
        "uri" -> request.uri,
        "protocol" -> request.protocol.value,
        "headers" -> request.headers.mkString(",")
      )
    )

    {
      case Success(Complete(response)) =>
        val timeElapsed = System.currentTimeMillis() - startTime
        log.info(
          "Completed request",
          (
            "request-id" -> requestId,
            "client-ip" -> clientIp.getOrElse("null"),
            "method" -> request.method.value,
            "uri" -> request.uri,
            "protocol" -> request.protocol.value,
            "headers" -> request.headers.mkString(","),
            "status" -> response.status.intValue(),
            data.fold("contentLength" -> contentLength.toString)(
              value => "content" -> value.toByteVector.toBase64
            ),
            "time-elapsed" -> timeElapsed
          )
        )

      case Success(Rejected(rejections)) =>
        val timeElapsed = System.currentTimeMillis() - startTime
        log.info(
          "Rejected request",
          (
            "request-id" -> requestId,
            "client-ip" -> clientIp.getOrElse("null"),
            "method" -> request.method.value,
            "uri" -> request.uri,
            "protocol" -> request.protocol.value,
            "headers" -> request.headers.mkString(","),
            "status" -> 404,
            data.fold("contentLength" -> contentLength.toString)(
              value => "content" -> value.toByteVector.toBase64
            ),
            "rejections" -> rejections.mkString(","),
            "time-elapsed" -> timeElapsed
          )
        )
      case Failure(exception) =>
        val timeElapsed = System.currentTimeMillis() - startTime
        log.info(
          "Failed processing request",
          (
            "request-id" -> requestId,
            "client-ip" -> clientIp.getOrElse("null"),
            "method" -> request.method.value,
            "uri" -> request.uri,
            "protocol" -> request.protocol.value,
            "headers" -> request.headers.mkString(","),
            "status" -> 500,
            data.fold("contentLength" -> contentLength.toString)(
              value => "content" -> value.toByteVector.toBase64
            ),
            "cause" -> exception.getMessage,
            "time-elapsed" -> timeElapsed
          )
        )
    }
  }

  private val basePath =
    if (config.hasPath("base-path")) Some(config.getString("base-path")).map(separateOnSlashes)
    else None
  private val host = config.getString("host")
  private val port = config.getInt("port")

  private val path: Route = cors() {
    aroundRequest(logRequest)(executionContext) {
      val time = Instant.now()
      httpApis
        .foldLeft(pathEndOrSingleSlash(reject)) {
          case (r, api) =>
            api.route(time) ~ r
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
  def route(time: Instant): Route
}
