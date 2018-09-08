package com.github.mwegrz.scalautil.akka.http.server.directives.routes

final case class Envelope[Value](data: List[Value], nextCursor: Option[String])
