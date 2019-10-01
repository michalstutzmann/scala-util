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
    materializer: Materializer
) extends Shutdownable {
  private val killSwitch = valueSource
    .log(name)
    .viaMat(KillSwitches.single)(Keep.right)
    .toMat(valueStore.addIfNotExists)(Keep.left)
    .run()

  def route(keys: Set[Key]): Route = get {
    parameters(
      (
        Symbol("filter[since]").as[Instant].?,
        Symbol("filter[until]").as[Instant].?,
        Symbol("filter[tail]").as[Int].?
      )
    ) { (since, until, tail) =>
      optionalHeaderValueByName("Accept") {
        case Some("text/event-stream") =>
          optionalHeaderValueByName("Last-Event-ID") { lastEventId =>
            val lastEventFromTime = lastEventId.map(
              value => Instant.ofEpochMilli(ByteVector.fromBase64(value).get.toLong()).plusNanos(1)
            )
            val lastEventTimeOrFromTime = lastEventFromTime.orElse(since)
            val liveValues = receiveLiveValues(keys)

            val values = tail match {
              case Some(value) =>
                val historicalValues = retrieveHistoricalValues(keys, value)

                historicalValues.concat(
                  liveValues.buffer(LiveValuesBufferSize, OverflowStrategy.dropNew)
                )
              case None =>
                lastEventTimeOrFromTime.fold(liveValues) { value =>
                  val historicalValues =
                    retrieveHistoricalValues(keys, value)

                  val historicalAndLiveValues =
                    historicalValues.concat(
                      liveValues.buffer(LiveValuesBufferSize, OverflowStrategy.dropNew)
                    )
                  until
                    .fold(historicalAndLiveValues)(
                      value => historicalAndLiveValues.takeWhile { case (time, _) => time.isBefore(value) }
                    )
                }
            }
            val response = toServerSentEvents(values)
            complete(response)
          }
        case _ =>
          tail match {
            case Some(value) =>
              val values = valueStore
                .retrieveLast(keys, value)
                .map(_._3)
                .map(Resource(name, None, _))
                .toMat(Sink.seq)(Keep.right)
                .run()
                .map(_.toList)
                .map(MultiDocument(_))
              complete(values)
            case None =>
              val values = valueStore
                .retrieveRange(keys, since.getOrElse(Instant.EPOCH), until.getOrElse(Instant.now))
                .map(_._3)
                .map(Resource(name, None, _))
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
