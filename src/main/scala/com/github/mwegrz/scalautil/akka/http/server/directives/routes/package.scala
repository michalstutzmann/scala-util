package com.github.mwegrz.scalautil.akka.http.server.directives

import akka.NotUsed
import akka.http.scaladsl.model.{ MessageEntity, StatusCodes }
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.{ Directive, Directive0, PathMatcher1, Route, ValidationRejection }
import akka.http.scaladsl.marshalling.{ Marshal, ToEntityMarshaller }
import akka.http.scaladsl.unmarshalling.{ FromEntityUnmarshaller, Unmarshaller }
import akka.stream.{ Materializer, OverflowStrategy }
import akka.stream.scaladsl.{ Keep, Sink, Source }
import scodec.bits.ByteVector
import akka.http.scaladsl.marshalling.sse.EventStreamMarshalling._
import java.time.Instant
import akka.http.scaladsl.model.sse.ServerSentEvent
import akka.http.scaladsl.server.directives.RouteDirectives.reject
import com.github.mwegrz.scalautil.store.{ KeyValueStore, TimeSeriesStore }
import org.scalactic.{ Bad, Good }

import scala.concurrent.duration._
import scala.concurrent.ExecutionContext
import scala.util.{ Failure, Success }

package object routes {
  private[routes] val LiveValuesBufferSize = 1000

  def keyValueStore[Key, Value](name: String)(
      implicit store: KeyValueStore[Key, Value],
      keyPathMatcher: PathMatcher1[Key],
      unitToEntityMarshaller: ToEntityMarshaller[Unit],
      valueToEntityMarshaller: ToEntityMarshaller[Value],
      errorDocumentToEntityMarshaller: ToEntityMarshaller[ErrorDocument],
      singleDocumentToEntityMarshaller: ToEntityMarshaller[SingleDocument[Value]],
      multiDocumentToEntityMarshaller: ToEntityMarshaller[MultiDocument[Value]],
      entityToSingleDocumentUnmarshaller: FromEntityUnmarshaller[SingleDocument[Value]],
      entityToValueUnmarshaller: FromEntityUnmarshaller[Value],
      fromStringToKeyUnmarshaller: Unmarshaller[String, Key],
      validator: Validator[Value],
      executionContext: ExecutionContext
  ): Route =
    pathEnd {
      get {
        parameters((Symbol("page[cursor]").as[Key].?, Symbol("page[limit]").as[Int])) { (cursor, limit) =>
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
      } ~
        post {
          entity(as[SingleDocument[Value]]) {
            case envelope @ SingleDocument(Some(resource @ Resource(_, _, entity))) =>
              validate(entity)(validator) {
                onComplete(store.add(entity)) {
                  case Success(Some((key, value))) =>
                    complete(
                      StatusCodes.Created -> envelope
                        .copy(data = Some(resource.copy(id = Some(key.toString), attributes = value)))
                    )
                  case Success(None) =>
                    complete(StatusCodes.Created -> envelope)
                  case Failure(KeyValueStore.KeyExistsException(_))    => complete(StatusCodes.Conflict)
                  case Failure(KeyValueStore.InvalidValueException(_)) => complete(StatusCodes.BadRequest)
                  case Failure(KeyValueStore.ForbiddenException(_))    => complete(StatusCodes.Forbidden)
                }
              }
          }
        }
    } ~ path(keyPathMatcher) { id =>
      post {
        entity(as[SingleDocument[Value]]) {
          case envelope @ SingleDocument(Some(resource @ Resource(_, _, entity))) =>
            validate(entity)(validator) {
              onComplete(store.add(id, entity)) {
                case Success(value)                                  => complete(StatusCodes.Created -> value)
                case Failure(KeyValueStore.KeyExistsException(_))    => complete(StatusCodes.Conflict)
                case Failure(KeyValueStore.InvalidValueException(_)) => complete(StatusCodes.BadRequest)
                case Failure(KeyValueStore.ForbiddenException(_))    => complete(StatusCodes.Forbidden)
              }
            }
        }
      } ~
        patch {
          entity(as[SingleDocument[Value]]) {
            case document @ SingleDocument(Some(resource @ Resource(_, _, entity))) =>
              validate(entity)(validator) {
                onComplete(store.update(id, entity)) {
                  case Success(Some(value)) =>
                    val updatedResource = resource.copy(attributes = value)
                    complete(document.copy(data = Some(updatedResource)))
                  case Success(None) =>
                    complete(SingleDocument(Option.empty[Resource[Value]]))
                  case Failure(KeyValueStore.KeyExistsException(_))    => complete(StatusCodes.Conflict)
                  case Failure(KeyValueStore.InvalidValueException(_)) => complete(StatusCodes.BadRequest)
                  case Failure(KeyValueStore.ForbiddenException(_))    => complete(StatusCodes.Forbidden)
                }
              }
          }
        } ~
        get {
          onSuccess(store.retrieve(id)) { value =>
            rejectEmptyResponse {
              complete(value.map(b => SingleDocument[Value](Some(Resource(name, Some(id.toString), b)))))
            }
          }
        } ~
        delete {
          complete(StatusCodes.NoContent -> store.delete(id))
        }
    }

  def singleValue[Key, Value](name: String, id: Key)(
      implicit store: KeyValueStore[Key, Value],
      unitToEntityMarshaller: ToEntityMarshaller[Unit],
      valueToEntityMarshaller: ToEntityMarshaller[Value],
      errorDocumentToEntityMarshaller: ToEntityMarshaller[ErrorDocument],
      singleDocumentToEntityMarshaller: ToEntityMarshaller[SingleDocument[Value]],
      entityToSingleDocumentUnmarshaller: FromEntityUnmarshaller[SingleDocument[Value]],
      validator: Validator[Value],
      executionContext: ExecutionContext
  ): Route =
    pathEnd {
      post {
        entity(as[SingleDocument[Value]]) {
          case envelope @ SingleDocument(Some(resource @ Resource(_, _, entity))) =>
            validate(entity)(validator) {
              onComplete(store.add(id, entity)) {
                case Success(value)                                  => complete(StatusCodes.Created -> value)
                case Failure(KeyValueStore.KeyExistsException(_))    => complete(StatusCodes.Conflict)
                case Failure(KeyValueStore.InvalidValueException(_)) => complete(StatusCodes.BadRequest)
                case Failure(KeyValueStore.ForbiddenException(_))    => complete(StatusCodes.Forbidden)
              }
            }
        }
      } ~
        patch {
          entity(as[SingleDocument[Value]]) {
            case document @ SingleDocument(Some(resource @ Resource(_, _, entity))) =>
              validate(entity)(validator) {
                onComplete(store.update(id, entity)) {
                  case Success(Some(value)) =>
                    val updatedResource = resource.copy(attributes = value)
                    complete(document.copy(data = Some(updatedResource)))
                  case Success(None) =>
                    complete(SingleDocument(Option.empty[Resource[Value]]))
                  case Failure(KeyValueStore.KeyExistsException(_))    => complete(StatusCodes.Conflict)
                  case Failure(KeyValueStore.InvalidValueException(_)) => complete(StatusCodes.BadRequest)
                  case Failure(KeyValueStore.ForbiddenException(_))    => complete(StatusCodes.Forbidden)
                }
              }
          }
        } ~
        get {
          onSuccess(store.retrieve(id)) { value =>
            rejectEmptyResponse {
              complete(value.map(b => SingleDocument[Value](Some(Resource(name, Some(id.toString), b)))))
            }
          }
        } ~
        delete {
          complete(StatusCodes.NoContent -> store.delete(id))
        }
    }

  /*def timeSeriesSource[Key, Value](name: String, keys: Set[Key])(
      implicit
      valueStore: TimeSeriesStore[Key, Value],
      valueSource: Source[(Key, Instant, Value), NotUsed],
      instantFromStringUnmarshaller: Unmarshaller[String, Instant],
      valueToEntityMarshaller: ToEntityMarshaller[Value],
      multiDocumentToEntityMarshaller: ToEntityMarshaller[MultiDocument[Value]],
      executionContext: ExecutionContext,
      materializer: Materializer
  ): Route = get {
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
                .retrieveRange(keys, since.get, until.get)
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

  def timeSeriesSink[Key, Value](name: String, keys: Set[Key])(
      implicit
      valueStore: TimeSeriesStore[Key, Value],
      valueSink: Sink[(Key, Instant, Value), NotUsed],
      singleDocumentToEntityMarshaller: ToEntityMarshaller[SingleDocument[Value]],
      entityToSingleDocumentUnmarshaller: FromEntityUnmarshaller[SingleDocument[Value]],
      materializer: Materializer,
      validator: Validator[Value]
  ): Route = post {
    entity(as[SingleDocument[Value]]) {
      case SingleDocument(Some(resource @ Resource(_, _, value))) =>
        validate(value)(validator) {
          val time = Instant.now()
          keys foreach { key =>
            Source.single((key, Instant.now(), value)).runWith(valueStore.addIfNotExists)
            Source.single(value).runWith(valueSink.contramap((key, time, _)))
          }
          val id = createId(time)
          complete(
            StatusCodes.Created -> SingleDocument(Some(resource.copy(`type` = name, id = Some(id))))
          )
        }
    }
  }*/

  private[routes] def validate[Value](value: Value)(implicit validator: Validator[Value]): Directive0 =
    Directive { inner =>
      validator.validate(value) match {
        case Good(_)      => inner(())
        case Bad(message) => reject(ValidationRejection(message))
      }
    }

  private[routes] def retrieveHistoricalValues[Key, Value](keys: Set[Key], sinceTime: Instant)(
      implicit valueStore: TimeSeriesStore[Key, Value]
  ): Source[(Instant, Value), NotUsed] =
    valueStore
      .retrieveRange(keys, sinceTime)
      .map { case (_, time, value) => (time, value) }

  private[routes] def retrieveHistoricalValues[Key, Value](keys: Set[Key], tail: Int)(
      implicit valueStore: TimeSeriesStore[Key, Value]
  ): Source[(Instant, Value), NotUsed] =
    valueStore
      .retrieveLast(keys, tail)
      .map { case (_, time, value) => (time, value) }

  private[routes] def retrieveHistoricalValues[Key, Value](keys: Set[Key], tail: Int, untilTime: Instant)(
      implicit valueStore: TimeSeriesStore[Key, Value]
  ): Source[(Instant, Value), NotUsed] =
    valueStore
      .retrieveLastUntil(keys, tail, untilTime)
      .map { case (_, time, value) => (time, value) }

  private[routes] def receiveLiveValues[Key, Value](keys: Set[Key])(
      implicit valueSource: Source[(Key, Instant, Value), NotUsed]
  ): Source[(Instant, Value), NotUsed] =
    valueSource
      .filter { case (key, _, _) => keys.contains(key) }
      .map {
        case (_, time, value) => (time, value)
      }

  private[routes] def toServerSentEvents[Key, Value](source: Source[(Instant, Value), NotUsed])(
      implicit valueToEntityMarshaller: ToEntityMarshaller[Value],
      executionContext: ExecutionContext,
      materializer: Materializer
  ): Source[ServerSentEvent, NotUsed] =
    source
      .mapAsync(2) {
        case (time, value) =>
          Marshal(value).to[MessageEntity].value.get.get.toStrict(Int.MaxValue.seconds) map { e =>
            val data = e.data
            val id = createId(time)
            ServerSentEvent(data = data.utf8String, id = Some(id))
          }
      }
      .keepAlive(15.second, () => ServerSentEvent.heartbeat)

  private[routes] def createId(time: Instant): String = ByteVector.fromLong(time.toEpochMilli).toBase64
}
