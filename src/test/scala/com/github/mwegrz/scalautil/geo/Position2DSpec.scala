package com.github.mwegrz.scalautil.geo

import com.github.mwegrz.scalautil.scalatest.TestSpec
import org.scalatest.Matchers

class Position2DSpec extends TestSpec with Matchers {
  describe("Position 2D") {
    it("should calculate the bearing at a different 2D position") {
      Given("two 2D positions")
      val position0 = Position2D(Latitude(0.0), Longitude(0.0))
      val position1 = Position2D(Latitude(0.01029153), Longitude(0.01022263))
      When("calculating the bearing")
      val result = position0.bearingAt(position1)
      Then("the result is correct")
      assert(result == 44.99998668183908)
    }

    it("should move in direction of a different 2D position by a distance") {
      Given("two 2D positions")
      val position0 = Position2D(Latitude(0.0), Longitude(0.0))
      val position1 = Position2D(Latitude(0.0063591640976794015), Longitude(0.0063591640356363225))
      When("calculating the bearing")
      val result = position0.moveTo(position1, 1000)
      Then("the result is correct")
      assert(result == Position2D(Latitude(0.00637334530333078), Longitude(0.00637334524131461)))
    }
  }
}
