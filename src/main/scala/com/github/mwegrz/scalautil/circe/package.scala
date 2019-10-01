package com.github.mwegrz.scalautil

import io.circe.{ Json, Printer }

package object circe {
  implicit class JsonOps(underlying: Json) {
    val noSpacesAndNoNulls: String = Printer(
      dropNullValues = true,
      indent = ""
    ).print(underlying)
  }
}
