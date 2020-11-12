package com.github.mwegrz.scalautil.eltin

import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import akka.util.Timeout
import com.github.mwegrz.scalautil.scalatest.TestSpec
import com.typesafe.config.ConfigFactory

import scala.concurrent.Await
import scala.concurrent.duration._
import scala.concurrent.ExecutionContext.Implicits.global

class EltinClientSpec extends TestSpec {
  describe("EltinClient") {
    it("should connect and retrieve data") {
      val config = ConfigFactory
        .parseString(
          """|eltin.client {
             |  host = "localhost"
             |  port = 40001
             |}""".stripMargin
        )
        .withFallback(ConfigFactory.load())

      implicit val actorSystem: ActorSystem = ActorSystem(getClass.getSimpleName)
      implicit val actorMaterializer: ActorMaterializer = ActorMaterializer()
      implicit val timeout: Timeout = Timeout(10.seconds)
      implicit val client: EltinClient = EltinClient(config.getConfig("eltin.client"))
      val result = client.set(
        Map(DisplayId(1) -> DisplayValue(3, 0), DisplayId(2) -> DisplayValue(2, 0))
      )
      Await.ready(result, 60.seconds)
      println(result)
    }
  }
}
