package com.github.mwegrz.scalautil.serialization

import scodec.bits.ByteVector

object Serde {
  implicit val UnitSerde: Serde[Unit] = new Serde[Unit] {
    override def valueToBytes(value: Unit): ByteVector = ByteVector.empty

    override def bytesToValue(binary: ByteVector): Unit = ()
  }

  implicit val StringSerde: Serde[String] = new Serde[String] {
    override def valueToBytes(value: String): ByteVector = ByteVector.encodeAscii(value).toTry.get

    override def bytesToValue(binary: ByteVector): String = binary.decodeAscii.toTry.get
  }
}

trait Serde[Value] {
  def valueToBytes(value: Value): ByteVector
  def bytesToValue(binary: ByteVector): Value
}
