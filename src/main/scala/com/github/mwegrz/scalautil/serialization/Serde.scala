package com.github.mwegrz.scalautil.serialization

trait Serde[Value] {
  def valueToBinary(value: Value): Array[Byte]
  def binaryToValue(binary: Array[Byte]): Value
}
