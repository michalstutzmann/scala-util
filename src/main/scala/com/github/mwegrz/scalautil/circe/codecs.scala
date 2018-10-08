package com.github.mwegrz.scalautil.circe

import akka.http.scaladsl.model.Uri
import com.github.mwegrz.scalautil.{
  BigDecimalWrapper,
  ByteVectorWrapper,
  ByteWrapper,
  DoubleWrapper,
  FloatWrapper,
  IntWrapper,
  LongWrapper,
  ShortWrapper,
  StringWrapper
}
import io.circe.{ Decoder, Encoder, KeyDecoder, KeyEncoder }
import io.circe.syntax._
import io.circe.parser._
import pl.iterators.kebs.macros.CaseClass1Rep
import scodec.bits.ByteVector
import shapeless.{ ::, Generic, HNil, Lazy }

object codecs {
  implicit def byteWrapperEncoder[V <: ByteWrapper](
      implicit rep: CaseClass1Rep[V, Byte]): Encoder[V] =
    Encoder.encodeByte.contramap(rep.unapply)
  implicit def byteWrapperDecoder[V <: ByteWrapper](
      implicit rep: CaseClass1Rep[V, Byte]): Decoder[V] =
    Decoder.decodeByte.map(rep.apply)

  implicit def shortWrapperEncoder[V <: ShortWrapper](
      implicit rep: CaseClass1Rep[V, Short]): Encoder[V] =
    Encoder.encodeShort.contramap(rep.unapply)
  implicit def shortWrapperDecoder[V <: ShortWrapper](
      implicit rep: CaseClass1Rep[V, Short]): Decoder[V] =
    Decoder.decodeShort.map(rep.apply)

  implicit def intWrapperEncoder[V <: IntWrapper](implicit rep: CaseClass1Rep[V, Int]): Encoder[V] =
    Encoder.encodeInt.contramap(rep.unapply)
  implicit def intWrapperDecoder[V <: IntWrapper](implicit rep: CaseClass1Rep[V, Int]): Decoder[V] =
    Decoder.decodeInt.map(rep.apply)

  implicit def longWrapperEncoder[V <: LongWrapper](
      implicit rep: CaseClass1Rep[V, Long]): Encoder[V] =
    Encoder.encodeLong.contramap(rep.unapply)
  implicit def longWrapperDecoder[V <: LongWrapper](
      implicit rep: CaseClass1Rep[V, Long]): Decoder[V] =
    Decoder.decodeLong.map(rep.apply)

  implicit def floatWrapperEncoder[V <: FloatWrapper](
      implicit rep: CaseClass1Rep[V, Float]): Encoder[V] =
    Encoder.encodeFloat.contramap(rep.unapply)
  implicit def floatWrapperDecoder[V <: FloatWrapper](
      implicit rep: CaseClass1Rep[V, Float]): Decoder[V] =
    Decoder.decodeFloat.map(rep.apply)

  implicit def doubleWrapperEncoder[V <: DoubleWrapper](
      implicit rep: CaseClass1Rep[V, Double]): Encoder[V] =
    Encoder.encodeDouble.contramap(rep.unapply)
  implicit def doubleWrapperDecoder[V <: DoubleWrapper](
      implicit rep: CaseClass1Rep[V, Double]): Decoder[V] =
    Decoder.decodeDouble.map(rep.apply)

  implicit def stringWrapperEncoder[V <: StringWrapper](
      implicit rep: CaseClass1Rep[V, String]): Encoder[V] =
    Encoder.encodeString.contramap(rep.unapply)
  implicit def stringWrapperDecoder[V <: StringWrapper](
      implicit rep: CaseClass1Rep[V, String]): Decoder[V] =
    Decoder.decodeString.map(rep.apply)

  implicit def bigDecimalWrapperEncoder[V <: BigDecimalWrapper](
      implicit rep: CaseClass1Rep[V, BigDecimal]): Encoder[V] =
    Encoder.encodeBigDecimal.contramap(rep.unapply)
  implicit def bigDecimalWrapperDecoder[V <: BigDecimalWrapper](
      implicit rep: CaseClass1Rep[V, BigDecimal]): Decoder[V] =
    Decoder.decodeBigDecimal.map(rep.apply)

  implicit def byteVectorWrapperEncoder[V <: ByteVectorWrapper](
      implicit rep: CaseClass1Rep[V, ByteVector]): Encoder[V] =
    ByteVectorEncoder.contramap(rep.unapply)
  implicit def byteVectorWrapperDecoder[V <: ByteVectorWrapper](
      implicit rep: CaseClass1Rep[V, ByteVector]): Decoder[V] =
    ByteVectorDecoder.map(rep.apply)

  implicit def anyValEncoder[T <: AnyVal, V](implicit g: Lazy[Generic.Aux[T, V :: HNil]],
                                             e: Encoder[V]): Encoder[T] =
    Encoder.instance { value ⇒
      e(g.value.to(value).head)
    }

  implicit def anyValDecoder[T <: AnyVal, V](implicit g: Lazy[Generic.Aux[T, V :: HNil]],
                                             d: Decoder[V]): Decoder[T] =
    Decoder.instance { cursor ⇒
      d(cursor).map { value ⇒
        g.value.from(value :: HNil)
      }
    }

  implicit val ByteVectorEncoder: Encoder[ByteVector] = Encoder.encodeString.contramap(_.toBase64)
  implicit val ByteVectorDecoder: Decoder[ByteVector] =
    Decoder.decodeString.map(ByteVector.fromBase64(_).get)

  implicit val UriEncoder: Encoder[Uri] = Encoder.encodeString.contramap(_.toString)
  implicit val UriDecoder: Decoder[Uri] = Decoder.decodeString.map(Uri(_))

  implicit def encodeMapKey[A <: AnyRef](implicit encoder: Encoder[A]): KeyEncoder[A] =
    new KeyEncoder[A] {
      override def apply(key: A): String = key.asJson.toString
    }

  implicit def decodeMapKey[A <: AnyRef](implicit decoder: Decoder[A]): KeyDecoder[A] =
    new KeyDecoder[A] {
      override def apply(key: String): Option[A] = parse(key).toOption.flatMap(_.as[A].toOption)
    }
}
