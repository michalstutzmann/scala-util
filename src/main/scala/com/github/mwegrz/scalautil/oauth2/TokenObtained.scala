package com.github.mwegrz.scalautil.oauth2

final case class TokenObtained(
    accessToken: String,
    tokenType: TokenType,
    expiresIn: Long,
    refreshToken: Option[String]
)
