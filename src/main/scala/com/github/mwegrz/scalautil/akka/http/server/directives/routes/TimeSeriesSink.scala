package com.github.mwegrz.scalautil.akka.http.server.directives.routes

import java.time.Instant

import akka.NotUsed
import akka.http.scaladsl.marshalling.ToEntityMarshaller
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.Directives.{ as, complete, entity, post }
import akka.http.scaladsl.server.Route
import akka.http.scaladsl.unmarshalling.FromEntityUnmarshaller
import akka.stream.Materializer
import akka.stream.scaladsl.{ Sink, Source }
import com.github.mwegrz.scalautil.store.TimeSeriesStore

class TimeSeriesSink[Key, Value](name: String)(
    implicit
    valueStore: TimeSeriesStore[Key, Value],
    valueSink: Sink[(Key, Instant, Value), NotUsed],
    singleDocumentToEntityMarshaller: ToEntityMarshaller[SingleDocument[Value]],
    entityToSingleDocumentUnmarshaller: FromEntityUnmarshaller[SingleDocument[Value]],
    materializer: Materializer,
    validator: Validator[Value]
) {
  def route(keys: Set[Key])(update: Value => Value): Route = post {
    entity(as[SingleDocument[Value]]) {
      case SingleDocument(Some(resource @ Resource(_, _, value))) =>
        validate(value)(validator) {
          val updatedValue = update(value)
          val time = Instant.now()
          keys foreach { key =>
            Source.single((key, Instant.now(), updatedValue)).log(name).runWith(valueStore.addIfNotExists)
            Source.single(updatedValue).runWith(valueSink.contramap((key, time, _)))
          }
          val id = createId(time)
          complete(
            StatusCodes.Created -> SingleDocument(
              Some(resource.copy(`type` = name, id = Some(id), attributes = updatedValue))
            )
          )
        }
    }
  }
}
