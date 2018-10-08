package com.github.mwegrz.scalautil.avro4s

import java.time.Duration

import akka.http.scaladsl.model.Uri
import com.github.mwegrz.scalautil.{
  BigDecimalWrapper,
  ByteVectorWrapper,
  DoubleWrapper,
  FloatWrapper,
  IntWrapper,
  LongWrapper,
  ShortWrapper,
  StringWrapper
}
import com.sksamuel.avro4s._
import org.apache.avro.Schema
import pl.iterators.kebs.macros.CaseClass1Rep
import scodec.bits.ByteVector
import shapeless.{ ::, Generic, HNil, Lazy }

object codecs {
  implicit def byteWrapperSchemaFor[V <: ShortWrapper]: SchemaFor[V] =
    SchemaFor.const(Schema.create(Schema.Type.INT))
  implicit def byteWrapperEncoder[V <: ShortWrapper](
      implicit rep: CaseClass1Rep[V, Byte]): Encoder[V] =
    Encoder.ByteEncoder.comap(rep.unapply)
  implicit def byteWrapperDecoder[V <: ShortWrapper](
      implicit rep: CaseClass1Rep[V, Byte]): Decoder[V] =
    Decoder.ByteDecoder.map(rep.apply)

  implicit def shortWrapperSchemaFor[V <: ShortWrapper]: SchemaFor[V] =
    SchemaFor.const(Schema.create(Schema.Type.INT))
  implicit def shortWrapperEncoder[V <: ShortWrapper](
      implicit rep: CaseClass1Rep[V, Short]): Encoder[V] =
    Encoder.ShortEncoder.comap(rep.unapply)
  implicit def shortWrapperDecoder[V <: ShortWrapper](
      implicit rep: CaseClass1Rep[V, Short]): Decoder[V] =
    Decoder.ShortDecoder.map(rep.apply)

  implicit def intWrapperSchemaFor[V <: IntWrapper]: SchemaFor[V] =
    SchemaFor.const(Schema.create(Schema.Type.INT))
  implicit def intWrapperEncoder[V <: IntWrapper](implicit rep: CaseClass1Rep[V, Int]): Encoder[V] =
    Encoder.IntEncoder.comap(rep.unapply)
  implicit def intWrapperDecoder[V <: IntWrapper](implicit rep: CaseClass1Rep[V, Int]): Decoder[V] =
    Decoder.IntDecoder.map(rep.apply)

  implicit def longWrapperSchemaFor[V <: LongWrapper]: SchemaFor[V] =
    SchemaFor.const(Schema.create(Schema.Type.LONG))
  implicit def longWrapperEncoder[V <: LongWrapper](
      implicit rep: CaseClass1Rep[V, Long]): Encoder[V] =
    Encoder.LongEncoder.comap(rep.unapply)
  implicit def longWrapperDecoder[V <: LongWrapper](
      implicit rep: CaseClass1Rep[V, Long]): Decoder[V] =
    Decoder.LongDecoder.map(rep.apply)

  implicit def floatWrapperSchemaFor[V <: FloatWrapper]: SchemaFor[V] =
    SchemaFor.const(Schema.create(Schema.Type.FLOAT))
  implicit def floatWrapperEncoder[V <: LongWrapper](
      implicit rep: CaseClass1Rep[V, Float]): Encoder[V] =
    Encoder.FloatEncoder.comap(rep.unapply)
  implicit def floatWrapperDecoder[V <: LongWrapper](
      implicit rep: CaseClass1Rep[V, Float]): Decoder[V] =
    Decoder.FloatDecoder.map(rep.apply)

  implicit def doubleWrapperSchemaFor[V <: DoubleWrapper]: SchemaFor[V] =
    SchemaFor.const(Schema.create(Schema.Type.DOUBLE))
  implicit def doubleWrapperEncoder[V <: DoubleWrapper](
      implicit rep: CaseClass1Rep[V, Double]): Encoder[V] =
    Encoder.DoubleEncoder.comap(rep.unapply)
  implicit def doubleWrapperDecoder[V <: DoubleWrapper](
      implicit rep: CaseClass1Rep[V, Double]): Decoder[V] =
    Decoder.DoubleDecoder.map(rep.apply)

