package com.github.mwegrz.scalautil

import java.io.{ ByteArrayInputStream, ByteArrayOutputStream }

import com.sksamuel.avro4s._
import org.apache.avro.Schema
import org.apache.avro.io.EncoderFactory

package object avro4s {
  implicit class AOps[A: Encoder](underlaying: A) {
    def toAvro(implicit schemaFor: SchemaFor[A]): Array[Byte] = {
      val baos = new ByteArrayOutputStream()
      val os = new WrapperAvroOutputStream[A](baos,
                                              schemaFor.schema,
                                              EncoderFactory.get().binaryEncoder(baos, null))
      os.write(underlaying)
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

    val input = new WrapperAvroInputStream[A](in,
                                              writerSchema.getOrElse(defaultSchema),
                                              readerSchema.getOrElse(defaultSchema))

    input.iterator.toSeq.head
  }
}
