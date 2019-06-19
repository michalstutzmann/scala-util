package com.github.mwegrz.scalautil.akka.http.server.directives

import akka.NotUsed
import akka.http.scaladsl.marshalling.{ Marshal, ToEntityMarshaller }
import akka.http.scaladsl.model.{ MessageEntity, StatusCodes }
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.{ Directive, Directive0, PathMatcher1, Route, ValidationRejection }
import akka.http.scaladsl.unmarshalling.{ FromEntityUnmarshaller, Unmarshaller }
import akka.stream.{ Materializer, OverflowStrategy }
import akka.stream.scaladsl.{ Sink, Source }
import scodec.bits.ByteVector
import akka.http.scaladsl.marshalling.sse.EventStreamMarshalling._
import java.time.Instant

import akka.http.scaladsl.model.sse.ServerSentEvent
import akka.http.scaladsl.server.directives.RouteDirectives.reject
import com.github.mwegrz.scalautil.store.{ KeyValueStore, TimeSeriesStore }
import org.scalactic.{ Bad, Good }

import scala.concurrent.duration._
import scala.concurrent.ExecutionContext

package object routes {
  private val LiveValuesBufferSize = 1000

  def keyValueStore[Key, Value](name: String)(
      implicit store: KeyValueStore[Key, Value],
      keyPathMatcher: PathMatcher1[Key],
      unitToEntityMarshaller: ToEntityMarshaller[Unit],
      valueToEntityMarshaller: ToEntityMarshaller[Value],
      multiDocumentToEntityMarshaller: ToEntityMarshaller[MultiDocument[Value]],
      entityToSingleDocumentUnmarshaller: FromEntityUnmarshaller[SingleDocument[Value]],
      entityToValueUnmarshaller: FromEntityUnmarshaller[Value],
      fromStringToKeyUnmarshaller: Unmarshaller[String, Key],
      validator: Validator[Value],
      executionContext: ExecutionContext
  ): Route =
    pathEnd {
      get {
        parameters(Symbol("page[cursor]").as[Key].?, Symbol("page[limit]").as[Int]) { (cursor, limit) =>
          val envelope = store
            .retrievePage(cursor, limit + 1)
            .map { elems =>
              val data =
                elems
                  .take(limit)
                  .toList
                  .map { case (key, value) => Resource(name, Some(key.toString), value) }
              //val nextCursor = a.keys.lastOption.map(b => ByteVector.encodeAscii(b.toString).right.get.toBase64)
              MultiDocument(data)
            }
          complete(envelope)
        } ~ pass {
          val envelope = store.retrieveAll
            .map { values =>
              val data = values.toList
              MultiDocument(data.map {
                case (key, value) => Resource(name, Some(key.toString), value)
              })
            }
          complete(envelope)
        }
      }
    } ~ path(keyPathMatcher) { id =>
      post {
        entity(as[SingleDocument[Value]]) {
          case SingleDocument(Resource(_, _, entity)) =>
            validate(entity)(validator) {
              complete(store.add(id, entity))
            }
        }
      } ~
        patch {
          entity(as[SingleDocument[Value]]) {
            case SingleDocument(Resource(_, _, entity)) =>
              validate(entity)(validator) {
                complete(store.add(id, entity))
              }
          }
        } ~
        get {
          complete(store.retrieve(id))
        } ~
        delete {
          complete(store.delete(id))
        }
    }

  def singleValue[Key, Value](name: String, id: Key)(
      implicit store: KeyValueStore[Key, Value],
      unitToEntityMarshaller: ToEntityMarshaller[Unit],
      valueToEntityMarshaller: ToEntityMarshaller[Value],
      entityToSingleDocumentUnmarshaller: FromEntityUnmarshaller[SingleDocument[Value]],
      validator: Validator[Value]
  ): Route =
    pathEnd {
      post {
        entity(as[SingleDocument[Value]]) {
          case SingleDocument(Resource(_, _, entity)) =>
            validate(entity)(validator) {
              complete(store.add(id, entity))
            }
        }
      } ~
        patch {
          entity(as[SingleDocument[Value]]) {
            case SingleDocument(Resource(_, _, entity)) =>
              validate(entity)(validator) {
                complete(store.add(id, entity))
              }
          }
        } ~
        get {
          complete(store.retrieve(id))
        } ~
        delete {
          complete(store.delete(id))
        }
    }

  def timeSeriesSource[Key, Value](name: String, keys: Set[Key])(
      implicit
      //keyStore: KeyValueStore[Key, _],
      valueStore: TimeSeriesStore[Key, Value],
      valueSource: Source[(Key, Instant, Value), NotUsed],
      instantFromStringUnmarshaller: Unmarshaller[String, Instant],
      valueToEntityMarshaller: ToEntityMarshaller[Value],
      executionContext: ExecutionContext,
      materializer: Materializer
  ): Route = get {
    parameters(
      Symbol("filter[since]").as[Instant].?,
      Symbol("filter[until]").as[Instant].?,
      Symbol("filter[tail]").as[Int].?,
      Symbol("filter[follow]").as[Boolean].?(false)
    ) { (since, until, tail, follow) =>
      optionalHeaderValueByName("Last-Event-ID") { lastEventId =>
        val lastEventFromTime = lastEventId.map(
          value => Instant.ofEpochMilli(ByteVector.fromBase64(value).get.toLong()).plusNanos(1)
        )
        val lastEventTimeOrFromTime = lastEventFromTime.orElse(since)
        val liveValues = receiveLiveValues(keys)

        val values = tail match {
          case Some(value) =>
            val historicalValues = retrieveHistoricalValues(keys, value)

            if (follow) {
              historicalValues.concat(
                liveValues.buffer(LiveValuesBufferSize, OverflowStrategy.dropNew)
              )
            } else {
              historicalValues
            }

          case None =>
            lastEventTimeOrFromTime.fold(liveValues) { value =>
              val historicalValues =
                retrieveHistoricalValues(keys, value)

              if (follow) {
                val historicalAndLiveValues =
                  historicalValues.concat(
                    liveValues.buffer(LiveValuesBufferSize, OverflowStrategy.dropNew)
                  )
                until
                  .fold(historicalAndLiveValues)(
                    value => historicalAndLiveValues.takeWhile { case (time, _) => time.isBefore(value) }
                  )
              } else {
                historicalValues
              }
            }
        }

        val response = toServerSentEvents(values)
        complete(response)
      }
    }
  }

  def timeSeriesSink[Key, Value](name: String, keys: Set[Key])(
      implicit
      //valueStore: TimeSeriesStore[Key, Value],
      valueSink: Sink[(Key, Instant, Value), NotUsed],
      entityToSingleDocumentUnmarshaller: FromEntityUnmarshaller[SingleDocument[Value]],
      materializer: Materializer,
      validator: Validator[Value]
  ): Route = post {
    entity(as[SingleDocument[Value]]) {
      case SingleDocument(Resource(_, _, value)) =>
        validate(value)(validator) {
          keys foreach { key =>
            //Source.single((key, Instant.now(), value)).runWith(valueStore.add)
            Source.single(value).runWith(valueSink.contramap((key, Instant.now(), _)))
          }
          complete(
            StatusCodes.Created
              .copy(intValue = StatusCodes.Created.intValue)(
                reason = "",
                defaultMessage = "",
                allowsEntity = true
              )
          )
        }
    }
  }

  private def validate[Value](value: Value)(implicit validator: Validator[Value]): Directive0 =
    Directive { inner =>
      validator.validate(value) match {
        case Good(_)      => inner(())
        case Bad(message) => reject(ValidationRejection(message))
      }
    }

  private def retrieveHistoricalValues[Key, Value](keys: Set[Key], fromTime: Instant)(
      implicit valueStore: TimeSeriesStore[Key, Value]
  ): Source[(Instant, Value), NotUsed] =
    valueStore
      .retrieveRange(keys, fromTime)
      .map { case (_, time, value) => (time, value) }

  private def retrieveHistoricalValues[Key, Value](keys: Set[Key], tail: Int)(
      implicit valueStore: TimeSeriesStore[Key, Value]
  ): Source[(Instant, Value), NotUsed] =
    valueStore
      .retrieveLast(keys, tail)
      .map { case (_, time, value) => (time, value) }

  private def receiveLiveValues[Key, Value](keys: Set[Key])(
      implicit valueSource: Source[(Key, Instant, Value), NotUsed]
  ): Source[(Instant, Value), NotUsed] =
    valueSource
      .filter { case (key, _, _) => keys.contains(key) }
      .map {
        case (_, time, value) => (time, value)
      }

  private def toServerSentEvents[Key, Value](source: Source[(Instant, Value), NotUsed])(
      implicit valueToEntityMarshaller: ToEntityMarshaller[Value],
      executionContext: ExecutionContext,
      materializer: Materializer
  ): Source[ServerSentEvent, NotUsed] =
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
