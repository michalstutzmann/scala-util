package com.github.mwegrz.scalautil.geo

import scodec.bits.ByteVector

object Latitude {
  def fromByteVector(bytes: ByteVector): Latitude = {
    require(bytes.length == 4)
    Latitude(bytes.toInt().toDouble / 100000)
  }

  def fromRadians(value: Double): Latitude = Latitude(value.toDegrees)
}

final case class Latitude(value: Double) extends AnyVal {
  def toByteVector: ByteVector = ByteVector.fromInt((value * 100000).toInt)

  def toRadians: Double = value.toRadians
}