package com.github.mwegrz.scalautil.oauth2

object ResponseType {
  case object Token extends ResponseType {
    override lazy val value = "token"
  }

  case object Code extends ResponseType {
    override lazy val value = "code"
  }

  def apply(value: String): ResponseType = value match {
    case Token.value => Token
    case Code.value  => Code
  }
}

trait ResponseType {
  def value: String

  override def toString: String = value
}
