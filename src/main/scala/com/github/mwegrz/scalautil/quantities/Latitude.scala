package com.github.mwegrz.scalautil.quantities

object Latitude {
  def fromRadians(radians: Radians): Latitude = Latitude(radians.value.toDegrees)

  def fromDegrees(degrees: Degrees): Latitude = Latitude(degrees.value)
}

final case class Latitude(value: Double) extends AnyVal {
  def toRadians: Radians = Radians(value.toRadians)

  def toDegrees: Degrees = Degrees(value)
}
