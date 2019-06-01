package com.github.mwegrz.scalautil.akka.stream.scaladsl

import akka.NotUsed
import akka.stream.{ ActorMaterializer, KillSwitches }
import akka.stream.scaladsl.{ BroadcastHub, Flow, Keep, MergeHub, Sink }
import com.github.mwegrz.app.Shutdownable

class BidiFlowHub[A, B](
    aBufferSize: Int = 256,
    aPerProducerBufferSize: Int = 16,
    bBufferSize: Int = 256,
    bPerProducerBufferSize: Int = 16,
    drainA: Boolean = false,
    drainB: Boolean = false
)(implicit actorMaterializer: ActorMaterializer)
    extends Shutdownable {

  private val ((aSink, akillSwitch), aSource) =
    MergeHub
      .source[A](aPerProducerBufferSize)
      .viaMat(KillSwitches.single)(Keep.both)
      .toMat(BroadcastHub.sink[A](aBufferSize))(Keep.both)
      .run()

  private val ((bSink, bkillSwitch), bSource) =
    MergeHub
      .source[B](bPerProducerBufferSize)
      .viaMat(KillSwitches.single)(Keep.both)
      .toMat(BroadcastHub.sink[B](bBufferSize))(Keep.both)
      .run()

  val leftFlow: Flow[A, B, NotUsed] = Flow.fromSinkAndSourceCoupled(aSink, bSource)

  val rightFlow: Flow[B, A, NotUsed] = Flow.fromSinkAndSourceCoupled(bSink, aSource)

  if (drainA) aSource.runWith(Sink.ignore)
  if (drainB) bSource.runWith(Sink.ignore)

  override def shutdown(): Unit = {
    akillSwitch.shutdown()
    bkillSwitch.shutdown()
  }
}
