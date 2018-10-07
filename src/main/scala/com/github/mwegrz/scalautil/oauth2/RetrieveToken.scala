package com.github.mwegrz.scalautil.oauth2

final case class RetrieveToken(
    audience: String,
    grantType: String,
    clientId: String,
    clientSecret: String
)
