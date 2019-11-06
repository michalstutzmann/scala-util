package com.github.mwegrz.scalautil.avro4s

import java.time.{ Duration, ZoneId }

import akka.http.scaladsl.model.Uri
import com.sksamuel.avro4s._
import io.circe.{ KeyDecoder, KeyEncoder }
import org.apache.avro.Schema
import scodec.bits.{ BitVector, ByteVector }
import com.github.mwegrz.scalautil.{ CaseClass1Rep, circe }
import javax.mail.internet.InternetAddress
import shapeless.{ ::, Generic, HNil, Lazy }

import scala.collection.immutable.SortedMap

object codecs {
  implicit val Avro4sByteVectorSchemaFor: SchemaFor[ByteVector] =
    SchemaFor.ByteArraySchemaFor.map(identity)
  implicit val Avro4sByteVectorEncoder: Encoder[ByteVector] =
    Encoder.ByteVectorEncoder.comap(_.toArray.toVector)
  implicit val Avro4sByteVectorDecoder: Decoder[ByteVector] = Decoder.ByteVectorDecoder.map(ByteVector(_))

  implicit val Avro4sBitVectorSchemaFor: SchemaFor[BitVector] =
    SchemaFor.StringSchemaFor.map(identity)
  implicit val Avro4sBitVectorEncoder: Encoder[BitVector] =
    Encoder.StringEncoder.comap(_.toBin)
  implicit val Avro4sBitVectorDecoder: Decoder[BitVector] =
    Decoder.StringDecoder.map(value => BitVector.fromBin(value).get)

  implicit val Avro4sDurationSchemaFor: SchemaFor[Duration] =
    SchemaFor.StringSchemaFor.map(identity)
  implicit val Avro4sDurationEncoder: Encoder[Duration] = Encoder.StringEncoder.comap(_.toString)
  implicit val Avro4sDurationDecoder: Decoder[Duration] = Decoder.StringDecoder.map(Duration.parse)

  implicit val Avro4sUriSchemaFor: SchemaFor[Uri] = SchemaFor.StringSchemaFor.map(identity)
  implicit val Avro4sUriEncoder: Encoder[Uri] = Encoder.StringEncoder.comap(_.toString)
  implicit val Avro4sUriDecoder: Decoder[Uri] = Decoder.StringDecoder.map(Uri(_))

  implicit val Avro4sInternetAddressSchemaFor: SchemaFor[InternetAddress] = SchemaFor.StringSchemaFor.map(identity)
  implicit val Avro4sInternetAddressEncoder: Encoder[InternetAddress] = Encoder.StringEncoder.comap(_.toString)
  implicit val Avro4sInternetAddressDecoder: Decoder[InternetAddress] =
    Decoder.StringDecoder.map(InternetAddress.parse(_).head)

  implicit val Avro4sZoneIdSchemaFor: SchemaFor[ZoneId] = SchemaFor.StringSchemaFor.map(identity)
  implicit val Avro4sZoneIdEncoder: Encoder[ZoneId] = Encoder.StringEncoder.comap(_.getId)
  implicit val Avro4sZoneIdDecoder: Decoder[ZoneId] = Decoder.StringDecoder.map(ZoneId.of)

  implicit def avro4sIndexedSeqSchemaFor[A: SchemaFor]: SchemaFor[IndexedSeq[A]] =
    SchemaFor.seqSchemaFor[A].map(identity)
  implicit def avro4sIndexedSeqEncoder[A: Encoder]: Encoder[IndexedSeq[A]] = Encoder.seqEncoder[A].comap(_.toIndexedSeq)
  implicit def avro4sIndexedSeqDecoder[A: Decoder]: Decoder[IndexedSeq[A]] = Decoder.seqDecoder[A].map(_.toIndexedSeq)

  implicit def avro4sValueClassSchemaFor[CC <: AnyVal, A](
      implicit rep: CaseClass1Rep[CC, A],
      subschema: SchemaFor[A],
      fieldMapper: FieldMapper = DefaultFieldMapper
  ): SchemaFor[CC] =
    SchemaFor.const(subschema.schema(fieldMapper))

  implicit def avro4sValueClassEncoder[CC <: AnyVal, A](
      implicit rep: CaseClass1Rep[CC, A],
      delegate: Encoder[A]
  ): Encoder[CC] =
    delegate.comap(rep.unapply)

  implicit def avro4sValueClassFromValue[CC <: AnyVal, B](
      implicit rep: CaseClass1Rep[CC, B],
      delegate: Decoder[B]
  ): Decoder[CC] =
    delegate.map { rep.apply }

  implicit def avro4sMapSchemaFor[Key, Value](
      implicit valueSchemaFor: SchemaFor[Value],
      fieldMapper: FieldMapper = DefaultFieldMapper
  ): SchemaFor[Map[Key, Value]] =
    SchemaFor.const(Schema.createMap(valueSchemaFor.schema(fieldMapper)))

  implicit def avro4sMapEncoder[Key, Value](
      implicit keyEncoder: KeyEncoder[Key],
      valueEncoder: Encoder[Value]
  ): Encoder[Map[Key, Value]] =
    Encoder
      .mapEncoder[Value]
      .comap(_.map { case (key, value) => (keyEncoder.apply(key), value) })

  implicit def avro4sMapDecoder[Key, Value](
      implicit keyDecoder: KeyDecoder[Key],
      valueDecoder: Decoder[Value]
  ): Decoder[Map[Key, Value]] =
    Decoder
      .mapDecoder[Value]
      .map(_.map { case (key, value) => (keyDecoder(key).get, value) })

  implicit def avro4sSortedMapSchemaFor[Key, Value](
      implicit valueSchemaFor: SchemaFor[Value],
      fieldMapper: FieldMapper = DefaultFieldMapper
  ): SchemaFor[SortedMap[Key, Value]] =
    SchemaFor.const(Schema.createMap(valueSchemaFor.schema(fieldMapper)))

  implicit def avro4sSortedMapEncoder[Key, Value](
      implicit keyEncoder: KeyEncoder[Key],
      valueEncoder: Encoder[Value],
      ordering: Ordering[Key]
  ): Encoder[SortedMap[Key, Value]] =
    avro4sMapEncoder[Key, Value].comap(_.toMap)

  implicit def avro4sSortedMapDecoder[Key, Value](
      implicit keyDecoder: KeyDecoder[Key],
      valueDecoder: Decoder[Value],
      ordering: Ordering[Key]
  ): Decoder[SortedMap[Key, Value]] =
    avro4sMapDecoder[Key, Value].map(SortedMap.from[Key, Value])

  implicit def avro4sStringValueClassKeyEncoder[Key, Ref](
      implicit generic: Lazy[Generic.Aux[Key, Ref]],
      evidence: Ref <:< (String :: HNil)
  ): KeyEncoder[Key] = circe.codecs.circeStringValueClassKeyEncoder

  implicit def avro4sStringValueClassKeyDecoder[Key, Ref](
      implicit generic: Lazy[Generic.Aux[Key, Ref]],
      evidence: (String :: HNil) =:= Ref
  ): KeyDecoder[Key] = circe.codecs.circeStringValueClassKeyDecoder
}
