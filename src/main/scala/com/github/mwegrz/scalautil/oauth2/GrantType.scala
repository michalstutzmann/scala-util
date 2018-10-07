package com.github.mwegrz.scalautil.oauth2

object GrantType {
  case object AuthorizationCode extends GrantType {
    override lazy val value = "authorization_code"
  }

  case object ClientCredentials extends GrantType {
    override lazy val value = "client_credentials"
  }

  case object RefreshToken extends GrantType {
    override lazy val value = "refresh_token"
  }

  def apply(value: String): GrantType = value match {
    case ClientCredentials.value => ClientCredentials
    case RefreshToken.value      => RefreshToken
  }
}

trait GrantType {
  def value: String

  override def toString: String = value
}
