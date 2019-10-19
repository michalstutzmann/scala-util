package com.github.mwegrz.scalautil.geo

import com.github.mwegrz.scalautil.quantities.{ Latitude, Longitude }
import org.gavaghan.geodesy.{ Ellipsoid, GeodeticCalculator, GlobalCoordinates }

final case class TwoDPosition(lat: Latitude, long: Longitude) {
  def bearingAt(that: TwoDPosition): Double = {
    val calc = new GeodeticCalculator()
    val reference = Ellipsoid.WGS84
    val dest = calc.calculateGeodeticCurve(
      reference,
      new GlobalCoordinates(lat.value, long.value),
      new GlobalCoordinates(that.lat.value, that.long.value)
    )
    dest.getAzimuth
  }

  def distanceTo(that: TwoDPosition): Double = {
    val calc = new GeodeticCalculator()
    val reference = Ellipsoid.WGS84
    val dest = calc.calculateGeodeticCurve(
      reference,
      new GlobalCoordinates(lat.value, long.value),
      new GlobalCoordinates(that.lat.value, that.long.value)
    )
    dest.getEllipsoidalDistance
  }

  def move(bearing: Double, distance: Double): TwoDPosition = {
    val calc = new GeodeticCalculator()
    val reference = Ellipsoid.WGS84
    val dest = calc.calculateEndingGlobalCoordinates(
      reference,
      new GlobalCoordinates(lat.value, long.value),
      bearing,
      distance,
      Array(0.0)
    )
    TwoDPosition(Latitude(dest.getLatitude), Longitude(dest.getLongitude))
  }

  def moveTo(that: TwoDPosition, distance: Double): TwoDPosition = move(bearingAt(that), distance)
}
