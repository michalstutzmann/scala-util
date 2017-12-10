package com.github.mwegrz.scalautil

import _root_.scodec.bits.{ ByteOrdering, ByteVector }
import _root_.scodec.bits.ByteOrdering.{ BigEndian, LittleEndian }

package object scodec {
  implicit class RichByteVector(byteVector: ByteVector) {
    def toHex(ordering: ByteOrdering): String = {
      val orderedBytes = ordering match {
        case BigEndian    => byteVector
        case LittleEndian => byteVector.reverse
      }
      orderedBytes.toHex
    }
  }
}
