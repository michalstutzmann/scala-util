package com.github.mwegrz.scalautil.akka.streams.scaladsl

import akka.stream.KillSwitches
import akka.stream.scaladsl.{ BroadcastHub, Flow, Keep, MergeHub }
import com.github.mwegrz.app.Shutdownable
import com.github.mwegrz.scalautil.akka.Akka

class MergeBroadcastHub[A, B, C](viaFlow: Flow[A, B, C])(implicit akka: Akka) extends Shutdownable {
  import akka.actorMaterializer

  private val broadcastHub = BroadcastHub.sink[B]

  private val mergeHub = MergeHub.source[A]

  private val (mergeBroadcastFlow, killSwitch) = mergeHub
    .viaMat(KillSwitches.single)(Keep.both)
    .viaMat(viaFlow)(Keep.both)
    .toMat(broadcastHub)(Keep.both)
    .mapMaterializedValue(a => (Flow.fromSinkAndSourceMat(a._1._1._1, a._2)((_, _) => a._1._2), a._1._1._2))
    .run()

  def flow: Flow[A, B, C] = mergeBroadcastFlow

  override def shutdown(): Unit = killSwitch.shutdown()
}
