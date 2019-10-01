package com.github.mwegrz.scalautil.mqtt

import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.{ Keep, Sink, Source }
import com.github.mwegrz.scalautil.mqtt.MqttClient.Qos
import com.github.mwegrz.scalautil.scalatest.TestSpec
import com.typesafe.config.ConfigFactory
import org.scalatest.concurrent.ScalaFutures
import com.github.mwegrz.scalautil.akka.stream.scaladsl._

import scala.concurrent.ExecutionContext
import scala.concurrent.duration._

class MqttClientSpec extends TestSpec with ScalaFutures {
  private implicit val actorSystem: ActorSystem = ActorSystem()
  private implicit val actorMaterializer: ActorMaterializer = ActorMaterializer()
  private implicit val executionContext: ExecutionContext = actorSystem.dispatcher

  private val client = MqttClient(ConfigFactory.load().getConfig("mqtt-client"))

  describe("MQTT client") {
    it("should send and receive the same message") {
      Given(s"sink and source and a message")
      val topic = "default"
      val sink = client
        .createFlow[String, String](topics = Map.empty, bufferSize = 8, Qos.AtLeastOnce)(_.getBytes, new String(_))
        .toSink
      val source = client
        .createFlow[String, String](topics = Map(topic -> Qos.AtLeastOnce), bufferSize = 8, Qos.AtLeastOnce)(
          _.getBytes,
          new String(_)
        )
        .toKillableSource
      val ((killSwitch, _), received) = source.toMat(Sink.head)(Keep.both).run()
      val message = "test"

      When("sending the message")
      Source.single((topic, message)).to(sink).run()

      Then("same message is received")
      whenReady(received, timeout(scaled(5.seconds))) { value =>
        assert(value == (topic, message))
      }
      killSwitch.shutdown()
    }
  }
}
