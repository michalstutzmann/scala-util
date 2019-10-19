package com.github.mwegrz.scalautil.quantities

object Longitude {
  def fromRadians(radians: Radians): Longitude = Longitude(radians.value.toDegrees)

  def fromDegrees(degrees: Degrees): Longitude = Longitude(degrees.value)
}

final case class Longitude(value: Double) extends AnyVal {
  def toRadians: Radians = Radians(value.toRadians)

  def toDegrees: Degrees = Degrees(value)
}
