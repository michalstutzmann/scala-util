package com.github.mwegrz.scalautil.serialization

import scodec.bits.ByteVector

object Serde {
  implicit val UnitSerde: Serde[Unit] = new Serde[Unit] {
    override def valueToBytes(value: Unit): ByteVector = ByteVector.empty

    override def bytesToValue(binary: ByteVector): Unit = ()
  }
}

trait Serde[Value] {
  def valueToBytes(value: Value): ByteVector
  def bytesToValue(binary: ByteVector): Value
}
