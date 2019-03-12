package com.github.mwegrz.scalautil.avro4s

import java.time.Duration

import akka.http.scaladsl.model.Uri
import com.sksamuel.avro4s._
import io.circe.{ KeyDecoder, KeyEncoder }
import org.apache.avro.Schema
import pl.iterators.kebs.macros.CaseClass1Rep
import scodec.bits.{ BitVector, ByteVector }

object coding {
  implicit val ByteVectorSchemaFor: SchemaFor[ByteVector] =
    SchemaFor.const(Schema.create(Schema.Type.BYTES))
  implicit val ByteVectorEncoder: Encoder[ByteVector] =
    Encoder.ByteVectorEncoder.comap(_.toArray.toVector)
  implicit val ByteVectorDecoder: Decoder[ByteVector] = Decoder.ByteVectorDecoder.map(ByteVector(_))

  implicit val BitVectorSchemaFor: SchemaFor[BitVector] =
    SchemaFor.const(Schema.create(Schema.Type.STRING))
  implicit val BitVectorEncoder: Encoder[BitVector] =
    Encoder.StringEncoder.comap(_.toBin)
  implicit val BitVectorDecoder: Decoder[BitVector] =
    Decoder.StringDecoder.map(value => BitVector.fromBin(value).get)

  implicit val DurationSchemaFor: SchemaFor[Duration] =
    SchemaFor.const(Schema.create(Schema.Type.STRING))
  implicit val DurationEncoder: Encoder[Duration] = Encoder.StringEncoder.comap(_.toString)
  implicit val DurationDecoder: Decoder[Duration] = Decoder.StringDecoder.map(Duration.parse)

  implicit val UriSchemaFor: SchemaFor[Uri] = SchemaFor.const(Schema.create(Schema.Type.STRING))
  implicit val UriEncoder: Encoder[Uri] = Encoder.StringEncoder.comap(_.toString)
  implicit val UriDecoder: Decoder[Uri] = Decoder.StringDecoder.map(Uri(_))

  implicit def valueClassSchemaFor[CC <: AnyVal, A](implicit rep: CaseClass1Rep[CC, A],
                                                    subschema: SchemaFor[A]): SchemaFor[CC] =
    SchemaFor.const(subschema.schema)

  implicit def valueClassEncoder[CC <: AnyVal, A](implicit rep: CaseClass1Rep[CC, A],
                                                  delegate: Encoder[A]): Encoder[CC] =
    delegate.comap(rep.unapply)

  implicit def valueClassFromValue[CC <: AnyVal, B](implicit rep: CaseClass1Rep[CC, B],
                                                    delegate: Decoder[B]): Decoder[CC] =
    delegate.map { rep.apply }

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
