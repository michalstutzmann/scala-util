package com.github.mwegrz.scalautil.geo

import scodec.bits.ByteVector

object Longitude {
  def fromByteVector(bytes: ByteVector): Longitude = {
    require(bytes.length == 4)
    Longitude(bytes.toInt().toFloat / 10000000)
  }

  def fromRadians(value: Double): Longitude = Longitude(value.toDegrees)
}

final case class Longitude(value: Double) extends AnyVal {
  def toByteVector: ByteVector = ByteVector.fromInt((value * 10000000).toInt)

  def toRadians: Double = value.toRadians
}
