package com.github.mwegrz.scalautil.akka.streams.scaladsl

import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.{ Flow, Sink, Source }
import com.github.mwegrz.scalautil.scalatest.TestSpec
import org.scalatest.concurrent.Eventually
import scala.concurrent.duration._

class FlowHubSpec extends TestSpec with Eventually {
  private implicit val actorSystem = ActorSystem()
  private implicit val actorMaterializer = ActorMaterializer()

  describe("MergeBroadcastHub") {
    it("should correctly create flows") {
      Given("a single uplink packet")
      var inMessage = 0
      var outMessage = 0
      val inFlow = {
        val source = Source.tick(0.second, 1.second, 1)
        val sink = Sink.foreach[Int](outMessage = _)
        Flow.fromSinkAndSource(sink, source)
      }
      val hub = new FlowHub(inFlow)

      When("creating and running a flow")
      Source.tick(0.second, 1.second, 1).via(hub.mergeBroadcastFlow).runForeach(inMessage = _)

      Then("traffic flows")
      eventually(timeout(scaled(5.seconds))) {
        assert(inMessage > 0 && outMessage > 0)
      }
    }
  }
}
