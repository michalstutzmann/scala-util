package com.github.mwegrz.scalautil.akka.streams

import java.net.InetSocketAddress

import akka.NotUsed
import akka.stream.scaladsl.{Flow, Keep, Sink, Source}
import akka.util.ByteString

package object scaladsl {
  type UdpFlow = Flow[(ByteString, InetSocketAddress), (ByteString, InetSocketAddress), NotUsed]

  implicit class FlowOps[A, B, C](flow: Flow[A, B, C]) {
    def toSource: Source[B, C] = Source.empty.viaMat(flow)(Keep.right)

    def toSink: Sink[A, C] = flow.toMat(Sink.ignore)(Keep.left)
  }
}
