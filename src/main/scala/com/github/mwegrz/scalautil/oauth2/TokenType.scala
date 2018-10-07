package com.github.mwegrz.scalautil.oauth2
import io.circe.{ Decoder, Encoder }

object TokenType {
  case object Bearer extends TokenType {
    override lazy val value = "bearer"
  }

  def apply(value: String): TokenType = value match {
    case Bearer.value => Bearer
  }

  implicit val circeEncoder: Encoder[TokenType] = Encoder.encodeString.contramap(_.toString)
  implicit val circeDecoder: Decoder[TokenType] = Decoder.decodeString.map(apply)
}

trait TokenType {
  def value: String

  override def toString: String = value
}
