package com.github.mwegrz.scalautil.disruptivetechnologies

import com.github.mwegrz.scalautil.scalatest.TestSpec
import io.circe._
import io.circe.parser._

class EventSpec extends TestSpec {
  describe("Position 2D") {
    it("should calculate the bearing at a different 2D position") {
      Given("two 2D positions")

      val eventJsonString =
        """{
          |  "eventId":"br6fg4gpqua3n832go3g",
          |  "targetName":"projects/bj19qhkcl5b000823ld0/devices/bhekm69j9bp0009ogvp0",
          |  "eventType":"networkStatus",
          |  "data":{
          |    "networkStatus":{
          |       "signalStrength":35,
          |       "updateTime":"2020-05-26T11:05:54.311000Z",
          |       "cloudConnectors":[{"id":"bh46tg4c0000br7i7ds0","signalStrength":35}],
          |       "transmissionMode":"LOW_POWER_STANDARD_MODE"
          |     }
          |   }
          |}""".stripMargin
      When("parsing and decoding")
      val result = parse(eventJsonString).map(_.as[Event])
      Then("the result is correct")
      println(result)
    }
  }
}
