package com.github.mwegrz.scalautil.akka.http.circe

import akka.http.scaladsl.model.MediaType
import akka.http.scaladsl.model.MediaTypes.{ `application/json`, `application/vnd.api+json` }
import de.heikoseeberger.akkahttpcirce.ErrorAccumulatingCirceSupport
import scala.collection.immutable.Seq

object JsonApiErrorAccumulatingCirceSupport extends JsonApiErrorAccumulatingCirceSupport

trait JsonApiErrorAccumulatingCirceSupport extends ErrorAccumulatingCirceSupport {
  override def mediaTypes: Seq[MediaType.WithFixedCharset] = List(`application/json`, `application/vnd.api+json`)
}
