package com.github.mwegrz.scalautil.akka.streams.scaladsl

import akka.NotUsed
import akka.stream.{ ActorMaterializer, ClosedShape, KillSwitches }
import akka.stream.scaladsl.{ BroadcastHub, Flow, GraphDSL, Keep, MergeHub, RunnableGraph }
import com.github.mwegrz.app.Shutdownable

class FlowHubOld[A, B, _](flow: Flow[A, B, _], bufferSize: Int = 256, perProducerBufferSize: Int = 16)(
    implicit actorMaterializer: ActorMaterializer)
    extends Shutdownable {
  private val broadcastHub = BroadcastHub.sink[B](bufferSize)
  private val mergeHub = MergeHub.source[A](perProducerBufferSize)

  private val (killSwitch, source, sink) = RunnableGraph
    .fromGraph(GraphDSL.create(flow.viaMat(KillSwitches.single)(Keep.right), broadcastHub, mergeHub)((_, _, _)) {
      implicit b => (i, bh, mh) =>
        import GraphDSL.Implicits._

        mh.out ~> i.in
        bh.in <~ i.out

        ClosedShape
    })
    .run()

  val mergeBroadcastFlow: Flow[A, B, NotUsed] = Flow.fromSinkAndSource(sink, source)

  override def shutdown(): Unit = killSwitch.shutdown()
}
