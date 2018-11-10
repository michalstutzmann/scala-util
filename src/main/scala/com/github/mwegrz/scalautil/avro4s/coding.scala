package com.github.mwegrz.scalautil.avro4s

import java.time.Duration

import akka.http.scaladsl.model.Uri
import com.sksamuel.avro4s._
import io.circe.{ KeyDecoder, KeyEncoder }
import org.apache.avro.Schema
import scodec.bits.ByteVector
import shapeless.{ ::, Generic, HNil, Lazy }

object coding {
  def valueClassSchemaFor[ValueClass, Ref, Value](
      implicit generic: Lazy[Generic.Aux[ValueClass, Ref]],
      evidence: Ref <:< (Value :: HNil),
      schemaFor: SchemaFor[Value]): SchemaFor[ValueClass] =
    SchemaFor.const(schemaFor.schema)

  def valueClassEncoder[ValueClass, Ref, Value](
      implicit generic: Lazy[Generic.Aux[ValueClass, Ref]],
      evidence: Ref <:< (Value :: HNil),
      encoder: Encoder[Value]): Encoder[ValueClass] =
    encoder.comap(generic.value.to(_).head)

  def valueClassDecoder[ValueClass, Ref, Value](
      implicit generic: Lazy[Generic.Aux[ValueClass, Ref]],
      evidence: (Value :: HNil) =:= Ref,
      decoder: Decoder[Value]): Decoder[ValueClass] =
    decoder.map { value =>
      generic.value.from(value :: HNil)
    }

  implicit def charValueClassSchemaFor[ValueClass, Ref](
      implicit generic: Lazy[Generic.Aux[ValueClass, Ref]],
      evidence: Ref <:< (Char :: HNil),
      schemaFor: SchemaFor[Char]): SchemaFor[ValueClass] =
    valueClassSchemaFor[ValueClass, Ref, Char]

  implicit def charValueClassEncoder[ValueClass, Ref](
      implicit generic: Lazy[Generic.Aux[ValueClass, Ref]],
      evidence: Ref <:< (Char :: HNil),
      encoder: Encoder[Char]): Encoder[ValueClass] = valueClassEncoder[ValueClass, Ref, Char]

  implicit def charValueClassDecoder[ValueClass, Ref](
      implicit g: Lazy[Generic.Aux[ValueClass, Ref]],
      evidence: (Char :: HNil) =:= Ref,
      decoder: Decoder[Char]): Decoder[ValueClass] =
    valueClassDecoder[ValueClass, Ref, Char]

  implicit def byteValueClassSchemaFor[ValueClass, Ref](
      implicit generic: Lazy[Generic.Aux[ValueClass, Ref]],
      evidence: Ref <:< (Byte :: HNil),
      schemaFor: SchemaFor[Byte]): SchemaFor[ValueClass] =
    valueClassSchemaFor[ValueClass, Ref, Byte]

  implicit def byteValueClassEncoder[ValueClass, Ref](
      implicit generic: Lazy[Generic.Aux[ValueClass, Ref]],
      evidence: Ref <:< (Byte :: HNil),
      encoder: Encoder[Byte]): Encoder[ValueClass] = valueClassEncoder[ValueClass, Ref, Byte]

  implicit def byteValueClassDecoder[ValueClass, Ref](
      implicit g: Lazy[Generic.Aux[ValueClass, Ref]],
      evidence: (Byte :: HNil) =:= Ref,
      decoder: Decoder[Byte]): Decoder[ValueClass] =
    valueClassDecoder[ValueClass, Ref, Byte]

  implicit def shortValueClassSchemaFor[ValueClass, Ref](
      implicit generic: Lazy[Generic.Aux[ValueClass, Ref]],
      evidence: Ref <:< (Short :: HNil),
      schemaFor: SchemaFor[Short]): SchemaFor[ValueClass] =
    valueClassSchemaFor[ValueClass, Ref, Short]

  implicit def shortValueClassEncoder[ValueClass, Ref](
      implicit generic: Lazy[Generic.Aux[ValueClass, Ref]],
      evidence: Ref <:< (Short :: HNil),
      encoder: Encoder[Short]): Encoder[ValueClass] = valueClassEncoder[ValueClass, Ref, Short]

