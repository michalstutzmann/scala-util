package com.github.mwegrz.scalautil.disruptivetechnologies

import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import com.github.mwegrz.scalautil.scalatest.TestSpec
import com.github.mwegrz.scalautil.sse.SseClient
import com.typesafe.config.ConfigFactory
import com.github.mwegrz.scalautil.ConfigOps
import com.github.mwegrz.scalautil.akka.stream.scaladsl.RestartPolicy

import scala.concurrent.Await
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._
import scala.util.Try

class DisruptiveTechnologiesClientSpec extends TestSpec {
  describe("DisruptiveTechnologiesClient") {
    it("should connect and retrieve data") {
      val config = ConfigFactory
        .parseString(
          """|disruptive-technologies-oauth2-client {
             |  service-account-email = "br2j7dj24te000b24tkg@bj19qhkcl5b000823ld0.serviceaccount.d21s.com"
             |  service-account-key-id = "br2j93b24te000b24tlg"
             |  service-account-secret = "57c0a0b3f62144f9bfb7559ac75b8052"
             |}""".stripMargin
        )
        .withFallback(ConfigFactory.load())

      implicit val actorSystem: ActorSystem = ActorSystem(getClass.getSimpleName)
      implicit val actorMaterializer: ActorMaterializer = ActorMaterializer()
      implicit val restartPolicy: RestartPolicy =
        RestartPolicy.fromConfig(config.getConfig("akka.stream.restart-policy"))
      implicit val sseClient: SseClient = SseClient(config.getConfig("sse-client"))
      implicit val oauth2Client: DisruptiveTechnologiesOauth2Client =
        DisruptiveTechnologiesOauth2Client(config.getConfig("disruptive-technologies-oauth2-client"))
      val client = DisruptiveTechnologiesClient(config.getConfig("disruptive-technologies-client"))

      Await.result(
        client.liveEventSource(ProjectId("bj19qhkcl5b000823ld0")).runFold(List.empty[Event]) { (a, b) =>
          println(b)
          b :: a
        },
        360.seconds
      )
      //Thread.sleep(100000)
      /*Await.result(
        client
          .eventHistory(ProjectId("bj19qhkcl5b000823ld0"), DeviceId("bhekm69j9bp0009ogvp0"), None, None, None, None)
          .map(a => println(a.events)),
        10.seconds
      )*/
    }
  }
}
