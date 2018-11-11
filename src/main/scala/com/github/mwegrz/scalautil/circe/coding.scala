package com.github.mwegrz.scalautil.circe

import java.util.Base64

import akka.http.scaladsl.model.Uri
import io.circe.generic.AutoDerivation
import io.circe.{ Decoder, Encoder, KeyDecoder, KeyEncoder }
import scodec.bits.ByteVector
import shapeless.{ ::, Generic, HNil, Lazy }

object coding extends AutoDerivation {
  implicit val UriEncoder: Encoder[Uri] = Encoder.encodeString.contramap(_.toString)
  implicit val UriDecoder: Decoder[Uri] = Decoder.decodeString.map(Uri(_))

  implicit def anyValValueClassEncoder[ValueClass, Ref, Value <: AnyVal](
      implicit generic: Lazy[Generic.Aux[ValueClass, Ref]],
      evidence: Ref <:< (Value :: HNil),
      encoder: Encoder[Value]): Encoder[ValueClass] =
    Encoder.instance { value ⇒
      encoder(generic.value.to(value).head)
    }

  implicit def anyValValueClassDecoder[ValueClass, Ref, Value <: AnyVal](
      implicit generic: Lazy[Generic.Aux[ValueClass, Ref]],
      evidence: (Value :: HNil) =:= Ref,
      decoder: Decoder[Value]): Decoder[ValueClass] =
    Decoder.instance { cursor ⇒
      decoder(cursor).map { value ⇒
        generic.value.from(value :: HNil)
      }
    }

  implicit def stringValueClassEncoder[ValueClass, Ref](
      implicit generic: Lazy[Generic.Aux[ValueClass, Ref]],
      evidence: Ref <:< (String :: HNil),
      encoder: Encoder[String]): Encoder[ValueClass] = Encoder.instance { value ⇒
    encoder(generic.value.to(value).head)
  }

  implicit def stringValueClassDecoder[ValueClass, Ref](
      implicit generic: Lazy[Generic.Aux[ValueClass, Ref]],
      evidence: (String :: HNil) =:= Ref,
      decoder: Decoder[String]): Decoder[ValueClass] = Decoder.instance { cursor ⇒
    decoder(cursor).map { value ⇒
      generic.value.from(value :: HNil)
    }
  }

  implicit def bigDecimalValueClassEncoder[ValueClass, Ref](
      implicit generic: Lazy[Generic.Aux[ValueClass, Ref]],
      evidence: Ref <:< (BigDecimal :: HNil),
      encoder: Encoder[BigDecimal]): Encoder[ValueClass] = Encoder.instance { value ⇒
    encoder(generic.value.to(value).head)
  }

  implicit def bigDecimalValueClassDecoder[ValueClass, Ref](
      implicit generic: Lazy[Generic.Aux[ValueClass, Ref]],
      evidence: (BigDecimal :: HNil) =:= Ref,
      decoder: Decoder[BigDecimal]): Decoder[ValueClass] = Decoder.instance { cursor ⇒
    decoder(cursor).map { value ⇒
      generic.value.from(value :: HNil)
    }
  }

  implicit val ByteVectorEncoder: Encoder[ByteVector] = Encoder.encodeString.contramap(_.toHex)
  implicit val ByteVectorDecoder: Decoder[ByteVector] =
    Decoder.decodeString.map(ByteVector.fromHex(_).get)

  /*implicit def byteVectorValueClassEncoder[ValueClass, Ref](
      implicit generic: Lazy[Generic.Aux[ValueClass, Ref]],
      evidence: Ref <:< (ByteVector :: HNil)): Encoder[ValueClass] = ???

  implicit def byteVectorValueClassDecoder[ValueClass, Ref](
      implicit g: Lazy[Generic.Aux[ValueClass, Ref]],
      evidence: (ByteVector :: HNil) =:= Ref): Decoder[ValueClass] = ???*/

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
