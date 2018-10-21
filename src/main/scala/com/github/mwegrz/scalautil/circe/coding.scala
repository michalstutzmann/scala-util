package com.github.mwegrz.scalautil.circe

import java.util.Base64

import akka.http.scaladsl.model.Uri
import io.circe.{ Decoder, Encoder, KeyDecoder, KeyEncoder }
import scodec.bits.ByteVector
import shapeless.{ ::, Generic, HNil, Lazy }

object coding {
  implicit def valueClassEncoder[ValueClass, Ref, Value](
      implicit generic: Lazy[Generic.Aux[ValueClass, Ref]],
      evidence: Ref <:< (Value :: HNil),
      encoder: Encoder[Value]): Encoder[ValueClass] =
    Encoder.instance { value ⇒
      encoder(generic.value.to(value).head)
    }

  implicit def valueClassDecoder[ValueClass, Ref, Value](
      implicit g: Lazy[Generic.Aux[ValueClass, Ref]],
      evidence: (Value :: HNil) =:= Ref,
      decoder: Decoder[Value]): Decoder[ValueClass] =
    Decoder.instance { cursor ⇒
      decoder(cursor).map { value ⇒
        g.value.from(value :: HNil)
      }
    }

  implicit val ByteVectorEncoder: Encoder[ByteVector] = Encoder.encodeString.contramap(_.toHex)
  implicit val ByteVectorDecoder: Decoder[ByteVector] =
    Decoder.decodeString.map(ByteVector.fromHex(_).get)

  implicit val UriEncoder: Encoder[Uri] = Encoder.encodeString.contramap(_.toString)
  implicit val UriDecoder: Decoder[Uri] = Decoder.decodeString.map(Uri(_))

  implicit def stringValueClassKeyEncoder[Key, Ref](
      implicit generic: Lazy[Generic.Aux[Key, Ref]],
      evidence: Ref <:< (String :: HNil)): KeyEncoder[Key] =
    new KeyEncoder[Key] {
      override def apply(key: Key): String = generic.value.to(key).head
    }

  implicit def stringValueClassKeyDecoder[Key, Ref](
      implicit generic: Lazy[Generic.Aux[Key, Ref]],
      evidence: (String :: HNil) =:= Ref): KeyDecoder[Key] =
    new KeyDecoder[Key] {
      override def apply(string: String): Option[Key] =
        Some(generic.value.from(string :: HNil))
    }

  implicit def byteArrayKeyEncoder: KeyEncoder[Array[Byte]] =
    new KeyEncoder[Array[Byte]] {
      override def apply(key: Array[Byte]): String =
        Base64.getEncoder.encodeToString(key)
    }

  implicit def byteArrayKeyDecoder: KeyDecoder[Array[Byte]] =
    new KeyDecoder[Array[Byte]] {
      override def apply(string: String): Option[Array[Byte]] =
        Some(Base64.getDecoder.decode(string))
    }
}
