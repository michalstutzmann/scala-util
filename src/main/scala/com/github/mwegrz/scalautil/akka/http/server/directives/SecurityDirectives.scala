package com.github.mwegrz.scalautil.akka.http.server.directives

import akka.http.scaladsl.model.headers.{ HttpChallenges, HttpCredentials, OAuth2BearerToken }
import akka.http.scaladsl.server.AuthenticationFailedRejection.{ CredentialsMissing, CredentialsRejected }
import akka.http.scaladsl.server.{ AuthenticationFailedRejection, Directive1 }
import akka.http.scaladsl.server.Directives.{
  AsyncAuthenticator,
  Authenticator,
  authenticateOrRejectWithChallenge,
  parameter
}
import akka.http.scaladsl.server.directives.BasicDirectives.{ extractExecutionContext, provide }
import akka.http.scaladsl.server.directives.{ AuthenticationDirective, AuthenticationResult, Credentials }
import akka.http.scaladsl.server.directives.FutureDirectives.onSuccess
import akka.http.scaladsl.server.directives.RouteDirectives.reject
import akka.http.scaladsl.util.FastFuture
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.directives.Credentials.{ Missing, Provided }
import akka.http.scaladsl.util.FastFuture.EnhancedFuture
import pdi.jwt.{ JwtAlgorithm, JwtCirce, JwtClaim }
import pdi.jwt.algorithms.{ JwtECDSAAlgorithm, JwtHmacAlgorithm, JwtRSAAlgorithm }

object SecurityDirectives {

  def jwtAuthenticator(secret: String, algorithm: JwtAlgorithm): Authenticator[JwtClaim] = {
    val decode = algorithm match {
      case a: JwtHmacAlgorithm =>
        (identifier: String) =>
          JwtCirce.decode(identifier, secret, Seq(a))
      case a: JwtRSAAlgorithm =>
        (identifier: String) =>
          JwtCirce.decode(identifier, secret, Seq(a))
      case a: JwtECDSAAlgorithm =>
        (identifier: String) =>
          JwtCirce.decode(identifier, secret, Seq(a))
    }

    {
      case Provided(id) => decode(id).toOption
      case Missing      => None
    }
  }

  /** Support `access_token` URI parameter as described in https://tools.ietf.org/html/rfc6750#section-2.3 */
  def authenticateOAuth2[T](realm: String, authenticator: Authenticator[T]): AuthenticationDirective[T] =
    authenticateOAuth2Async(realm, cred ⇒ FastFuture.successful(authenticator(cred)))

  private def authenticateOAuth2Async[T](realm: String,
                                         authenticator: AsyncAuthenticator[T]): AuthenticationDirective[T] =
    extractExecutionContext.flatMap { implicit ec ⇒
      val accessToken = parameter('access_token.?)

      def liftedAuthenticator(cred: Option[HttpCredentials]) = authenticator(Credentials(cred)).fast.map {
        case Some(t) ⇒ AuthenticationResult.success(t)
        case None ⇒ AuthenticationResult.failWithChallenge(HttpChallenges.oAuth2(realm))
      }

      accessToken.flatMap {
        case Some(_) =>
          accessToken.flatMap { cred ⇒
            onSuccess(liftedAuthenticator(cred.map(OAuth2BearerToken))).flatMap {
              case Right(user) ⇒ provide(user)
              case Left(challenge) ⇒
                val cause = if (cred.isEmpty) CredentialsMissing else CredentialsRejected
                reject(AuthenticationFailedRejection(cause, challenge)): Directive1[T]
            }
          }
        case None =>
          authenticateOrRejectWithChallenge[OAuth2BearerToken, T](liftedAuthenticator)
      }
    }
}
