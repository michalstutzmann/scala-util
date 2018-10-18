package com.github.mwegrz.scalautil.geo

import scodec.bits.ByteVector

import org.gavaghan.geodesy.{ Ellipsoid, GeodeticCalculator, GlobalCoordinates }

object TwoDPosition {
  def fromByteVector(bytes: ByteVector): TwoDPosition = {
    require(bytes.length == 8)
    val latitude = Latitude.fromByteVector(bytes.take(4))
    val longitude = Longitude.fromByteVector(bytes.drop(4))
    TwoDPosition(latitude, longitude)
  }
}

final case class TwoDPosition(latitude: Latitude, longitude: Longitude) {
  def toByteVector: ByteVector = latitude.toByteVector ++ longitude.toByteVector

  def bearingAt(that: TwoDPosition): Double = {
    val calc = new GeodeticCalculator()
    val reference = Ellipsoid.WGS84
    val dest = calc.calculateGeodeticCurve(
      reference,
      new GlobalCoordinates(latitude.value, longitude.value),
      new GlobalCoordinates(that.latitude.value, that.longitude.value))
    dest.getAzimuth
  }

  def distanceTo(that: TwoDPosition): Double = {
    val calc = new GeodeticCalculator()
    val reference = Ellipsoid.WGS84
    val dest = calc.calculateGeodeticCurve(
      reference,
      new GlobalCoordinates(latitude.value, longitude.value),
      new GlobalCoordinates(that.latitude.value, that.longitude.value))
    dest.getEllipsoidalDistance
  }

  def move(bearing: Double, distance: Double): TwoDPosition = {
    val calc = new GeodeticCalculator()
    val reference = Ellipsoid.WGS84
    val dest = calc.calculateEndingGlobalCoordinates(
      reference,
      new GlobalCoordinates(latitude.value, longitude.value),
      bearing,
      distance,
      Array(0.0))
    TwoDPosition(Latitude(dest.getLatitude), Longitude(dest.getLongitude))
  }

  def moveTo(that: TwoDPosition, distance: Double): TwoDPosition = move(bearingAt(that), distance)
}
