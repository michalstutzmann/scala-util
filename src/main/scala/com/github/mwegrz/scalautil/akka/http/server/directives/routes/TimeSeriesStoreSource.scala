package com.github.mwegrz.scalautil.akka.http.server.directives.routes

import java.time.Instant

import akka.NotUsed
import akka.http.scaladsl.marshalling.{ Marshal, ToEntityMarshaller, ToResponseMarshaller }
import akka.http.scaladsl.model.{ MessageEntity, StatusCodes }
import akka.http.scaladsl.model.headers._
import akka.http.scaladsl.model.sse.ServerSentEvent
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import akka.http.scaladsl.unmarshalling.Unmarshaller
import akka.stream.{ KillSwitches, Materializer }
import akka.stream.scaladsl.{ Keep, Source }
import com.github.mwegrz.app.Shutdownable
import com.github.mwegrz.scalautil.store.TimeSeriesStore
import akka.http.scaladsl.marshalling.sse.EventStreamMarshalling._

import scala.concurrent.duration._
import scala.concurrent.ExecutionContext

class TimeSeriesStoreSource[Key, Value](valueStore: TimeSeriesStore[Key, Value],
                                        valueSource: Source[(Key, Instant, Value), NotUsed])(
    implicit
    instantFromStringUnmarshaller: Unmarshaller[String, Instant],
    valueToEntityMarshaller: ToEntityMarshaller[Value],
    valueSourceToResponseMarshaller: ToResponseMarshaller[Source[Value, NotUsed]],
    executionContext: ExecutionContext,
    materializer: Materializer)
    extends Shutdownable {
  private val storing = valueSource
    .viaMat(KillSwitches.single)(Keep.right)
    .toMat(valueStore.store)(Keep.left)
    .run()

  def route(keys: Set[Key]): Route = get {
    parameters('from_time.as[Instant], 'until_time.as[Instant]) { (fromTime, untilTime) =>
      val response = valueStore
        .retrieveRange(keys, fromTime, untilTime)
        .map { case (_, value) => value }
      complete(response)
    } ~ optionalHeaderValueByType[`Last-Event-ID`]() {
      case Some(`Last-Event-ID`(id)) => complete(StatusCodes.NotImplemented)
      case None =>
        val response = valueSource
          .map { case (_, _, value) => ServerSentEvent(Marshal(value).to[MessageEntity].value.get.get.toString) }
          .keepAlive(15.second, () => ServerSentEvent.heartbeat)
        complete(response)
    }
  }

  override def shutdown(): Unit = storing.shutdown()
}
