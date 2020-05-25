package com.github.mwegrz.scalautil.akka.http.server.directives.routes

import java.time.Instant

import akka.NotUsed
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import akka.http.scaladsl.marshalling.ToEntityMarshaller
import akka.http.scaladsl.unmarshalling.Unmarshaller
import akka.stream.{ KillSwitches, Materializer, OverflowStrategy }
import akka.stream.scaladsl.{ Keep, Sink, Source }
import scodec.bits.ByteVector
import akka.http.scaladsl.marshalling.sse.EventStreamMarshalling._
import com.github.mwegrz.app.Shutdownable
import com.github.mwegrz.scalautil.akka.stream.scaladsl.{ PolicyRestartSink, RestartPolicy }
import com.github.mwegrz.scalautil.store.TimeSeriesStore

import scala.concurrent.ExecutionContext

class TimeSeriesSource[Key, Value](name: String)(
    implicit
    valueStore: TimeSeriesStore[Key, Value],
    valueSource: Source[(Key, Instant, Value), NotUsed],
    instantFromStringUnmarshaller: Unmarshaller[String, Instant],
    valueToEntityMarshaller: ToEntityMarshaller[Value],
    multiDocumentToEntityMarshaller: ToEntityMarshaller[MultiDocument[Value]],
    executionContext: ExecutionContext,
    materializer: Materializer,
    restartPolicy: RestartPolicy
) extends Shutdownable {
  private val killSwitch = valueSource
    .viaMat(KillSwitches.single)(Keep.right)
    .toMat(PolicyRestartSink.withBackoff(() => valueStore.addOrReplace))(Keep.left)
    .run()

  def route(keys: Set[Key]): Route = get {
    parameters(
      (
        Symbol("filter[since]").as[Instant].?,
        Symbol("filter[until]").as[Instant].?,
        Symbol("filter[tail]").as[Int].?,
        Symbol("lastEventId").as[String].?
      )
    ) { (since, until, tail, lastEventIdParam) =>
      optionalHeaderValueByName("Accept") {
        case Some("text/event-stream") =>
          optionalHeaderValueByName("Last-Event-ID") { lastEventId =>
            val lastEventTime = lastEventId
              .orElse(lastEventIdParam)
              .map(value => Instant.ofEpochMilli(ByteVector.fromBase64(value).get.toLong()).plusNanos(1))
            val lastEventTimeOrSinceTime = lastEventTime.orElse(since)
            val liveValues = receiveLiveValues(keys)

            val values = (lastEventTime, tail) match {
              case (None, Some(value)) =>
                val historicalValues = retrieveHistoricalValues(keys, value)

                historicalValues.concat(
                  liveValues.buffer(LiveValuesBufferSize, OverflowStrategy.dropNew)
                )
              case (_, _) =>
                lastEventTimeOrSinceTime.fold(liveValues) { sinceValue =>
                  val historicalAndLiveValues =
                    retrieveHistoricalValues(keys, sinceValue).concat(
                      liveValues.buffer(LiveValuesBufferSize, OverflowStrategy.dropNew)
                    )
                  until
                    .fold(historicalAndLiveValues)(untilValue => retrieveHistoricalValues(keys, sinceValue, untilValue))
                }
            }
            val response = toServerSentEvents(values)
            complete(response)
          }
        case _ =>
          tail match {
            case Some(value) =>
              val values = until
                .fold(valueStore.retrieveLast(keys, value))(valueStore.retrieveLastUntil(keys, value, _))
                .map(_._3)
                .map(a => Resource(name, null, Some(a)))
                .toMat(Sink.seq)(Keep.right)
                .run()
                .map(_.toList)
                .map(MultiDocument(_))
              complete(values)
            case None =>
              val values = valueStore
                .retrieveRange(keys, since.getOrElse(Instant.EPOCH), until.getOrElse(Instant.now))
                .map(_._3)
                .map(a => Resource(name, null, Some(a)))
                .toMat(Sink.seq)(Keep.right)
                .run()
                .map(_.toList)
                .map(MultiDocument(_))
              complete(values)
          }
      }
    }
  }

  override def shutdown(): Unit = killSwitch.shutdown()
}
