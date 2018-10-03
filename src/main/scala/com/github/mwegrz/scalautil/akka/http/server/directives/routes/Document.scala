package com.github.mwegrz.scalautil.akka.http.server.directives.routes

import akka.http.scaladsl.model.Uri

object Document {
  final case class Resource[Value](`type`: String, id: String, attributes: Value)
  final case class Links(next: Uri)
}

sealed trait Document

final case class SingleDocument[Value](data: Document.Resource[Value]) extends Document

final case class MultiDocument[Value](data: List[Document.Resource[Value]]) extends Document
