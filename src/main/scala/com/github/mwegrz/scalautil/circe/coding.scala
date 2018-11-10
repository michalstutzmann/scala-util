package com.github.mwegrz.scalautil.circe

import java.util.Base64

import akka.http.scaladsl.model.Uri
import io.circe.{ Decoder, Encoder, KeyDecoder, KeyEncoder }
import scodec.bits.ByteVector
import shapeless.{ ::, Generic, HNil, Lazy }

object coding {
  def valueClassEncoder[ValueClass, Ref, Value](
      implicit generic: Lazy[Generic.Aux[ValueClass, Ref]],
      evidence: Ref <:< (Value :: HNil),
      encoder: Encoder[Value]): Encoder[ValueClass] =
    Encoder.instance { value ⇒
      encoder(generic.value.to(value).head)
    }

  def valueClassDecoder[ValueClass, Ref, Value](implicit g: Lazy[Generic.Aux[ValueClass, Ref]],
                                                evidence: (Value :: HNil) =:= Ref,
                                                decoder: Decoder[Value]): Decoder[ValueClass] =
    Decoder.instance { cursor ⇒
      decoder(cursor).map { value ⇒
        g.value.from(value :: HNil)
      }
    }

  implicit def charValueClassEncoder[ValueClass, Ref](
      implicit generic: Lazy[Generic.Aux[ValueClass, Ref]],
      evidence: Ref <:< (Char :: HNil),
      encoder: Encoder[Char]): Encoder[ValueClass] = valueClassEncoder[ValueClass, Ref, Char]

  implicit def charValueClassDecoder[ValueClass, Ref](
      implicit g: Lazy[Generic.Aux[ValueClass, Ref]],
      evidence: (Char :: HNil) =:= Ref,
      decoder: Decoder[Char]): Decoder[ValueClass] =
    valueClassDecoder[ValueClass, Ref, Char]

  implicit def byteValueClassEncoder[ValueClass, Ref](
      implicit generic: Lazy[Generic.Aux[ValueClass, Ref]],
      evidence: Ref <:< (Byte :: HNil),
      encoder: Encoder[Byte]): Encoder[ValueClass] = valueClassEncoder[ValueClass, Ref, Byte]

  implicit def byteValueClassDecoder[ValueClass, Ref](
      implicit g: Lazy[Generic.Aux[ValueClass, Ref]],
      evidence: (Byte :: HNil) =:= Ref,
      decoder: Decoder[Byte]): Decoder[ValueClass] =
    valueClassDecoder[ValueClass, Ref, Byte]

  implicit def shortValueClassEncoder[ValueClass, Ref](
      implicit generic: Lazy[Generic.Aux[ValueClass, Ref]],
      evidence: Ref <:< (Short :: HNil),
      encoder: Encoder[Short]): Encoder[ValueClass] = valueClassEncoder[ValueClass, Ref, Short]

  implicit def shortValueClassDecoder[ValueClass, Ref](
      implicit g: Lazy[Generic.Aux[ValueClass, Ref]],
      evidence: (Short :: HNil) =:= Ref,
      decoder: Decoder[Short]): Decoder[ValueClass] =
    valueClassDecoder[ValueClass, Ref, Short]

  implicit def intValueClassEncoder[ValueClass, Ref](
      implicit generic: Lazy[Generic.Aux[ValueClass, Ref]],
      evidence: Ref <:< (Int :: HNil),
      encoder: Encoder[Int]): Encoder[ValueClass] = valueClassEncoder[ValueClass, Ref, Int]

  implicit def intValueClassDecoder[ValueClass, Ref](implicit g: Lazy[Generic.Aux[ValueClass, Ref]],
                                                     evidence: (Int :: HNil) =:= Ref,
                                                     decoder: Decoder[Int]): Decoder[ValueClass] =
    valueClassDecoder[ValueClass, Ref, Int]

  implicit def longValueClassEncoder[ValueClass, Ref](
      implicit generic: Lazy[Generic.Aux[ValueClass, Ref]],
      evidence: Ref <:< (Long :: HNil),
      encoder: Encoder[Long]): Encoder[ValueClass] = valueClassEncoder[ValueClass, Ref, Long]

