package com.github.mwegrz.scalautil.avro4s

import com.sksamuel.avro4s.{ Decoder, Encoder, SchemaFor }
import org.apache.avro.Schema

trait Avro4sStringCoding[A] {
  def apply(string: String): A

  def unapply(a: A): Option[String]

  implicit val avro4sSchemaFor: SchemaFor[A] = SchemaFor.const(Schema.create(Schema.Type.STRING))
  implicit val avro4sEncoder: Encoder[A] = Encoder.StringEncoder.comap(unapply(_).get)
  implicit val avro4sDecoder: Decoder[A] = Decoder.StringDecoder.map(apply)
}
