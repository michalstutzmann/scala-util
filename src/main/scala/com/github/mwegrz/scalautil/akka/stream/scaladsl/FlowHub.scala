package com.github.mwegrz.scalautil.akka.stream.scaladsl

import akka.NotUsed
import akka.stream.{ ActorMaterializer, KillSwitches }
import akka.stream.scaladsl.{ BroadcastHub, Flow, Keep, MergeHub, Sink }
import com.github.mwegrz.app.Shutdownable

class FlowHub[A](bufferSize: Int = 256, perProducerBufferSize: Int = 16, drain: Boolean = false)(
    implicit actorMaterializer: ActorMaterializer)
    extends Shutdownable {

  private val ((sink, killSwitch), source) =
    MergeHub
      .source[A](perProducerBufferSize)
      .viaMat(KillSwitches.single)(Keep.both)
      .toMat(BroadcastHub.sink[A](bufferSize))(Keep.both)
      .run()

  val flow: Flow[A, A, NotUsed] = Flow.fromSinkAndSourceCoupled(sink, source)

  if (drain) source.runWith(Sink.ignore)

  override def shutdown(): Unit = killSwitch.shutdown()
}
