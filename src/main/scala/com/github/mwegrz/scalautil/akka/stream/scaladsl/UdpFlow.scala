package com.github.mwegrz.scalautil.akka.stream.scaladsl

import java.net.InetSocketAddress

import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import akka.Done
import akka.stream.scaladsl.{ Flow, Sink }
import akka.util.ByteString
import com.github.mwegrz.scalautil.akka.stream.UdpFlowStage

import scala.concurrent.Future

object UdpFlow {
  def apply(localAddr: InetSocketAddress,
            maxBufferSize: Int,
            dropped: Sink[(ByteString, InetSocketAddress), Future[Done]])(
      implicit actorSystem: ActorSystem,
      actorMaterializer: ActorMaterializer): UdpFlow =
    Flow.fromGraph(new UdpFlowStage(localAddr, maxBufferSize, dropped))
}
