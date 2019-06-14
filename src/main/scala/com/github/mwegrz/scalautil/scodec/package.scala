package com.github.mwegrz.scalautil

import _root_.scodec.bits.{ ByteOrdering, ByteVector }
import _root_.scodec.bits.ByteOrdering.{ BigEndian, LittleEndian }
import _root_.scodec.{ Attempt, Codec }

import scala.util.Try

package object scodec {
  implicit class CodecOps[A](underlying: Codec[A]) {
    def validWiden[B >: A]: Codec[B] =
      underlying
        .widen[B](a => a.asInstanceOf[B], value => Attempt.successful(value.asInstanceOf[A]))
  }

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
