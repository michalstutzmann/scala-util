package com.github.mwegrz.scalautil.akka.http

import akka.http.scaladsl.model.Uri
import akka.http.scaladsl.unmarshalling.Unmarshaller
import com.github.mwegrz.scalautil.oauth2.{ JwtKey, ResponseType }
import com.github.mwegrz.scalautil.oauth2.netemera.NetemeraJwtClaim
import pdi.jwt.JwtAlgorithm
import shapeless.{ ::, Generic, HNil, Lazy }

import scala.concurrent.Future

object unmarshalling {
  /*implicit def valueClassUnmarshaller[ValueClass, Ref, Value, A](
      implicit generic: Lazy[Generic.Aux[ValueClass, Ref]],
      evidence: (Value :: HNil) =:= Ref,
      unmarshaller: Unmarshaller[A, Value]): Unmarshaller[A, ValueClass] =
    unmarshaller.map { value =>
      generic.value.from(value :: HNil)
    }*/

  implicit def stringToStringValueClassUnmarshaller[ValueClass, Ref](
      implicit generic: Lazy[Generic.Aux[ValueClass, Ref]],
      evidence: (String :: HNil) =:= Ref,
      unmarshaller: Unmarshaller[String, String]): Unmarshaller[String, ValueClass] =
    unmarshaller.map { value =>
      generic.value.from(value :: HNil)
    }

  implicit def stringToNetemeraJwtClaimUnmarshaller(
      implicit jwtKey: JwtKey,
      jwtAlgorithm: JwtAlgorithm): Unmarshaller[String, NetemeraJwtClaim] =
    createUnmarshaller[String, NetemeraJwtClaim](NetemeraJwtClaim.fromString(_).get)

  implicit val stringToUriUnmarshaller: Unmarshaller[String, Uri] =
    createUnmarshaller[String, Uri](Uri(_))

  implicit val stringToResponseTypeUnmarshaller: Unmarshaller[String, ResponseType] =
    createUnmarshaller[String, ResponseType](ResponseType(_))

  def createUnmarshaller[A, B](f: A => B): Unmarshaller[A, B] =
    Unmarshaller(_ => a => Future.successful(f(a)))
}
