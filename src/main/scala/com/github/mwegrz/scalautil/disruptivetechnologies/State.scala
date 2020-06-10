package com.github.mwegrz.scalautil.disruptivetechnologies

import io.circe.Decoder
import io.circe.generic.extras.semiauto.deriveEnumerationDecoder

object State {
  case object PRESENT extends State
  case object NOT_PRESENT extends State

  implicit val decoder: Decoder[State] = deriveEnumerationDecoder[State]
}

sealed trait State
