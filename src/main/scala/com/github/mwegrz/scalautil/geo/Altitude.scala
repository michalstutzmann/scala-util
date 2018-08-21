package com.github.mwegrz.scalautil.geo

import scodec.bits.ByteVector

object Altitude {
  def fromByteVector(bytes: ByteVector): Altitude = {
    require(bytes.length == 4)
    Altitude(bytes.toInt().toFloat / 100000)
  }
}

final case class Altitude(value: Double) extends AnyVal {
  def toByteVector: ByteVector = ByteVector.fromInt((value * 100000).toInt)
}
