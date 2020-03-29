package com.github.mwegrz.scalautil.avro4s

import com.sksamuel.avro4s.{ Decoder, Encoder, SchemaFor }
import org.apache.avro.Schema

trait Avro4sIntCoding[A] {
  def apply(int: Int): A

  def unapply(a: A): Option[Int]

  implicit val avro4sSchemaFor: SchemaFor[A] = SchemaFor.IntSchemaFor.map[A](identity)
  implicit val avro4sEncoder: Encoder[A] = Encoder.IntEncoder.comap(unapply(_).get)
  implicit val avro4sDecoder: Decoder[A] = Decoder.IntDecoder.map(apply)
}
