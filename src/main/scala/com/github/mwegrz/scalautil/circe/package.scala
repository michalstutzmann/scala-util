package com.github.mwegrz.scalautil

import io.circe.{ Json, Printer }

package object circe {
  implicit class JsonOps(underlying: Json) {
    val noSpacesAndNoNulls: String = Printer(
      preserveOrder = true,
      dropNullValues = true,
      indent = ""
    ).pretty(underlying)
  }
}
