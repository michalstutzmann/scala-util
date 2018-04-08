package com.github.mwegrz.scalautil

import pdi.jwt.algorithms.{ JwtECDSAAlgorithm, JwtHmacAlgorithm, JwtRSAAlgorithm }
import pdi.jwt.{ JwtAlgorithm, JwtCirce, JwtClaim }

import scala.util.Try

package object jwt {
  def decode(token: String, key: String, algorithm: JwtAlgorithm): Try[JwtClaim] =
    algorithm match {
      case a: JwtHmacAlgorithm  => JwtCirce.decode(token, key, Seq(a))
      case a: JwtRSAAlgorithm   => JwtCirce.decode(token, key, Seq(a))
      case a: JwtECDSAAlgorithm => JwtCirce.decode(token, key, Seq(a))
    }

  def decode(token: String, key: String, algorithm: String): Try[JwtClaim] =
    decode(token, key, JwtAlgorithm.fromString(algorithm))
}