  implicit def shortValueClassDecoder[ValueClass, Ref](
      implicit g: Lazy[Generic.Aux[ValueClass, Ref]],
      evidence: (Short :: HNil) =:= Ref,
      decoder: Decoder[Short]): Decoder[ValueClass] =
    valueClassDecoder[ValueClass, Ref, Short]

  implicit def intValueClassSchemaFor[ValueClass, Ref](
      implicit generic: Lazy[Generic.Aux[ValueClass, Ref]],
      evidence: Ref <:< (Int :: HNil),
      schemaFor: SchemaFor[Int]): SchemaFor[ValueClass] = valueClassSchemaFor[ValueClass, Ref, Int]

  implicit def intValueClassEncoder[ValueClass, Ref](
      implicit generic: Lazy[Generic.Aux[ValueClass, Ref]],
      evidence: Ref <:< (Int :: HNil),
      encoder: Encoder[Int]): Encoder[ValueClass] = valueClassEncoder[ValueClass, Ref, Int]

  implicit def intValueClassDecoder[ValueClass, Ref](implicit g: Lazy[Generic.Aux[ValueClass, Ref]],
                                                     evidence: (Int :: HNil) =:= Ref,
                                                     decoder: Decoder[Int]): Decoder[ValueClass] =
    valueClassDecoder[ValueClass, Ref, Int]

  implicit def longValueClassSchemaFor[ValueClass, Ref](
      implicit generic: Lazy[Generic.Aux[ValueClass, Ref]],
      evidence: Ref <:< (Long :: HNil),
      schemaFor: SchemaFor[Long]): SchemaFor[ValueClass] =
    valueClassSchemaFor[ValueClass, Ref, Long]

  implicit def longValueClassEncoder[ValueClass, Ref](
      implicit generic: Lazy[Generic.Aux[ValueClass, Ref]],
      evidence: Ref <:< (Long :: HNil),
      encoder: Encoder[Long]): Encoder[ValueClass] = valueClassEncoder[ValueClass, Ref, Long]

  implicit def longValueClassDecoder[ValueClass, Ref](
      implicit g: Lazy[Generic.Aux[ValueClass, Ref]],
      evidence: (Long :: HNil) =:= Ref,
      decoder: Decoder[Long]): Decoder[ValueClass] =
    valueClassDecoder[ValueClass, Ref, Long]

  implicit def floatValueClassSchemaFor[ValueClass, Ref](
      implicit generic: Lazy[Generic.Aux[ValueClass, Ref]],
      evidence: Ref <:< (Float :: HNil),
      schemaFor: SchemaFor[Float]): SchemaFor[ValueClass] =
    valueClassSchemaFor[ValueClass, Ref, Float]

  implicit def floatValueClassEncoder[ValueClass, Ref](
      implicit generic: Lazy[Generic.Aux[ValueClass, Ref]],
      evidence: Ref <:< (Float :: HNil),
      encoder: Encoder[Float]): Encoder[ValueClass] = valueClassEncoder[ValueClass, Ref, Float]

  implicit def floatValueClassDecoder[ValueClass, Ref](
      implicit g: Lazy[Generic.Aux[ValueClass, Ref]],
      evidence: (Float :: HNil) =:= Ref,
      decoder: Decoder[Float]): Decoder[ValueClass] =
    valueClassDecoder[ValueClass, Ref, Float]

  implicit def doubleValueClassSchemaFor[ValueClass, Ref](
      implicit generic: Lazy[Generic.Aux[ValueClass, Ref]],
      evidence: Ref <:< (Double :: HNil),
      schemaFor: SchemaFor[Double]): SchemaFor[ValueClass] =
    valueClassSchemaFor[ValueClass, Ref, Double]

  implicit def doubleValueClassEncoder[ValueClass, Ref](
      implicit generic: Lazy[Generic.Aux[ValueClass, Ref]],
      evidence: Ref <:< (Double :: HNil),
      encoder: Encoder[Double]): Encoder[ValueClass] = valueClassEncoder[ValueClass, Ref, Double]

  implicit def doubleValueClassDecoder[ValueClass, Ref](
      implicit g: Lazy[Generic.Aux[ValueClass, Ref]],
      evidence: (Double :: HNil) =:= Ref,
      decoder: Decoder[Double]): Decoder[ValueClass] =
    valueClassDecoder[ValueClass, Ref, Double]