  implicit def stringWrapperSchemaFor[V <: StringWrapper]: SchemaFor[V] =
    SchemaFor.const(Schema.create(Schema.Type.DOUBLE))
  implicit def stringWrapperEncoder[V <: StringWrapper](
      implicit rep: CaseClass1Rep[V, String]): Encoder[V] =
    Encoder.StringEncoder.comap(rep.unapply)
  implicit def stringWrapperDecoder[V <: StringWrapper](
      implicit rep: CaseClass1Rep[V, String]): Decoder[V] =
    Decoder.StringDecoder.map(rep.apply)

  implicit def bigDecimalWrapperSchemaFor[V <: BigDecimalWrapper]: SchemaFor[V] =
    SchemaFor.const(Schema.create(Schema.Type.BYTES))
  implicit def bigDecimalWrapperEncoder[V <: BigDecimalWrapper](
      implicit rep: CaseClass1Rep[V, BigDecimal]): Encoder[V] =
    Encoder.BigDecimalEncoder.comap(rep.unapply)
  implicit def bigDecimalWrapperDecoder[V <: BigDecimalWrapper](
      implicit rep: CaseClass1Rep[V, BigDecimal]): Decoder[V] =
    Decoder.bigDecimalDecoder.map(rep.apply)

  implicit def byteVectorWrapperSchemaFor[V <: ByteVectorWrapper]: SchemaFor[V] =
    SchemaFor.const(Schema.create(Schema.Type.BYTES))
  implicit def byteVectorWrapperEncoder[V <: ByteVectorWrapper](
      implicit rep: CaseClass1Rep[V, ByteVector]): Encoder[V] =
    ByteVectorEncoder.comap(rep.unapply)
  implicit def byteVectorWrapperDecoder[V <: ByteVectorWrapper](
      implicit rep: CaseClass1Rep[V, ByteVector]): Decoder[V] =
    ByteVectorDecoder.map(rep.apply)

  implicit val ByteVectorSchemaFor: SchemaFor[ByteVector] =
    SchemaFor.const(Schema.create(Schema.Type.BYTES))
  implicit val ByteVectorEncoder: Encoder[ByteVector] =
    Encoder.ByteVectorEncoder.comap(_.toArray.toVector)
  implicit val ByteVectorDecoder: Decoder[ByteVector] = Decoder.ByteVectorDecoder.map(ByteVector(_))

  implicit val DurationSchemaFor: SchemaFor[Duration] =
    SchemaFor.const(Schema.create(Schema.Type.STRING))
  implicit val DurationEncoder: Encoder[Duration] = Encoder.StringEncoder.comap(_.toString)
  implicit val DurationDecoder: Decoder[Duration] = Decoder.StringDecoder.map(Duration.parse)

  implicit val UriSchemaFor: SchemaFor[Uri] = SchemaFor.const(Schema.create(Schema.Type.STRING))
  implicit val UriEncoder: Encoder[Uri] = Encoder.StringEncoder.comap(_.toString)
  implicit val UriDecoder: Decoder[Uri] = Decoder.StringDecoder.map(Uri(_))

  implicit def typedKeyMapSchemaFor[K, V](
      implicit valueSchemaFor: SchemaFor[V]): SchemaFor[Map[K, V]] =
    SchemaFor.const(Schema.createMap(valueSchemaFor.schema))

  implicit def typedKeyMapEncoder[K, V](
      implicit valueEncoder: Encoder[V],
      generic: Lazy[Generic.Aux[K, String :: HNil]]): Encoder[Map[K, V]] =
    Encoder.mapEncoder[V].comap(_.map { case (key, value) => (generic.value.to(key).head, value) })

  implicit def typedKeyMapDecoder[K, V](
      implicit valueDecoder: Decoder[V],
      generic: Lazy[Generic.Aux[K, String :: HNil]]): Decoder[Map[K, V]] =
    Decoder
      .mapDecoder[V]
      .map(_.map { case (key, value) => (generic.value.from(key :: HNil), value) })
}
