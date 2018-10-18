package com.github.mwegrz.scalautil.geo

import scodec.bits.ByteVector

object ThreeDPosition {
  def fromByteVector(bytes: ByteVector): TwoDPosition = {
    require(bytes.length == 12)
    val latitude = Latitude.fromByteVector(bytes.take(4))
    val longitude = Longitude.fromByteVector(bytes.drop(4))
    TwoDPosition(latitude, longitude)
  }
}

final case class ThreeDPosition(latitude: Latitude, longitude: Longitude, altitude: Altitude) {
  def toByteVector: ByteVector =
    latitude.toByteVector ++ longitude.toByteVector ++ altitude.toByteVector
}
