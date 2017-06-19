package com.github.mwegrz.scalautil.akka.http

import java.util.UUID

import akka.http.scaladsl.Http
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.{ Route, ValidationRejection }
import akka.http.scaladsl.server.directives.Credentials
import akka.http.scaladsl.server.directives.Credentials.Provided
import com.github.mwegrz.app.Shutdownable
import com.github.mwegrz.scalastructlog.KeyValueLogging
import com.github.mwegrz.scalautil.akka.Akka
import com.typesafe.config.Config
import com.github.mwegrz.scalautil.ConfigOps

import scala.concurrent.ExecutionContext

class HttpServer private (config: Config, httpApis: Map[String, HttpApi])(implicit akka: Akka, ec: ExecutionContext)
    extends Shutdownable
    with KeyValueLogging {
  import HttpServer.generateRequestId

  import akka.actorSystem
  import akka.actorMaterializer

  private val host = config.getString("host")
  private val port = config.getInt("port")

  private val route =
    parameter('access_token) { token =>
      //authenticateOAuth2[Map[String, String]]("application-server", authenticator) { claims =>
      authorize(alow(Map("token" -> token))) {
        httpApis
          .foldLeft(pathEndOrSingleSlash {
            reject(ValidationRejection("Not authorized"))
          }) {
            case (r, (name, a)) =>
              pathPrefix(name) {
                val reqId = generateRequestId()
                a.route(reqId)
              } ~ r
          }
      }
    }

  private def alow(claims: Map[String, String]): Boolean =
    claims.get("token").forall(_ == "Xdctqr566")

  private val bindingFuture = Http().bindAndHandle(route, host, port)

  log.debug("Initialized")

  override def shutdown(): Unit =
    bindingFuture.flatMap(_.unbind()).map(_ => log.debug("Shut down"))
}

object HttpServer {
  def apply(config: Config, httpApis: Map[String, HttpApi])(implicit akka: Akka, ec: ExecutionContext): HttpServer =
    new HttpServer(config.withReferenceDefaults("http-server"), httpApis)

  //private def authenticated[T](authenticator: Authenticator[T]): AuthenticationDirective[T] = //authenticateOAuth2[T]("application-server", authenticator)
  //parameter('access_token.?).flatMap {
  //case Some(token) => authenticateOrRejectWithChallenge(cred ⇒ FastFuture.successful(authenticator(cred)))
  //case None => authenticateOAuth2[T]("application-server", authenticator)
  //}

  private def authenticator(credentials: Credentials): Option[Map[String, String]] = {
    credentials match {
      case Provided(c) =>
        if (c == "Xdctqr566") Some(Map.empty) else None
      case _ => None
    }
  }

  private def generateRequestId(): String = UUID.randomUUID().toString
}

trait HttpApi {
  def route(requestId: String): Route
}
