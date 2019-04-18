package com.github.mwegrz.scalautil.oauth2

final case class ObtainToken(
    audience: String,
    grantType: String,
    clientId: String,
    clientSecret: String
)
