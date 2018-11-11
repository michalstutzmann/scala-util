package com.github.mwegrz.scalautil.akka.http.server.directives

import akka.NotUsed
import akka.http.scaladsl.marshalling.{ Marshal, ToEntityMarshaller }
import akka.http.scaladsl.model.{ MessageEntity, StatusCodes }
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.{ PathMatcher1, Route }
import akka.http.scaladsl.unmarshalling.{ FromEntityUnmarshaller, Unmarshaller }
import akka.stream.{ Materializer, OverflowStrategy }
import akka.stream.scaladsl.{ Sink, Source }
import scodec.bits.ByteVector
import akka.http.scaladsl.marshalling.sse.EventStreamMarshalling._
import java.time.Instant

import akka.http.scaladsl.model.sse.ServerSentEvent
import com.github.mwegrz.scalautil.store.{ KeyValueStore, TimeSeriesStore }

import scala.concurrent.duration._
import scala.concurrent.ExecutionContext

package object routes {
  private val LiveValuesBufferSize = 1000

  def keyValueStore[Key, Value](name: String)(
      implicit valueStore: KeyValueStore[Key, Value],
      keyPathMatcher: PathMatcher1[Key],
      unitToEntityMarshaller: ToEntityMarshaller[Unit],
      valueToEntityMarshaller: ToEntityMarshaller[Value],
      multiDocumentToEntityMarshaller: ToEntityMarshaller[MultiDocument[Value]],
      entityToSingleDocumentUnmarshaller: FromEntityUnmarshaller[SingleDocument[Value]],
      entityToValueUnmarshaller: FromEntityUnmarshaller[Value],
      fromStringToKeyUnmarshaller: Unmarshaller[String, Key],
      executionContext: ExecutionContext): Route =
    pathEnd {
      get {
        parameters(Symbol("page[cursor]").as[Key].?, Symbol("page[limit]").as[Int]) {
          (cursor, limit) =>
            val envelope = valueStore
              .retrievePage(cursor, limit + 1)
              .map { elems =>
                val data =
                  elems
                    .take(limit)
                    .toList
                    .map { case (key, value) => Resource(name, key.toString, value) }
                //val nextCursor = a.keys.lastOption.map(b => ByteVector.encodeAscii(b.toString).right.get.toBase64)
                MultiDocument(data)
              }
            complete(envelope)
        } ~ pass {
          val envelope = valueStore.retrieveAll
            .map { values =>
              val data = values.toList
              MultiDocument(data.map {
                case (key, value) => Resource(name, key.toString, value)
              })
            }
          complete(envelope)
        }
      }
    } ~ path(keyPathMatcher) { id =>
      post {
        entity(as[SingleDocument[Value]]) {
          case SingleDocument(Resource(_, _, entity)) =>
            complete(valueStore.add(id, entity))
        }
      } ~
        get {
          complete(valueStore.retrieve(id))
        } ~
        delete {
          complete(valueStore.delete(id))
        }
    }

  def singleValue[Key, Value](name: String, id: Key)(
      implicit valueStore: KeyValueStore[Key, Value],
      //keyPathMatcher: PathMatcher1[Key],
      unitToEntityMarshaller: ToEntityMarshaller[Unit],
      valueToEntityMarshaller: ToEntityMarshaller[Value],
      //multiDocumentToEntityMarshaller: ToEntityMarshaller[MultiDocument[Value]],
      entityToSingleDocumentUnmarshaller: FromEntityUnmarshaller[SingleDocument[Value]],
      //entityToValueUnmarshaller: FromEntityUnmarshaller[Value],
      //fromStringToKeyUnmarshaller: Unmarshaller[String, Key],
      //executionContext: ExecutionContext
  ): Route =
    pathEnd {
      post {
        entity(as[SingleDocument[Value]]) {
          case SingleDocument(Resource(_, _, entity)) =>
            complete(valueStore.add(id, entity))
        }
      } ~
        get {
          complete(valueStore.retrieve(id))
        } ~
        delete {
          complete(valueStore.delete(id))
        }
    }

  def timeSeriesStoreSource[Key, Value](name: String, keys: Set[Key])(
      implicit
      valueStore: TimeSeriesStore[Key, Value],
      valueSource: Source[(Key, Instant, Value), NotUsed],
      instantFromStringUnmarshaller: Unmarshaller[String, Instant],
      valueToEntityMarshaller: ToEntityMarshaller[Value],
      executionContext: ExecutionContext,
      materializer: Materializer): Route = get {
    parameters(Symbol("filter[from_time]").as[Instant].?,
               Symbol("filter[until_time]").as[Instant].?) { (fromTime, untilTime) =>
      optionalHeaderValueByName("Last-Event-ID") { lastEventId =>
        val lastEventFromTime = lastEventId.map(value =>
          Instant.ofEpochMilli(ByteVector.fromBase64(value).get.toLong()).plusNanos(1))
        val lastEventTimeOrFromTime = lastEventFromTime.orElse(fromTime)
        val liveValues = receiveLiveValues(keys)
        val values = lastEventTimeOrFromTime.fold(receiveLiveValues(keys)) { value =>
          val historicalValues =
            retrieveHistoricalValues(keys, value, untilTime.getOrElse(Instant.now()))
          historicalValues.concat(liveValues.buffer(LiveValuesBufferSize, OverflowStrategy.dropNew))
        }
        val response = toServerSentEvents(values)
        complete(response)
      }
    }
  }

  def timeSeriesStoreSink[Key, Value](name: String, keys: Set[Key])(
      implicit
      valueStore: TimeSeriesStore[Key, Value],
      valueSink: Sink[Value, NotUsed],
      entityToSingleDocumentUnmarshaller: FromEntityUnmarshaller[SingleDocument[Value]],
      materializer: Materializer): Route = post {
    entity(as[SingleDocument[Value]]) {
      case SingleDocument(Resource(_, _, value)) =>
        keys foreach { key =>
          Source.single((key, Instant.now(), value)).runWith(valueStore.add)
          Source.single(value).runWith(valueSink)
        }
        complete(
          StatusCodes.Created
            .copy(intValue = StatusCodes.Created.intValue)(reason = "",
                                                           defaultMessage = "",
                                                           allowsEntity = true))
    }
  }

  private def retrieveHistoricalValues[Key, Value](keys: Set[Key],
                                                   fromTime: Instant,
                                                   untilTime: Instant)(
      implicit valueStore: TimeSeriesStore[Key, Value]): Source[(Instant, Value), NotUsed] =
    valueStore
      .retrieveRange(keys, fromTime, untilTime)
      .map { case (_, time, value) => (time, value) }

  private def receiveLiveValues[Key, Value](keys: Set[Key])(
      implicit valueSource: Source[(Key, Instant, Value), NotUsed])
    : Source[(Instant, Value), NotUsed] =
    valueSource
      .filter { case (key, _, _) => keys.contains(key) }
      .map {
        case (_, time, value) => (time, value)
      }

  private def toServerSentEvents[Key, Value](source: Source[(Instant, Value), NotUsed])(
      implicit valueToEntityMarshaller: ToEntityMarshaller[Value],
      executionContext: ExecutionContext,
      materializer: Materializer): Source[ServerSentEvent, NotUsed] =
    source
      .mapAsync(2) {
        case (time, value) =>
          Marshal(value).to[MessageEntity].value.get.get.toStrict(Int.MaxValue.seconds) map { e =>
            val data = e.data
            val id = ByteVector.fromLong(time.toEpochMilli).toBase64
            ServerSentEvent(data = data.utf8String, id = Some(id))
          }
      }
      .keepAlive(15.second, () => ServerSentEvent.heartbeat)
}
