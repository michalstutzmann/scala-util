package com.github.mwegrz.scalautil.akka.http

import java.time.Instant

import akka.http.scaladsl.model.Uri
import akka.http.scaladsl.unmarshalling.Unmarshaller
import com.github.mwegrz.scalautil.jwt.JwtKey
import com.github.mwegrz.scalautil.oauth2.ResponseType
import com.github.mwegrz.scalautil.oauth2.ocupoly.OcupolyJwtClaim
import pdi.jwt.JwtAlgorithm
import shapeless.{ ::, Generic, HNil, Lazy }

object unmarshalling {
  implicit def stringToStringValueClassUnmarshaller[ValueClass, Ref](implicit
      generic: Lazy[Generic.Aux[ValueClass, Ref]],
      evidence: (String :: HNil) =:= Ref
  ): Unmarshaller[String, ValueClass] =
    Unmarshaller.strict { value => generic.value.from(value :: HNil) }

  implicit def stringToOcupolyJwtClaimUnmarshaller(implicit
      jwtKey: JwtKey,
      jwtAlgorithm: JwtAlgorithm
  ): Unmarshaller[String, OcupolyJwtClaim] =
    Unmarshaller.strict(OcupolyJwtClaim.fromString(_).get)

  implicit val stringToUriUnmarshaller: Unmarshaller[String, Uri] =
    Unmarshaller.strict(Uri(_))

  implicit val stringToInstantUnmarshaller: Unmarshaller[String, Instant] =
    Unmarshaller.strict(Instant.parse)

  implicit val stringToResponseTypeUnmarshaller: Unmarshaller[String, ResponseType] =
    Unmarshaller.strict(ResponseType(_))
}
