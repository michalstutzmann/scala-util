package com.github.mwegrz.scalautil.akka.http.server.directives

import akka.http.scaladsl.model.{ HttpRequest, HttpResponse, StatusCodes }
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.RouteResult.Complete
import akka.http.scaladsl.server.{ Directive0, RouteResult }
import akka.stream.scaladsl.Flow
import akka.util.ByteString

import scala.concurrent.ExecutionContext
import scala.util.{ Failure, Success, Try }

// Source: https://gist.githubusercontent.com/adamw/e5e391f49153aac37a57fab795896f1a/raw/0cec4267196275d2a29db938ee1c517f58c08b03/directive2.scala
object AroundDirectives {

  val timeoutResponse = HttpResponse(
    StatusCodes.NetworkReadTimeout,
    entity = "Unable to serve response within time limit."
  )

  def aroundRequest(
      onRequest: HttpRequest => Try[RouteResult] => Unit
  )(implicit ec: ExecutionContext): Directive0 = {
    extractRequestContext.flatMap { ctx =>
      {
        val onDone = onRequest(ctx.request)
        mapInnerRoute { inner =>
          withRequestTimeoutResponse(_ => {
            onDone(Success(Complete(timeoutResponse)))
            timeoutResponse
          }) {
            inner.andThen { resultFuture =>
              resultFuture
                .map {
                  case c @ Complete(response) =>
                    Complete(response.mapEntity { entity =>
                      if (entity.isKnownEmpty()) {
                        onDone(Success(c))
                        entity
                      } else {
                        // On an empty entity, `transformDataBytes` unsets `isKnownEmpty`.
                        // Call onDone right away, since there's no significant amount of
                        // data to send, anyway.
                        entity.transformDataBytes(Flow[ByteString].watchTermination() {
                          case (m, f) =>
                            f.map(_ => c).onComplete(onDone)
                            m
                        })
                      }
                    })
                  case other =>
                    onDone(Success(other))
                    other
                }
                .andThen { // skip this if you use akka.http.scaladsl.server.handleExceptions, put onDone there
                  case Failure(ex) =>
                    onDone(Failure(ex))
                }
            }
          }
        }
      }
    }
  }
}
