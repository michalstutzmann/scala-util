package com.github.mwegrz.scalautil.auth0

import java.time.Instant

import io.circe.generic.auto._
import io.circe.parser.parse
import pdi.jwt.JwtClaim

object Auth0JwtClaim {
  case class JwtClaimContent(azp: String, scope: String, gty: String)

  def fromJwtClaim(jwtClaim: JwtClaim): Auth0JwtClaim = {
    val iss = jwtClaim.issuer.get
    val sub = jwtClaim.subject.get
    val aud = jwtClaim.audience.get
    val iat = Instant.ofEpochSecond(jwtClaim.issuedAt.get)
    val exp = Instant.ofEpochSecond(jwtClaim.expiration.get)
    val jwtClaimContent = parse(jwtClaim.content).toTry.flatMap(_.as[JwtClaimContent].toTry).get
    val azp = jwtClaimContent.azp
    val scope = jwtClaimContent.scope.split(" ").toSet
    val gty = jwtClaimContent.gty
    Auth0JwtClaim(iss, sub, aud, iat, exp, azp, scope, gty)
  }
}

case class Auth0JwtClaim(
    iss: String,
    sub: String,
    aud: Set[String],
    iat: Instant,
    exp: Instant,
    azp: String,
    scope: Set[String],
    gty: String
)
