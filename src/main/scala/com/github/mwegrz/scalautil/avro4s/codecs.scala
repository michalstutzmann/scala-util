package com.github.mwegrz.scalautil.avro4s

import java.time.Duration
import java.util.Base64

import akka.http.scaladsl.model.Uri
import com.sksamuel.avro4s._
import org.apache.avro.Schema
import scodec.bits.ByteVector
import shapeless.{ ::, Generic, HNil, Lazy }

object codecs {
  implicit def caseClassWrapperSchemaFor[Wrapper, Ref, Value](
      implicit generic: Lazy[Generic.Aux[Wrapper, Ref]],
      evidence: Ref <:< (Value :: HNil),
      schemaFor: SchemaFor[Value]): SchemaFor[Wrapper] =
    SchemaFor.const(schemaFor.schema)

  implicit def caseClassWrapperEncoder[Wrapper, Ref, Value](
      implicit generic: Lazy[Generic.Aux[Wrapper, Ref]],
      evidence: Ref <:< (Value :: HNil),
      encoder: Encoder[Value]): Encoder[Wrapper] =
    encoder.comap(generic.value.to(_).head)

  implicit def caseClassWrapperDecoder[Wrapper, Ref, Value](
      implicit generic: Lazy[Generic.Aux[Wrapper, Ref]],
      evidence: (Value :: HNil) =:= Ref,
      decoder: Decoder[Value]): Decoder[Wrapper] =
    decoder.map { value =>
      generic.value.from(value :: HNil)
    }

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

  implicit def typedKeyMapSchemaFor[KeyWrapper, Ref, Value](
      implicit generic: Lazy[Generic.Aux[KeyWrapper, Ref]],
      evidence: Ref <:< (String :: HNil),
      valueSchemaFor: SchemaFor[Value]): SchemaFor[Map[KeyWrapper, Value]] =
    SchemaFor.const(Schema.createMap(valueSchemaFor.schema))

  implicit def typedKeyMapEncoder[KeyWrapper, Ref, Value](
      implicit generic: Lazy[Generic.Aux[KeyWrapper, Ref]],
      evidence: Ref <:< (String :: HNil),
      valueEncoder: Encoder[Value]): Encoder[Map[KeyWrapper, Value]] =
    Encoder
      .mapEncoder[Value]
      .comap(_.map { case (key, value) => (generic.value.to(key).head, value) })

  implicit def typedKeyMapDecoder[KeyWrapper, Ref, Value](
      implicit generic: Lazy[Generic.Aux[KeyWrapper, Ref]],
      evidence: (String :: HNil) =:= Ref,
      valueDecoder: Decoder[Value]): Decoder[Map[KeyWrapper, Value]] =
    Decoder
      .mapDecoder[Value]
      .map(_.map { case (key, value) => (generic.value.from(key :: HNil), value) })

  implicit def byteArrayKeyMapSchemaFor[V](
      implicit valueSchemaFor: SchemaFor[V]): SchemaFor[Map[Array[Byte], V]] =
    SchemaFor.const(Schema.createMap(valueSchemaFor.schema))

  implicit def byteArrayKeyMapEncoder[V](
      implicit valueEncoder: Encoder[V]): Encoder[Map[Array[Byte], V]] =
    Encoder
      .mapEncoder[V]
      .comap(_.map { case (key, value) => (Base64.getEncoder.encodeToString(key), value) })

  implicit def byteArrayKeyMapDecoder[V](
      implicit valueDecoder: Decoder[V]): Decoder[Map[Array[Byte], V]] =
    Decoder
      .mapDecoder[V]
      .map(_.map { case (key, value) => (Base64.getDecoder.decode(key), value) })
}
