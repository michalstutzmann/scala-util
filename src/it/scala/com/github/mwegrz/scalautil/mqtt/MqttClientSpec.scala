package com.github.mwegrz.scalautil.mqtt

import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.{Keep, Sink, Source}
import com.github.mwegrz.scalautil.mqtt.MqttClient.Qos
import com.github.mwegrz.scalautil.scalatest.TestSpec
import com.typesafe.config.ConfigFactory
import org.scalatest.concurrent.ScalaFutures
import com.github.mwegrz.scalautil.akka.stream.scaladsl._
import org.scalatest.BeforeAndAfterAll

import scala.concurrent.ExecutionContext
import scala.concurrent.duration._

class MqttClientSpec extends TestSpec with ScalaFutures with BeforeAndAfterAll {
  private implicit var actorSystem: ActorSystem = _
  private implicit var actorMaterializer: ActorMaterializer = _
  private implicit var executionContext: ExecutionContext = _

  private var client: MqttClient = _

  override def beforeAll(): Unit = {
    actorSystem = ActorSystem()
    actorMaterializer = ActorMaterializer()
    executionContext = actorSystem.dispatcher
    client = MqttClient(ConfigFactory.load().getConfig("mqtt-client"))
  }

  override def afterAll(): Unit = actorSystem.terminate()

  describe("MQTT client") {
    it("should send and receive the same messages") {
      Given(s"sink and source and messages")
      val topic = "default"
      val sink = client
        .createFlow[String, String](topics = Map.empty, bufferSize = 1, Qos.AtMostOnce)(_.getBytes, new String(_))
        .toSink
      val source = client
        .createFlow[String, String](topics = Map(topic -> Qos.AtMostOnce), bufferSize = 1, Qos.AtMostOnce)(
          _.getBytes,
          new String(_)
        )
        .toKillableSource
      val messages = List((topic, "test1"), (topic, "test2"), (topic, "test3"))

      val ((killSwitch, connected), received) = source.take(messages.length).toMat(Sink.collection)(Keep.both).run()

      When("sending the messages")
      connected.foreach( _ => Source(messages).to(sink).run())

      Then("same messages are received")
      whenReady(received, timeout(scaled(5.seconds))) { value =>
        assert(value == messages)
      }
      killSwitch.shutdown()
    }
  }
}
