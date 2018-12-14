package com.github.mwegrz.scalautil.akka.http.server.directives.routes

final case class Resource[Value](`type`: String, id: Option[String], attributes: Value)
