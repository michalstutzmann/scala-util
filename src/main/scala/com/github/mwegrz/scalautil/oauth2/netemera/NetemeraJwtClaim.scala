package com.github.mwegrz.scalautil.oauth2.netemera

import com.github.mwegrz.scalautil.jwt.{ JwtKey, decode }
import io.circe.syntax._
import io.circe.generic.auto._
import io.circe.parser.parse
import pdi.jwt.{ JwtAlgorithm, JwtClaim }

import scala.util.Try

object NetemeraJwtClaim {
  final case class JwtClaimContent(scope: String, organization: String)

  def apply(string: String)(implicit key: JwtKey, algorithm: JwtAlgorithm): Try[NetemeraJwtClaim] =
    fromString(string)

  def fromString(
      string: String
  )(implicit key: JwtKey, algorithm: JwtAlgorithm): Try[NetemeraJwtClaim] =
    decode(string, key.value, algorithm).map(fromJwtClaim)

  def fromJwtClaim(jwtClaim: JwtClaim): NetemeraJwtClaim = {
    val iss = jwtClaim.issuer.get
    val sub = jwtClaim.subject.get
    val aud = jwtClaim.audience.get
    val iat = jwtClaim.issuedAt.get
    val exp = jwtClaim.expiration
    val jwtClaimContent = parse(jwtClaim.content).toTry.flatMap(_.as[JwtClaimContent].toTry).get
    val scope = if (jwtClaimContent.scope.nonEmpty) {
      jwtClaimContent.scope.split(" ").toSet
    } else {
      Set.empty[String]
    }
    val organization = jwtClaimContent.organization
    NetemeraJwtClaim(iss, sub, aud, iat, exp, scope, organization)
  }
}

final case class NetemeraJwtClaim(
    iss: String,
    sub: String,
    aud: Set[String],
    iat: Long,
    exp: Option[Long],
    scope: Set[String],
    organization: String
) {
  def toJwtClaim: JwtClaim = {
    val jwt = JwtClaim()
      .by(iss)
      .about(sub)
      .to(aud)
      .issuedAt(iat)

    val jwtExpiresAt = exp.fold(jwt)(value => jwt.expiresAt(value))

    jwtExpiresAt + NetemeraJwtClaim
      .JwtClaimContent(scope = scope.mkString(" "), organization = organization)
      .asJson
      .noSpaces
  }
}
