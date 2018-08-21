package com.github.mwegrz.scalautil.geo

import scodec.bits.ByteVector

import scala.math._

object Position2D {
  private val R = 6371000 // Earth radius in metres

  def fromByteVector(bytes: ByteVector): Position2D = {
    require(bytes.length == 8)
    val latitude = Latitude.fromByteVector(bytes.take(4))
    val longitude = Longitude.fromByteVector(bytes.drop(4))
    Position2D(latitude, longitude)
  }
}

final case class Position2D(latitude: Latitude, longitude: Longitude) {
  import Position2D._

  def toByteVector: ByteVector = latitude.toByteVector ++ longitude.toByteVector

  def bearingAt(that: Position2D): Double = {
    val φ1 = latitude.toRadians
    val φ2 = that.latitude.toRadians
    val λ1 = longitude.toRadians
    val λ2 = that.longitude.toRadians
    val Δφ = φ2 - φ1
    val Δλ = λ2 - λ1

    val y = sin(Δφ) * cos(λ2)
    val x = cos(φ1) * sin(φ2) - sin(φ1) * cos(φ2) * cos(Δλ)

    (360 + atan2(y, x).toDegrees) % 360
  }

  def distanceTo(that: Position2D): Double = {
    val φ1 = latitude.toRadians
    val φ2 = that.latitude.toRadians
    val λ1 = longitude.toRadians
    val λ2 = that.longitude.toRadians
    val Δφ = φ2 - φ1
    val Δλ = λ2 - λ1

    val a = sin(Δφ / 2) * sin(Δφ / 2) + cos(φ1) * cos(φ2) * sin(Δλ / 2) * sin(Δλ / 2)
    val c = 2 * atan2(sqrt(a), sqrt(1 - a))

    R * c
  }

  def move(bearing: Double, distance: Double): Position2D = {
    val φ1 = latitude.toRadians
    val λ1 = longitude.toRadians

    val φ2 = asin(sin(φ1) * cos(distance / R) + cos(φ1) * sin(distance / R) * cos(bearing.toRadians))
    val λ2 = λ1 + atan2(sin(bearing.toRadians) * sin(distance / R) * cos(φ1), cos(distance / R) - sin(φ1) * sin(φ2))

    Position2D(Latitude(φ2.toDegrees), Longitude(((λ2 + 3 * Pi) % (2 * Pi) - Pi).toDegrees))
  }

  def moveTo(that: Position2D, distance: Double): Position2D = move(bearingAt(that), distance)
}