  implicit def stringValueClassSchemaFor[ValueClass, Ref](
      implicit generic: Lazy[Generic.Aux[ValueClass, Ref]],
      evidence: Ref <:< (String :: HNil),
      schemaFor: SchemaFor[String]): SchemaFor[ValueClass] =
    valueClassSchemaFor[ValueClass, Ref, String]

  implicit def stringValueClassEncoder[ValueClass, Ref](
      implicit generic: Lazy[Generic.Aux[ValueClass, Ref]],
      evidence: Ref <:< (String :: HNil),
      encoder: Encoder[String]): Encoder[ValueClass] = valueClassEncoder[ValueClass, Ref, String]

  implicit def stringValueClassDecoder[ValueClass, Ref](
      implicit g: Lazy[Generic.Aux[ValueClass, Ref]],
      evidence: (String :: HNil) =:= Ref,
      decoder: Decoder[String]): Decoder[ValueClass] =
    valueClassDecoder[ValueClass, Ref, String]

  implicit def bigDecimalValueClassSchemaFor[ValueClass, Ref](
      implicit generic: Lazy[Generic.Aux[ValueClass, Ref]],
      evidence: Ref <:< (BigDecimal :: HNil),
      schemaFor: SchemaFor[BigDecimal]): SchemaFor[ValueClass] =
    valueClassSchemaFor[ValueClass, Ref, BigDecimal]

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

  implicit val ByteVectorSchemaFor: SchemaFor[ByteVector] =
    SchemaFor.const(Schema.create(Schema.Type.BYTES))
  implicit val ByteVectorEncoder: Encoder[ByteVector] =
    Encoder.ByteVectorEncoder.comap(_.toArray.toVector)
  implicit val ByteVectorDecoder: Decoder[ByteVector] = Decoder.ByteVectorDecoder.map(ByteVector(_))

  implicit def byteVectorValueClassSchemaFor[ValueClass, Ref](
      implicit generic: Lazy[Generic.Aux[ValueClass, Ref]],
      evidence: Ref <:< (ByteVector :: HNil)): SchemaFor[ValueClass] =
    valueClassSchemaFor[ValueClass, Ref, ByteVector]

  implicit def byteVectorValueClassEncoder[ValueClass, Ref](
      implicit generic: Lazy[Generic.Aux[ValueClass, Ref]],
      evidence: Ref <:< (ByteVector :: HNil)): Encoder[ValueClass] =
    valueClassEncoder[ValueClass, Ref, ByteVector]

  implicit def byteVectorValueClassDecoder[ValueClass, Ref](
      implicit g: Lazy[Generic.Aux[ValueClass, Ref]],
      evidence: (ByteVector :: HNil) =:= Ref): Decoder[ValueClass] =
    valueClassDecoder[ValueClass, Ref, ByteVector]

  implicit val DurationSchemaFor: SchemaFor[Duration] =
    SchemaFor.const(Schema.create(Schema.Type.STRING))
  implicit val DurationEncoder: Encoder[Duration] = Encoder.StringEncoder.comap(_.toString)
  implicit val DurationDecoder: Decoder[Duration] = Decoder.StringDecoder.map(Duration.parse)

  implicit val UriSchemaFor: SchemaFor[Uri] = SchemaFor.const(Schema.create(Schema.Type.STRING))
  implicit val UriEncoder: Encoder[Uri] = Encoder.StringEncoder.comap(_.toString)
  implicit val UriDecoder: Decoder[Uri] = Decoder.StringDecoder.map(Uri(_))

  implicit def mapSchemaFor[Key, Value](
      implicit valueSchemaFor: SchemaFor[Value]): SchemaFor[Map[Key, Value]] =
    SchemaFor.const(Schema.createMap(valueSchemaFor.schema))

  implicit def mapEncoder[Key, Value](implicit keyEncoder: KeyEncoder[Key],
                                      valueEncoder: Encoder[Value]): Encoder[Map[Key, Value]] =
    Encoder
      .mapEncoder[Value]
      .comap(_.map { case (key, value) => (keyEncoder.apply(key), value) })

  implicit def mapDecoder[Key, Value](implicit keyDecoder: KeyDecoder[Key],
                                      valueDecoder: Decoder[Value]): Decoder[Map[Key, Value]] =
    Decoder
      .mapDecoder[Value]
      .map(_.map { case (key, value) => (keyDecoder(key).get, value) })

}
