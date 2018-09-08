package com.github.mwegrz.scalautil.oauth2.netemera

import com.github.mwegrz.scalautil.jwt.decode
import io.circe.syntax._
import io.circe.generic.auto._
import io.circe.parser.parse
import pdi.jwt.{ JwtAlgorithm, JwtClaim }

import scala.util.Try

object NetemeraJwtClaim {
  case class JwtClaimContent(scope: String, group: String)

  def fromString(string: String, key: String, algorithm: JwtAlgorithm): Try[NetemeraJwtClaim] =
    decode(string, key, algorithm).map(fromJwtClaim)

  def fromString(string: String, key: String, algorithm: String): Try[NetemeraJwtClaim] =
    decode(string, key, algorithm).map(fromJwtClaim)

  def fromJwtClaim(jwtClaim: JwtClaim): NetemeraJwtClaim = {
    val iss = jwtClaim.issuer.get
    val sub = jwtClaim.subject.get
    val aud = jwtClaim.audience.get
    val iat = jwtClaim.issuedAt.get
    val exp = jwtClaim.expiration.get
    val jwtClaimContent = parse(jwtClaim.content).toTry.flatMap(_.as[JwtClaimContent].toTry).get
    val scope = jwtClaimContent.scope.split(" ").toSet
    val organization = jwtClaimContent.group
    NetemeraJwtClaim(iss, sub, aud, iat, exp, scope, organization)
  }
}

case class NetemeraJwtClaim(
    iss: String,
    sub: String,
    aud: Set[String],
    iat: Long,
    exp: Long,
    scope: Set[String],
    group: String
) {
  def toJwtClaim: JwtClaim =
    JwtClaim()
      .by(iss)
      .about(sub)
      .to(aud)
      .issuedAt(iat)
      .expiresAt(exp) +
      NetemeraJwtClaim.JwtClaimContent(scope = scope.mkString(" "), group = group).asJson.noSpaces
}
