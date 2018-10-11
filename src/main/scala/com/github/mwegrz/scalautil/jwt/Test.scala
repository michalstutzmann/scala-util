package com.github.mwegrz.scalautil.jwt

import io.circe.syntax._
import io.circe.generic.auto._
import com.github.mwegrz.scalautil.circe.coding._
import com.github.mwegrz.scalautil.oauth2.JwtKey

object Test extends App {
  println(JwtKey("a").asJson)
}
