package com.github.mwegrz.scalautil

import java.io.{ ByteArrayInputStream, ByteArrayOutputStream }

import com.sksamuel.avro4s._
import org.apache.avro.Schema
import org.apache.avro.io.EncoderFactory
import _root_.scodec.bits.ByteVector

import scala.util.Try

package object avro4s {
  implicit class AOps[A: SchemaFor: Encoder](underlaying: A) {
    def toAvro: Array[Byte] = {
      val baos = new ByteArrayOutputStream()
      val os = new WrapperAvroOutputStream[A](
        baos,
        implicitly[SchemaFor[A]].schema,
        EncoderFactory.get().binaryEncoder(baos, null)
      )
      os.write(underlaying)
      os.close()
      baos.toByteArray
    }
  }

  def fromAvro[A: SchemaFor: Decoder](
      bytes: Array[Byte],
      writerSchema: Option[Schema] = None,
      readerSchema: Option[Schema] = None
  ): Try[A] = Try {
    val defaultSchema = implicitly[SchemaFor[A]].schema
    val resolvedWriterSchema = writerSchema.getOrElse(defaultSchema)
    val resolvedReaderSchema = readerSchema.getOrElse(defaultSchema)

    val in = new ByteArrayInputStream(bytes)

    val input = new WrapperAvroInputStream[A](in, resolvedWriterSchema, resolvedReaderSchema)

    if (input.iterator.hasNext) {
      input.iterator.toSeq.head
    } else {
      throw new IllegalArgumentException(
        s"Cannot decode: bytes: 0x${ByteVector(bytes).toHex}: " +
          s"writer schema: ${resolvedWriterSchema.toString(false)} " +
          s"reader schema: ${resolvedReaderSchema.toString(false)}"
      )
    }
  }
}
