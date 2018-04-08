package com.github.mwegrz.scalautil

import io.circe.{ KeyDecoder, KeyEncoder, Printer }

package object circe {
  implicit val jsonPrinter: Printer = Printer(
    preserveOrder = true,
    dropNullValues = true,
    indent = ""
  )

  def createKeyEncoder[A](f: A => String): KeyEncoder[A] = new KeyEncoder[A] {
    override def apply(value: A): String = f(value)
  }

  def createKeyDecoder[A](f: String => Option[A]): KeyDecoder[A] = new KeyDecoder[A] {
    override def apply(key: String): Option[A] = f(key)
  }
}
