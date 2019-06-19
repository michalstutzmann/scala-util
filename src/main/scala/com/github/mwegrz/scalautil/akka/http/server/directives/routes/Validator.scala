package com.github.mwegrz.scalautil.akka.http.server.directives.routes

import org.scalactic.Or

trait Validator[Value] {
  def validate(value: Value): Or[Value, String]
}
