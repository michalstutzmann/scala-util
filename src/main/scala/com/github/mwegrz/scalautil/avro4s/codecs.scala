package com.github.mwegrz.scalautil.avro4s

import java.time.{ Duration, ZoneId }

import akka.http.scaladsl.model.Uri
import com.sksamuel.avro4s._
import io.circe.{ KeyDecoder, KeyEncoder }
import org.apache.avro.Schema
import scodec.bits.{ BitVector, ByteVector }
import com.github.mwegrz.scalautil.{ CaseClass1Rep, circe }
import shapeless.{ ::, Generic, HNil, Lazy }

object codecs {
  implicit val ByteVectorSchemaFor: SchemaFor[ByteVector] =
    SchemaFor.ByteArraySchemaFor.map(identity)
  implicit val ByteVectorEncoder: Encoder[ByteVector] =
    Encoder.ByteVectorEncoder.comap(_.toArray.toVector)
  implicit val ByteVectorDecoder: Decoder[ByteVector] = Decoder.ByteVectorDecoder.map(ByteVector(_))

  implicit val BitVectorSchemaFor: SchemaFor[BitVector] =
    SchemaFor.StringSchemaFor.map(identity)
  implicit val BitVectorEncoder: Encoder[BitVector] =
    Encoder.StringEncoder.comap(_.toBin)
  implicit val BitVectorDecoder: Decoder[BitVector] =
    Decoder.StringDecoder.map(value => BitVector.fromBin(value).get)

  implicit val DurationSchemaFor: SchemaFor[Duration] =
    SchemaFor.StringSchemaFor.map(identity)
  implicit val DurationEncoder: Encoder[Duration] = Encoder.StringEncoder.comap(_.toString)
  implicit val DurationDecoder: Decoder[Duration] = Decoder.StringDecoder.map(Duration.parse)

  implicit val UriSchemaFor: SchemaFor[Uri] = SchemaFor.StringSchemaFor.map(identity)
  implicit val UriEncoder: Encoder[Uri] = Encoder.StringEncoder.comap(_.toString)
  implicit val UriDecoder: Decoder[Uri] = Decoder.StringDecoder.map(Uri(_))

  implicit val ZoneIdSchemaFor: SchemaFor[ZoneId] = SchemaFor.StringSchemaFor.map(identity)
  implicit val ZoneIdEncoder: Encoder[ZoneId] = Encoder.StringEncoder.comap(_.getId)
  implicit val ZoneIdDecoder: Decoder[ZoneId] = Decoder.StringDecoder.map(ZoneId.of)

  implicit def indexedSeqSchemaFor[A: SchemaFor]: SchemaFor[IndexedSeq[A]] =
    SchemaFor.seqSchemaFor[A].map(identity)
  implicit def indexedSeqEncoder[A: Encoder]: Encoder[IndexedSeq[A]] = Encoder.seqEncoder[A].comap(_.toIndexedSeq)
  implicit def indexedSeqDecoder[A: Decoder]: Decoder[IndexedSeq[A]] = Decoder.seqDecoder[A].map(_.toIndexedSeq)

  implicit def valueClassSchemaFor[CC <: AnyVal, A](
      implicit rep: CaseClass1Rep[CC, A],
      subschema: SchemaFor[A],
      fieldMapper: FieldMapper = DefaultFieldMapper
  ): SchemaFor[CC] =
    SchemaFor.const(subschema.schema(fieldMapper))

  implicit def valueClassEncoder[CC <: AnyVal, A](
      implicit rep: CaseClass1Rep[CC, A],
      delegate: Encoder[A]
  ): Encoder[CC] =
    delegate.comap(rep.unapply)

  implicit def valueClassFromValue[CC <: AnyVal, B](
      implicit rep: CaseClass1Rep[CC, B],
      delegate: Decoder[B]
  ): Decoder[CC] =
    delegate.map { rep.apply }

  implicit def mapSchemaFor[Key, Value](
      implicit valueSchemaFor: SchemaFor[Value],
      fieldMapper: FieldMapper = DefaultFieldMapper
  ): SchemaFor[Map[Key, Value]] =
    SchemaFor.const(Schema.createMap(valueSchemaFor.schema(fieldMapper)))

  implicit def mapEncoder[Key, Value](
      implicit keyEncoder: KeyEncoder[Key],
      valueEncoder: Encoder[Value]
  ): Encoder[Map[Key, Value]] =
    Encoder
      .mapEncoder[Value]
      .comap(_.map { case (key, value) => (keyEncoder.apply(key), value) })

  implicit def mapDecoder[Key, Value](
      implicit keyDecoder: KeyDecoder[Key],
      valueDecoder: Decoder[Value]
  ): Decoder[Map[Key, Value]] =
    Decoder
      .mapDecoder[Value]
      .map(_.map { case (key, value) => (keyDecoder(key).get, value) })

  implicit def stringValueClassKeyEncoder[Key, Ref](
      implicit generic: Lazy[Generic.Aux[Key, Ref]],
      evidence: Ref <:< (String :: HNil)
  ): KeyEncoder[Key] = circe.codecs.stringValueClassKeyEncoder

  implicit def stringValueClassKeyDecoder[Key, Ref](
      implicit generic: Lazy[Generic.Aux[Key, Ref]],
      evidence: (String :: HNil) =:= Ref
  ): KeyDecoder[Key] = circe.codecs.stringValueClassKeyDecoder
}
