package com.github.mwegrz.scalautil

import java.io.{ ByteArrayInputStream, ByteArrayOutputStream }

import com.sksamuel.avro4s._
import org.apache.avro.Schema

package object avro4s {
  implicit class AOps[A: Encoder](c: A) {
    def toAvro(implicit schemaFor: SchemaFor[A]): Array[Byte] = {
      val baos = new ByteArrayOutputStream()
      val os = AvroOutputStream.binary[A].to(baos).build(schemaFor.schema)
      os.write(c)
      os.close()
      baos.toByteArray
    }
  }

  def parseAvro[A: Decoder](
      bytes: Array[Byte],
      writerSchema: Option[Schema] = None,
      readerSchema: Option[Schema] = None)(implicit schemaFor: SchemaFor[A]): A = {
    val defaultSchema = schemaFor.schema
    val in = new ByteArrayInputStream(bytes)
    val input = AvroInputStream
      .binary[A]
      .from(in)
      .build(writerSchema.getOrElse(defaultSchema), readerSchema.getOrElse(defaultSchema))
    input.iterator.toSeq.head
  }
}
