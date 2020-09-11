package com.github.mwegrz.scalautil.disruptivetechnologies

import com.github.mwegrz.scalautil.scalatest.TestSpec
import io.circe.parser._

class EventSpec extends TestSpec {
  describe("Event") {
    it("should parse and decode") {
      Given("raw event")

      val eventJsonString =
        """{
          |      "eventId": "bsl3lo4ip0ro05965m3g",
          |      "targetName": "projects/brku1p94jplfqcpoja6g/devices/bhekp869j9fg00ecpct0",
          |      "eventType": "temperature",
          |      "data": {
          |        "temperature": {
          |          "value": 23.55,
          |          "updateTime": "2020-08-05T04:51:43.201000Z"
          |        }
          |      },
          |      "timestamp": "2020-08-05T04:51:43.201000Z"
          |    }
          |""".stripMargin
      When("parsing and decoding")
      println(parse(eventJsonString))
      val result = parse(eventJsonString).map(_.as[Event])
      Then("the result is correct")
      println(result)
    }
  }
}
