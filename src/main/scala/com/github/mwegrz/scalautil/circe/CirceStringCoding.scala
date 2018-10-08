package com.github.mwegrz.scalautil.circe

import io.circe.{ Decoder, Encoder }

trait CirceStringCoding[A] {
  def apply(string: String): A

  def unapply(a: A): Option[String]

  implicit val circeEncoder: Encoder[A] = Encoder.encodeString.contramap(unapply(_).get)
  implicit val circeDecoder: Decoder[A] = Decoder.decodeString.map(apply)
}
