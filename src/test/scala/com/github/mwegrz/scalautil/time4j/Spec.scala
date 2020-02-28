package com.github.mwegrz.scalautil.time4j

import java.time.Instant

import com.github.mwegrz.scalautil.scalatest.TestSpec
import org.scalatest.Matchers

class Spec extends TestSpec with Matchers {
  describe("conversion should work as expected") {
    it("should calculate") {
      Given("an instant")
      val instant = Instant.now()
      When("calculating the containment")
      val gpsMilliseconds = instantToGpsMilliseconds(instant)
      val result = gpsMillisecondsToInstant(gpsMilliseconds)
      Then("the result is correct")
      assert(result == instant)
    }
  }
}
