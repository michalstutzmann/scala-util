package com.github.mwegrz.scalautil.geo

import com.github.mwegrz.scalautil.quantities.{ Latitude, Longitude, Meters }
import com.github.mwegrz.scalautil.scalatest.TestSpec
import org.scalatest.Matchers

class TwoDShapeSpec extends TestSpec with Matchers {
  describe("2D shape") {
    it("should calculate containment") {
      Given("a circle and a 2D position")
      val circle = Circle(TwoDPosition(Latitude(53.011939), Longitude(18.611414)), Meters(12))
      val position = TwoDPosition(Latitude(53.01192), Longitude(18.61158))
      When("calculating the containment")
      val result = circle.contains(position)
      Then("the result is correct")
      assert(result)
    }
  }
}
