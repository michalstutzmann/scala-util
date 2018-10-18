package com.github.mwegrz.scalautil.avro4s

import com.sksamuel.avro4s.{ Decoder, Encoder, SchemaFor }
import org.apache.avro.Schema

trait Avro4sBigDecimalCoding[A] {
  def apply(bigDecimal: BigDecimal): A

  def unapply(a: A): Option[BigDecimal]

  implicit val avro4sSchemaFor: SchemaFor[A] = SchemaFor.const(Schema.create(Schema.Type.STRING))
  implicit val avro4sEncoder: Encoder[A] = Encoder.BigDecimalEncoder.comap(unapply(_).get)
  implicit val avro4sDecoder: Decoder[A] = Decoder.bigDecimalDecoder.map(apply)
}
