package com.github.mwegrz.scalautil.avro4s

import java.time.Duration

import akka.http.scaladsl.model.Uri
import com.sksamuel.avro4s._
import io.circe.{ KeyDecoder, KeyEncoder }
import org.apache.avro.Schema
import scodec.bits.ByteVector
import shapeless.{ ::, Generic, HNil, Lazy }

object coding {
  implicit def anyValValueClassSchemaFor[ValueClass <: AnyVal, Ref, Value](
      implicit generic: Lazy[Generic.Aux[ValueClass, Ref]],
      evidence: Ref <:< (Value :: HNil),
      schemaFor: SchemaFor[Value]): SchemaFor[ValueClass] =
    SchemaFor.const(schemaFor.schema)

  implicit def anyValValueClassEncoder[ValueClass <: AnyVal, Ref, Value](
      implicit generic: Lazy[Generic.Aux[ValueClass, Ref]],
      evidence: Ref <:< (Value :: HNil),
      encoder: Encoder[Value]): Encoder[ValueClass] =
    encoder.comap(generic.value.to(_).head)

  implicit def anyValValueClassDecoder[ValueClass <: AnyVal, Ref, Value](
      implicit generic: Lazy[Generic.Aux[ValueClass, Ref]],
      evidence: (Value :: HNil) =:= Ref,
      decoder: Decoder[Value]): Decoder[ValueClass] =
    decoder.map { value =>
      generic.value.from(value :: HNil)
    }

  /*implicit def anyValValueClassSchemaFor[ValueClass, Ref, Value <: AnyVal](
      implicit generic: Lazy[Generic.Aux[ValueClass, Ref]],
      evidence: Ref <:< (Value :: HNil),
      schemaFor: SchemaFor[Value]): SchemaFor[ValueClass] =
    SchemaFor.const(schemaFor.schema)

  implicit def anyValValueClassEncoder[ValueClass, Ref, Value <: AnyVal](
      implicit generic: Lazy[Generic.Aux[ValueClass, Ref]],
      evidence: Ref <:< (Value :: HNil),
      encoder: Encoder[Value]): Encoder[ValueClass] =
    encoder.comap(generic.value.to(_).head)

  implicit def anyValValueClassDecoder[ValueClass, Ref, Value <: AnyVal](
      implicit generic: Lazy[Generic.Aux[ValueClass, Ref]],
      evidence: (Value :: HNil) =:= Ref,
      decoder: Decoder[Value]): Decoder[ValueClass] =
    decoder.map { value =>
      generic.value.from(value :: HNil)
    }

  implicit def stringValueClassSchemaFor[ValueClass, Ref](
      implicit generic: Lazy[Generic.Aux[ValueClass, Ref]],
      evidence: Ref <:< (String :: HNil),
      schemaFor: SchemaFor[String]): SchemaFor[ValueClass] = SchemaFor.const(schemaFor.schema)

  implicit def stringValueClassEncoder[ValueClass, Ref](
      implicit generic: Lazy[Generic.Aux[ValueClass, Ref]],
      evidence: Ref <:< (String :: HNil),
      encoder: Encoder[String]): Encoder[ValueClass] = encoder.comap(generic.value.to(_).head)

  implicit def stringValueClassDecoder[ValueClass, Ref](
      implicit generic: Lazy[Generic.Aux[ValueClass, Ref]],
      evidence: (String :: HNil) =:= Ref,
      decoder: Decoder[String]): Decoder[ValueClass] = decoder.map { value =>
    generic.value.from(value :: HNil)
  }

  implicit def bigDecimalValueClassSchemaFor[ValueClass, Ref](
      implicit generic: Lazy[Generic.Aux[ValueClass, Ref]],
      evidence: Ref <:< (BigDecimal :: HNil),
      schemaFor: SchemaFor[BigDecimal]): SchemaFor[ValueClass] = SchemaFor.const(schemaFor.schema)

  implicit def bigDecimalValueClassEncoder[ValueClass, Ref](
      implicit generic: Lazy[Generic.Aux[ValueClass, Ref]],
      evidence: Ref <:< (BigDecimal :: HNil),
      encoder: Encoder[BigDecimal]): Encoder[ValueClass] = encoder.comap(generic.value.to(_).head)

  implicit def bigDecimalValueClassDecoder[ValueClass, Ref](
      implicit generic: Lazy[Generic.Aux[ValueClass, Ref]],
      evidence: (BigDecimal :: HNil) =:= Ref,
      decoder: Decoder[BigDecimal]): Decoder[ValueClass] = decoder.map { value =>
    generic.value.from(value :: HNil)
  }*/

  implicit val ByteVectorSchemaFor: SchemaFor[ByteVector] =
    SchemaFor.const(Schema.create(Schema.Type.BYTES))
  implicit val ByteVectorEncoder: Encoder[ByteVector] =
    Encoder.ByteVectorEncoder.comap(_.toArray.toVector)
  implicit val ByteVectorDecoder: Decoder[ByteVector] = Decoder.ByteVectorDecoder.map(ByteVector(_))

  /*implicit def byteVectorValueClassSchemaFor[ValueClass, Ref](
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
    valueClassDecoder[ValueClass, Ref, ByteVector]*/

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
