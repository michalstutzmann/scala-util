package com.github.mwegrz.scalautil.ocupoly

import com.github.mwegrz.scalautil.jwt.{ JwtKey, decode }
import io.circe.generic.auto._
import io.circe.parser.parse
import io.circe.syntax._
import pdi.jwt.{ JwtAlgorithm, JwtClaim }

import scala.util.Try

object OcupolyJwtClaim {
  final case class JwtClaimContent(scope: String, organization: String)

  def apply(string: String)(implicit key: JwtKey, algorithm: JwtAlgorithm): Try[OcupolyJwtClaim] =
    fromString(string)

  def fromString(
      string: String
  )(implicit key: JwtKey, algorithm: JwtAlgorithm): Try[OcupolyJwtClaim] =
    decode(string, key.value, algorithm).map(fromJwtClaim)

  def fromJwtClaim(jwtClaim: JwtClaim): OcupolyJwtClaim = {
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
    val organization = if (jwtClaimContent.organization.nonEmpty) {
      jwtClaimContent.organization.split(" ").toSet
    } else {
      Set.empty[String]
    }
    OcupolyJwtClaim(sub, aud, iat, exp, scope, organization)
  }
}

final case class OcupolyJwtClaim(
    sub: String,
    aud: Set[String],
    iat: Long,
    exp: Option[Long],
    scope: Set[String],
    organization: Set[String]
) {
  def toJwtClaim: JwtClaim = {
    val jwt = JwtClaim()
      .about(sub)
      .to(aud)
      .issuedAt(iat)

    val jwtExpiresAt = exp.fold(jwt)(value => jwt.expiresAt(value))

    jwtExpiresAt + OcupolyJwtClaim
      .JwtClaimContent(scope = scope.mkString(" "), organization = organization.mkString(" "))
      .asJson
      .noSpaces
  }
}
