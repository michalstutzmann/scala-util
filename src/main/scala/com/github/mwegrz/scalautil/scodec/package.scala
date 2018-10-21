package com.github.mwegrz.scalautil

import _root_.scodec.bits.{ ByteOrdering, ByteVector }
import _root_.scodec.bits.ByteOrdering.{ BigEndian, LittleEndian }

import scala.util.Try

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

  def fromHex(hex: String, byteOrdering: ByteOrdering): Try[ByteVector] = Try {
    ByteVector
      .fromHex(hex)
      .map { byteVector =>
        byteOrdering match {
          case BigEndian    => byteVector
          case LittleEndian => byteVector.reverse
        }
      }
      .getOrElse(throw new IllegalArgumentException(s"invalid hex value: $hex"))
  }
}
