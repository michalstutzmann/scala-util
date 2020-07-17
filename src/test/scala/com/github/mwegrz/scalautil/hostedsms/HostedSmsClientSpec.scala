package com.github.mwegrz.scalautil.hostedsms

import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import com.github.mwegrz.scalautil.mobile.{ Msisdn, Sms }
import com.github.mwegrz.scalautil.scalatest.TestSpec
import com.typesafe.config.ConfigFactory

import scala.concurrent.Await
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._

class HostedSmsClientSpec extends TestSpec {
  describe("HostedSmsClient") {
    it("should connect and retrieve data") {
      val config = ConfigFactory
        .parseString(
          """|hosted-sms.client {
             |  base-uri = "https://api.hostedsms.pl/SimpleApi"
             |  user-email = ""
             |  password = ""
             |}""".stripMargin
        )
        .withFallback(ConfigFactory.load())

      implicit val actorSystem: ActorSystem = ActorSystem(getClass.getSimpleName)
      implicit val actorMaterializer: ActorMaterializer = ActorMaterializer()
      implicit val client: HostedSmsClient =
        HostedSmsClient(config.getConfig("hosted-sms.client"))
      Await.ready(client.send(Sms(Msisdn(""), "Cleaning", "Test6")), 10.seconds)
    }
  }
}
