package com.github.mwegrz.scalautil.avro4s

import java.io.{ EOFException, InputStream }

import com.github.mwegrz.scalautil.StringVal
import com.sksamuel.avro4s.{ AvroInputStream, FromValue, SchemaFor }
import org.apache.avro.Schema
import org.apache.avro.generic.GenericDatumReader
import org.apache.avro.io.DecoderFactory

import scala.util.Try

class AvroBinaryStringValInputStream[T <: StringVal](
    in: InputStream,
    writerSchema: Option[Schema] = None,
    readerSchema: Option[Schema] = None)(implicit schemaFor: SchemaFor[T], fromValue: FromValue[T])
    extends AvroInputStream[T] {

  private val wSchema = writerSchema.getOrElse(schemaFor())
  private val rSchema = readerSchema.getOrElse(schemaFor())
  private val datumReader = new GenericDatumReader[org.apache.avro.util.Utf8](wSchema, rSchema)
  private val decoder = DecoderFactory.get().binaryDecoder(in, null)

  private val _iter = Iterator
    .continually {
      try {
        datumReader.read(null, decoder)
      } catch {
        case _: EOFException => null
      }
    }
    .takeWhile(_ != null)

  override def iterator: Iterator[T] = new Iterator[T] {
    override def hasNext: Boolean = _iter.hasNext
    override def next(): T = fromValue(_iter.next)
  }

  override def tryIterator: Iterator[Try[T]] = new Iterator[Try[T]] {
    override def hasNext: Boolean = _iter.hasNext
    override def next(): Try[T] = Try(fromValue(_iter.next))
  }

  override def close(): Unit = in.close()
}
