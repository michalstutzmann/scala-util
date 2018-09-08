package com.github.mwegrz.scalautil.geo

import scodec.bits.ByteVector

import org.gavaghan.geodesy.{ Ellipsoid, GeodeticCalculator, GlobalCoordinates }

object Position2D {
  def fromByteVector(bytes: ByteVector): Position2D = {
    require(bytes.length == 8)
    val latitude = Latitude.fromByteVector(bytes.take(4))
    val longitude = Longitude.fromByteVector(bytes.drop(4))
    Position2D(latitude, longitude)
  }
}

final case class Position2D(latitude: Latitude, longitude: Longitude) {
  def toByteVector: ByteVector = latitude.toByteVector ++ longitude.toByteVector

  def bearingAt(that: Position2D): Double = {
    val calc = new GeodeticCalculator()
    val reference = Ellipsoid.WGS84
    val dest = calc.calculateGeodeticCurve(reference,
                                           new GlobalCoordinates(latitude.value, longitude.value),
                                           new GlobalCoordinates(that.latitude.value, that.longitude.value))
    dest.getAzimuth
  }

  def distanceTo(that: Position2D): Double = {
    val calc = new GeodeticCalculator()
    val reference = Ellipsoid.WGS84
    val dest = calc.calculateGeodeticCurve(reference,
                                           new GlobalCoordinates(latitude.value, longitude.value),
                                           new GlobalCoordinates(that.latitude.value, that.longitude.value))
    dest.getEllipsoidalDistance
  }

  def move(bearing: Double, distance: Double): Position2D = {
    val calc = new GeodeticCalculator()
    val reference = Ellipsoid.WGS84
    val dest = calc.calculateEndingGlobalCoordinates(reference,
                                                     new GlobalCoordinates(latitude.value, longitude.value),
                                                     bearing,
                                                     distance,
                                                     Array(0.0))
    Position2D(Latitude(dest.getLatitude), Longitude(dest.getLongitude))
  }

  def moveTo(that: Position2D, distance: Double): Position2D = move(bearingAt(that), distance)
}