  implicit def longValueClassDecoder[ValueClass, Ref](
      implicit g: Lazy[Generic.Aux[ValueClass, Ref]],
      evidence: (Long :: HNil) =:= Ref,
      decoder: Decoder[Long]): Decoder[ValueClass] =
    valueClassDecoder[ValueClass, Ref, Long]

  implicit def floatValueClassEncoder[ValueClass, Ref](
      implicit generic: Lazy[Generic.Aux[ValueClass, Ref]],
      evidence: Ref <:< (Float :: HNil),
      encoder: Encoder[Float]): Encoder[ValueClass] = valueClassEncoder[ValueClass, Ref, Float]

  implicit def floatValueClassDecoder[ValueClass, Ref](
      implicit g: Lazy[Generic.Aux[ValueClass, Ref]],
      evidence: (Float :: HNil) =:= Ref,
      decoder: Decoder[Float]): Decoder[ValueClass] =
    valueClassDecoder[ValueClass, Ref, Float]

  implicit def doubleValueClassEncoder[ValueClass, Ref](
      implicit generic: Lazy[Generic.Aux[ValueClass, Ref]],
      evidence: Ref <:< (Double :: HNil),
      encoder: Encoder[Double]): Encoder[ValueClass] = valueClassEncoder[ValueClass, Ref, Double]

  implicit def doubleValueClassDecoder[ValueClass, Ref](
      implicit g: Lazy[Generic.Aux[ValueClass, Ref]],
      evidence: (Double :: HNil) =:= Ref,
      decoder: Decoder[Double]): Decoder[ValueClass] =
    valueClassDecoder[ValueClass, Ref, Double]

  implicit def stringValueClassEncoder[ValueClass, Ref](
      implicit generic: Lazy[Generic.Aux[ValueClass, Ref]],
      evidence: Ref <:< (String :: HNil),
      encoder: Encoder[String]): Encoder[ValueClass] = valueClassEncoder[ValueClass, Ref, String]

  implicit def stringValueClassDecoder[ValueClass, Ref](
      implicit g: Lazy[Generic.Aux[ValueClass, Ref]],
      evidence: (String :: HNil) =:= Ref,
      decoder: Decoder[String]): Decoder[ValueClass] =
    valueClassDecoder[ValueClass, Ref, String]

  implicit def bigDecimalValueClassEncoder[ValueClass, Ref](
      implicit generic: Lazy[Generic.Aux[ValueClass, Ref]],
      evidence: Ref <:< (BigDecimal :: HNil),
      encoder: Encoder[BigDecimal]): Encoder[ValueClass] =
    valueClassEncoder[ValueClass, Ref, BigDecimal]

  implicit def bigDecimalValueClassDecoder[ValueClass, Ref](
      implicit g: Lazy[Generic.Aux[ValueClass, Ref]],
      evidence: (BigDecimal :: HNil) =:= Ref,
      decoder: Decoder[BigDecimal]): Decoder[ValueClass] =
    valueClassDecoder[ValueClass, Ref, BigDecimal]

  implicit val ByteVectorEncoder: Encoder[ByteVector] = Encoder.encodeString.contramap(_.toHex)
  implicit val ByteVectorDecoder: Decoder[ByteVector] =
    Decoder.decodeString.map(ByteVector.fromHex(_).get)

  implicit def byteVectorValueClassEncoder[ValueClass, Ref](
      implicit generic: Lazy[Generic.Aux[ValueClass, Ref]],
      evidence: Ref <:< (ByteVector :: HNil)): Encoder[ValueClass] =
    valueClassEncoder[ValueClass, Ref, ByteVector]

  implicit def byteVectorValueClassDecoder[ValueClass, Ref](
      implicit g: Lazy[Generic.Aux[ValueClass, Ref]],
      evidence: (ByteVector :: HNil) =:= Ref): Decoder[ValueClass] =
    valueClassDecoder[ValueClass, Ref, ByteVector]

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
