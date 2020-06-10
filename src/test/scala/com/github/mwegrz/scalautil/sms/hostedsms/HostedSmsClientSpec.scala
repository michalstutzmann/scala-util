package com.github.mwegrz.scalautil.sms.hostedsms

import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import com.github.mwegrz.scalautil.scalatest.TestSpec
import com.github.mwegrz.scalautil.sms.{ Msisdn, Sms }
import com.typesafe.config.ConfigFactory

import scala.concurrent.Await
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._

class HostedSmsClientSpec extends TestSpec {
  describe("Position 2D") {
    it("should calculate the bearing at a different 2D position") {
      val config = ConfigFactory.parseString("""base-uri = "https://api.hostedsms.pl/SimpleApi"
          |user-email = ""
          |password = ""
          |""".stripMargin)

      implicit val actorSystem: ActorSystem = ActorSystem("test")
      implicit val actorMaterializer: ActorMaterializer = ActorMaterializer()
      val client = HostedSmsClient(config)

      Await.result(
        client.send(Sms(recipient = Msisdn("48602270659"), sender = "TestowySMS", message = "Test")),
        60.seconds
      )
    }
  }
}
