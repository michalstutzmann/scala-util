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
import com.github.mwegrz.scalautil.StringVal
import com.github.mwegrz.scalautil.store.TimeSeriesStore

import scala.concurrent.ExecutionContext

class TimeSeriesStoreSink[Key <: StringVal, Value](name: String,
                                                   valueStore: TimeSeriesStore[Key, Value],
                                                   valueSink: Sink[Value, NotUsed])(
    implicit
    instantFromStringUnmarshaller: Unmarshaller[String, Instant],
    valueSourceToResponseMarshaller: ToResponseMarshaller[Source[Value, NotUsed]],
    fromEntityToValueUnmarshaller: FromEntityUnmarshaller[Value],
    multiDocumentToEntityMarshaller: ToEntityMarshaller[MultiDocument[Value]],
    entityToSingleDocumentUnmarshaller: FromEntityUnmarshaller[SingleDocument[Value]],
    executionContext: ExecutionContext,
    materializer: Materializer)
    extends Shutdownable {
  def route(keys: Set[Key]): Route = {
    post {
      entity(as[SingleDocument[Value]]) {
        case SingleDocument(Document.Resource(_, _, value)) =>
          keys foreach { key =>
            Source.single((key, Instant.now(), value)).runWith(valueStore.store)
            Source.single(value).runWith(valueSink)
          }
          complete(
            StatusCodes.Created
              .copy(intValue = StatusCodes.Created.intValue)(reason = "",
                                                             defaultMessage = "",
                                                             allowsEntity = true))
      }
    } ~
      get {
        parameters(Symbol("filter[from_time]").as[Instant],
                   Symbol("filter[until_time]").as[Instant] ? Instant.now) {
          (fromTime, untilTime) =>
            val response =
              retrieveHistoricalValues(keys, fromTime, untilTime)
                .runFold(List.empty[(Instant, Value)])((a, b) => b :: a)
                .map(_.map { case (key, value) => Document.Resource(name, key.toString, value) })
                .map(data => MultiDocument(data))
            complete(response)
        }
      }
  }

  private def retrieveHistoricalValues(keys: Set[Key],
                                       fromTime: Instant,
                                       untilTime: Instant): Source[(Instant, Value), NotUsed] =
    valueStore
      .retrieveRange(keys, fromTime, untilTime)
      .map { case (_, time, value) => (time, value) }

  override def shutdown(): Unit = ()
}
