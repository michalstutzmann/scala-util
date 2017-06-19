package com.github.mwegrz.scalautil.akka.streams

import java.net._

import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.{ Keep, Sink, Source }
import akka.util.ByteString
import com.github.mwegrz.scalautil.akka.streams.scaladsl.UdpFlow
import com.github.mwegrz.scalautil.scalatest.UnitSpec
import org.scalatest.concurrent.Eventually

import scala.concurrent.duration._

class UdpFlowStageSpec extends UnitSpec with Eventually {
  private val loopbackAddr = new InetSocketAddress(InetAddress.getLoopbackAddress, new ServerSocket(0).getLocalPort)

  private implicit val actorSystem = ActorSystem()
  private implicit val actorMaterializer = ActorMaterializer()

  describe("UDP packets") {
    it("should be sent and received") {
      val NumOfPackets = 3

      Given(s"$NumOfPackets UDP packets and a UDP flow")
      val udpFlow = UdpFlow(loopbackAddr, 10, Sink.ignore)
      val outPackets = List.fill(NumOfPackets)((ByteString("test"), loopbackAddr))
      var inPackets = List.empty[(ByteString, InetSocketAddress)]
      val source =
        Source(outPackets).concatMat(Source.maybe[(ByteString, InetSocketAddress)])(Keep.right).via(udpFlow)

      When("sending packets")
      Then("same packets are received")
      val streamDone = source.toMat(Sink.foreach(p => inPackets = p :: inPackets))(Keep.left).run()

      eventually(timeout(scaled(5.seconds))) {
        assert(inPackets == outPackets)
      }

      streamDone.success(None)
    }
  }
}
