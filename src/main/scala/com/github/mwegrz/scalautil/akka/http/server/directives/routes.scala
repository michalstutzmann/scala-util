package com.github.mwegrz.scalautil.akka.http.server.directives

import java.time.Instant

import akka.NotUsed
import akka.http.scaladsl.marshalling.{ Marshal, ToEntityMarshaller }
import akka.http.scaladsl.model.MessageEntity
import akka.http.scaladsl.model.sse.ServerSentEvent
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.{ PathMatcher1, Route }
import akka.http.scaladsl.unmarshalling.{ FromEntityUnmarshaller, Unmarshaller }
import akka.stream.Materializer
import akka.stream.scaladsl.{ Flow, Source }
import com.github.mwegrz.scalautil.store.{ KeyValueStore, TimeSeriesStore }

import scala.concurrent.duration._
import scala.concurrent.ExecutionContext

object routes {
  implicit private val instantDeserializer: Unmarshaller[String, Instant] =
    Unmarshaller.strict[String, Instant](a => Instant.parse(a))

  case class Envelope[Value](data: List[Value])

  def keyValueStore[Key, Value](implicit store: KeyValueStore[Key, Value],
                                pathMatcher1: PathMatcher1[Key],
                                unitMarshaller: ToEntityMarshaller[Unit],
                                valueMarshaller: ToEntityMarshaller[Value],
                                valueIterableMarshaller: ToEntityMarshaller[Envelope[Value]],
                                unmarshaller: FromEntityUnmarshaller[Value],
                                executionContext: ExecutionContext): Route = {
    pathEnd {
      get {
        complete(store.retrieveAll.map(a => Envelope(a.values.toList)))
      }
    } ~ path(pathMatcher1) { id =>
      put {
        entity(as[Value]) { entity =>
          complete(store.store(id, entity))
        }
      } ~
        get {
          complete(store.retrieveByKey(id))
        } ~
        delete {
          complete(store.removeByKey(id))
        }
    }
  }

  def timeSeriesStore[Key, Value](implicit store: TimeSeriesStore[Key, Value],
                                  flow: Flow[Value, Value, NotUsed],
                                  pathMatcher1: PathMatcher1[Key],
                                  unitMarshaller: ToEntityMarshaller[Unit],
                                  valueMarshaller: ToEntityMarshaller[Value],
                                  valueSourceMarshaller: ToEntityMarshaller[Source[Value, NotUsed]],
                                  unmarshaller: FromEntityUnmarshaller[Value],
                                  executionContext: ExecutionContext,
                                  materializer: Materializer): Route = {
    path(pathMatcher1) { id =>
      post {
        entity(as[Value]) { entity =>
          complete(
            store.store
              .runWith(
                Source
                  .single((id, Instant.now(), entity))
                  .mapMaterializedValue(_ => ())))
        }
      } ~
        get {
          parameters('from_time.as[Instant], 'to_time.as[Instant]) { (fromTime, toTime) =>
            complete(store.retrieveRange(Set(id), fromTime, toTime).map { case (_, value) => value })
          } ~ pass {
            import akka.http.scaladsl.marshalling.sse.EventStreamMarshalling._
            val source = Source
              .maybe[Value]
              .via(flow)
              .map(p => ServerSentEvent(Marshal(p).to[MessageEntity].value.get.get.toString))
              .keepAlive(15.second, () => ServerSentEvent.heartbeat)
            complete(source)
          }
        }
    }
  }
}
