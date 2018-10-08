package com.github.mwegrz.scalautil.circe

import io.circe.{ Decoder, Encoder }

trait CirceIntCoding[A] {
  def apply(int: Int): A

  def unapply(a: A): Option[Int]

  implicit val circeEncoder: Encoder[A] = Encoder.encodeInt.contramap(unapply(_).get)
  implicit val circeDecoder: Decoder[A] = Decoder.decodeInt.map(apply)
}
