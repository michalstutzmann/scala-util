package com.github.mwegrz.scalautil.akka.streams

import akka.stream.scaladsl.{Flow, Keep, Sink, Source}

package object scaladsl {
  implicit class FlowOps[A, B, C](flow: Flow[A, B, C]) {
    def toSource: Source[B, C] = Source.empty.viaMat(flow)(Keep.right)

    def toSink: Sink[A, C] = flow.toMat(Sink.ignore)(Keep.left)
  }
}
