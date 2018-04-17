package com.github.mwegrz.scalautil.akka.http.server.directives.routes

import java.time.Instant

import akka.NotUsed
import akka.http.scaladsl.marshalling.{ ToEntityMarshaller, ToResponseMarshaller }
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import akka.http.scaladsl.unmarshalling.{ FromEntityUnmarshaller, Unmarshaller }
import akka.stream.Materializer
import akka.stream.scaladsl.{ Sink, Source }
import com.github.mwegrz.app.Shutdownable
import com.github.mwegrz.scalautil.store.TimeSeriesStore

import scala.concurrent.ExecutionContext

class TimeSeriesStoreSink[Key, Value](valueStore: TimeSeriesStore[Key, Value], valueSink: Sink[Value, NotUsed])(
    implicit
    instantFromStringUnmarshaller: Unmarshaller[String, Instant],
    valueToEntityMarshaller: ToEntityMarshaller[Value],
    valueSourceToResponseMarshaller: ToResponseMarshaller[Source[Value, NotUsed]],
    fromEntityToValueUnmarshaller: FromEntityUnmarshaller[Value],
    executionContext: ExecutionContext,
    materializer: Materializer)
    extends Shutdownable {
  def route(keys: Set[Key]): Route = {
    post {
      entity(as[Value]) { value =>
        keys foreach { key =>
          Source.single((key, Instant.now(), value)).runWith(valueStore.store)
          Source.single(value).runWith(valueSink)
        }
        complete(StatusCodes.Created)
      }
    } ~
      get {
        parameters('from_time.as[Instant], 'to_time.as[Instant]) { (fromTime, toTime) =>
          complete(valueStore.retrieveRange(keys, fromTime, toTime).map { case (_, value) => value })
        }
      }
  }

  override def shutdown(): Unit = ???
}
