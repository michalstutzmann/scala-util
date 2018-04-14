package com.github.mwegrz.scalautil

import io.circe.Printer

package object circe {
  implicit val jsonPrinter: Printer = Printer(
    preserveOrder = true,
    dropNullValues = true,
    indent = ""
  )
}
