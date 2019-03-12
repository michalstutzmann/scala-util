package com.github.mwegrz.scalautil.circe

import java.util.Base64

import akka.http.scaladsl.model.Uri
import io.circe.{ Decoder, Encoder, KeyDecoder, KeyEncoder }
import pl.iterators.kebs.macros.CaseClass1Rep
import scodec.bits.{ BitVector, ByteVector }
import shapeless.{ ::, Generic, HNil, Lazy }

object coding {
  implicit def valueClassEncoder[CC <: AnyVal, A](implicit rep: CaseClass1Rep[CC, A],
                                                  delegate: Encoder[A]): Encoder[CC] =
    delegate.contramap(rep.unapply)

  implicit def valueClassFromValue[CC <: AnyVal, B](implicit rep: CaseClass1Rep[CC, B],
                                                    delegate: Decoder[B]): Decoder[CC] =
    delegate.map { rep.apply }

  implicit val ByteVectorEncoder: Encoder[ByteVector] = Encoder.encodeString.contramap(_.toHex)
  implicit val ByteVectorDecoder: Decoder[ByteVector] =
    Decoder.decodeString.map(ByteVector.fromHex(_).get)

  implicit val BitVectorEncoder: Encoder[BitVector] = Encoder.encodeString.contramap(_.toBin)
  implicit val BitVectorDecoder: Decoder[BitVector] =
    Decoder.decodeString.map(BitVector.fromBin(_).get)

  implicit val UriEncoder: Encoder[Uri] = Encoder.encodeString.contramap(_.toString)
  implicit val UriDecoder: Decoder[Uri] = Decoder.decodeString.map(Uri(_))

  implicit def stringValueClassKeyEncoder[Key, Ref](
      implicit generic: Lazy[Generic.Aux[Key, Ref]],
      evidence: Ref <:< (String :: HNil)): KeyEncoder[Key] = key => generic.value.to(key).head

  implicit def stringValueClassKeyDecoder[Key, Ref](
      implicit generic: Lazy[Generic.Aux[Key, Ref]],
      evidence: (String :: HNil) =:= Ref): KeyDecoder[Key] =
    string => Some(generic.value.from(string :: HNil))

  implicit def byteArrayKeyEncoder: KeyEncoder[Array[Byte]] = Base64.getEncoder.encodeToString

  implicit def byteArrayKeyDecoder: KeyDecoder[Array[Byte]] =
    string => Some(Base64.getDecoder.decode(string))
}
