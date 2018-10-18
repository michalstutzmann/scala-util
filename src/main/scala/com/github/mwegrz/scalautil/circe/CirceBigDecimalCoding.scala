package com.github.mwegrz.scalautil.circe

import io.circe.{ Decoder, Encoder }

trait CirceBigDecimalCoding[A] {
  def apply(int: BigDecimal): A

  def unapply(a: A): Option[BigDecimal]

  implicit val circeEncoder: Encoder[A] = Encoder.encodeBigDecimal.contramap(unapply(_).get)
  implicit val circeDecoder: Decoder[A] = Decoder.decodeBigDecimal.map(apply)
}
