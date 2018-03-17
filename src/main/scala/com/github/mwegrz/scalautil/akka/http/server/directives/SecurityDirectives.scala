package com.github.mwegrz.scalautil.akka.http.server.directives

import akka.http.scaladsl.server.Directives.Authenticator
import akka.http.scaladsl.server.directives.Credentials.{ Missing, Provided }
import com.github.mwegrz.scalautil.auth0.Auth0JwtClaim
import com.typesafe.config.Config
import pdi.jwt.{ JwtAlgorithm, JwtCirce, JwtClaim }
import pdi.jwt.algorithms.{ JwtECDSAAlgorithm, JwtHmacAlgorithm, JwtRSAAlgorithm }

object SecurityDirectives {
  def auth0JwtAuthenticator(config: Config): Authenticator[Auth0JwtClaim] =
    credentials => jwtAuthenticator(config)(credentials).map(Auth0JwtClaim.fromJwtClaim)

  def jwtAuthenticator(config: Config): Authenticator[JwtClaim] = {
    val key = config.getString("key")
    val algorithm = JwtAlgorithm.fromString(config.getString("algorithm"))
    jwtAuthenticator(key, algorithm)
  }

  def jwtAuthenticator(key: String, algorithm: JwtAlgorithm): Authenticator[JwtClaim] = {
    val decode = algorithm match {
      case a: JwtHmacAlgorithm =>
        (identifier: String) =>
          JwtCirce.decode(identifier, key, Seq(a))
      case a: JwtRSAAlgorithm =>
        (identifier: String) =>
          JwtCirce.decode(identifier, key, Seq(a))
      case a: JwtECDSAAlgorithm =>
        (identifier: String) =>
          JwtCirce.decode(identifier, key, Seq(a))
    }

    {
      case Provided(id) => decode(id).toOption
      case Missing      => None
    }
  }
}
