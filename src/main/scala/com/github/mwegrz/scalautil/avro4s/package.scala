package com.github.mwegrz.scalautil

import java.io.{ ByteArrayInputStream, ByteArrayOutputStream }
import com.sksamuel.avro4s._

package object avro4s {
  implicit class AOps[A](c: A) {
    def toAvro(implicit schemaFor: SchemaFor[A], toRecord: ToRecord[A]): Array[Byte] = {
      val baos = new ByteArrayOutputStream()
      val output = AvroOutputStream.binary[A](baos)
      output.write(c)
      output.close()
      baos.toByteArray
    }
  }

  def parseAvro[A](bytes: Array[Byte])(implicit schemaFor: SchemaFor[A], toRecord: FromRecord[A]): A = {
    val in = new ByteArrayInputStream(bytes)
    val input = AvroInputStream.binary[A](in)
    input.iterator.toSeq.head
  }
}
