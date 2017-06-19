package com.github.mwegrz.scalautil.spray

import spray.json.JsValue

package object json {
  def jsValueToString(value: JsValue) = value.toString().stripPrefix("\"").stripSuffix("\"")
}
