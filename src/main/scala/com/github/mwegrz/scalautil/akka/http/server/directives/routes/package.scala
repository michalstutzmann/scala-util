package com.github.mwegrz.scalautil.akka.http.server.directives

import java.time.Instant

import akka.http.scaladsl.marshalling.ToEntityMarshaller
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.{ PathMatcher1, Route }
import akka.http.scaladsl.unmarshalling.{ FromEntityUnmarshaller, Unmarshaller }
import com.github.mwegrz.scalautil.store.KeyValueStore

import scala.concurrent.duration._
import scala.concurrent.ExecutionContext

package object routes {
  implicit private val instantDeserializer: Unmarshaller[String, Instant] =
    Unmarshaller.strict[String, Instant](a => Instant.parse(a))

  def keyValueStore[Key, Value](implicit store: KeyValueStore[Key, Value],
                                keyPathMatcher: PathMatcher1[Key],
                                unitToEntityMarshaller: ToEntityMarshaller[Unit],
                                valueMarshaller: ToEntityMarshaller[Value],
                                valueIterableMarshaller: ToEntityMarshaller[Envelope[Value]],
                                unmarshaller: FromEntityUnmarshaller[Value],
                                fromStringToKeyUnmarshaller: Unmarshaller[String, Key],
                                executionContext: ExecutionContext): Route = {
    pathEnd {
      get {
        parameters('from.as[Key].?, 'count.as[Int]) { (from, count) =>
          complete(store.retrievePage(from, count).map(a => Envelope(a.values.toList)))
        } ~ pass {
          complete(store.retrieveAll.map(a => Envelope(a.values.toList)))
        }
      }
    } ~ path(keyPathMatcher) { id =>
      put {
        entity(as[Value]) { entity =>
          complete(store.store(id, entity))
        }
      } ~
        get {
          complete(store.retrieve(id))
        } ~
        delete {
          complete(store.remove(id))
        }
    }
  }
}
