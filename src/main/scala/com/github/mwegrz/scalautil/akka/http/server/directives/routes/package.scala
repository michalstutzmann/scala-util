package com.github.mwegrz.scalautil.akka.http.server.directives

import java.time.Instant

import akka.http.scaladsl.marshalling.ToEntityMarshaller
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.{ PathMatcher1, Route }
import akka.http.scaladsl.unmarshalling.{ FromEntityUnmarshaller, Unmarshaller }
import com.github.mwegrz.scalautil.StringVal
import com.github.mwegrz.scalautil.store.KeyValueStore
import scodec.bits.ByteVector

import scala.concurrent.ExecutionContext

package object routes {
  implicit private val instantDeserializer: Unmarshaller[String, Instant] =
    Unmarshaller.strict[String, Instant](a => Instant.parse(a))

  def keyValueStore[Key <: StringVal, Value](name: String)(
      implicit store: KeyValueStore[Key, Value],
      keyPathMatcher: PathMatcher1[Key],
      unitToEntityMarshaller: ToEntityMarshaller[Unit],
      valueMarshaller: ToEntityMarshaller[Value],
      multiDocumentToEntityMarshaller: ToEntityMarshaller[MultiDocument[Value]],
      entityToSingleDocumentUnmarshaller: FromEntityUnmarshaller[SingleDocument[Value]],
      unmarshaller: FromEntityUnmarshaller[Value],
      fromStringToKeyUnmarshaller: Unmarshaller[String, Key],
      executionContext: ExecutionContext): Route = {
    pathEnd {
      get {
        parameters(Symbol("page[cursor]").as[Key].?, Symbol("page[limit]").as[Int]) {
          (cursor, limit) =>
            val envelope = store
              .retrievePage(cursor, limit + 1)
              .map { elems =>
                val data =
                  elems
                    .take(limit)
                    .toList
                    .map { case (key, value) => Document.Resource(name, key.toString, value) }
                //val nextCursor = a.keys.lastOption.map(b => ByteVector.encodeAscii(b.toString).right.get.toBase64)
                MultiDocument(data)
              }
            complete(envelope)
        } ~ pass {
          val envelope = store.retrieveAll
            .map { values =>
              val data = values.toList
              MultiDocument(data.map {
                case (key, value) => Document.Resource(name, key.toString, value)
              })
            }
          complete(envelope)
        }
      }
    } ~ path(keyPathMatcher) { id =>
      post {
        entity(as[SingleDocument[Value]]) {
          case SingleDocument(Document.Resource(_, _, entity)) =>
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
