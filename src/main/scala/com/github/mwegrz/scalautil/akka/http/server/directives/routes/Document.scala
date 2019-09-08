package com.github.mwegrz.scalautil.akka.http.server.directives.routes

sealed trait Document

final case class ErrorDocument(errors: Seq[Error]) extends Document

final case class SingleDocument[Value](data: Option[Resource[Value]]) extends Document

final case class MultiDocument[Value](data: List[Resource[Value]]) extends Document
