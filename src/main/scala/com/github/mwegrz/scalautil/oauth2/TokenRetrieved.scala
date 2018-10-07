package com.github.mwegrz.scalautil.oauth2

final case class TokenRetrieved(accessToken: String,
                                tokenType: TokenType,
                                expiresIn: Long,
                                refreshToken: Option[String])
