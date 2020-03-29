package com.github.mwegrz.scalautil.avro4s

import com.sksamuel.avro4s.{ Decoder, Encoder, SchemaFor }
import org.apache.avro.Schema

trait Avro4sBigDecimalCoding[A] {
  def apply(bigDecimal: BigDecimal): A

  def unapply(a: A): Option[BigDecimal]

  implicit val avro4sSchemaFor: SchemaFor[A] = SchemaFor.StringSchemaFor.map[A](identity)
  implicit val avro4sEncoder: Encoder[A] = Encoder.bigDecimalEncoder.comap(unapply(_).get)
  implicit val avro4sDecoder: Decoder[A] = Decoder.BigDecimalDecoder.map(apply)
}
