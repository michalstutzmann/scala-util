package com.github.mwegrz.scalautil.geo

import scodec.bits.ByteVector

object Position3D {
  def fromByteVector(bytes: ByteVector): Position2D = {
    require(bytes.length == 12)
    val latitude = Latitude.fromByteVector(bytes.take(4))
    val longitude = Longitude.fromByteVector(bytes.drop(4))
    Position2D(latitude, longitude)
  }
}

final case class Position3D(latitude: Latitude, longitude: Longitude, altitude: Altitude) {
  def toByteVector: ByteVector =
    latitude.toByteVector ++ longitude.toByteVector ++ altitude.toByteVector
}
