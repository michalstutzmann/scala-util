package com.github.mwegrz.scalautil.avro4s

import java.io.{ EOFException, InputStream }

import com.sksamuel.avro4s.{ AvroInputStream, Decoder, FieldMapper }
import org.apache.avro.Schema
import org.apache.avro.generic.{ GenericData, GenericDatumReader, GenericRecord }
import org.apache.avro.io.DecoderFactory
import org.apache.avro.util.Utf8

import scala.util.Try

class WrapperAvroInputStream[T](
    in: InputStream,
    writerSchema: Schema,
    readerSchema: Schema,
    fieldMapper: FieldMapper
)(
    implicit decoder: Decoder[T]
) extends AvroInputStream[T] {

  private val datumReader = readerSchema.getType match {
    case Schema.Type.STRING =>
      new GenericDatumReader[Utf8](writerSchema, readerSchema, new GenericData)
    case _ => new GenericDatumReader[GenericRecord](writerSchema, readerSchema, new GenericData)
  }

  private val avroDecoder = DecoderFactory.get().binaryDecoder(in, null)

  private val _iter = Iterator
    .continually {
      try {
        datumReader.read(null, avroDecoder)
      } catch {
        case _: EOFException => null
      }
    }
    .takeWhile(_ != null)

  /**
    * Returns an iterator for the values of T in the stream.
    */
  override def iterator: Iterator[T] = new Iterator[T] {
    override def hasNext: Boolean = _iter.hasNext
    override def next(): T = decoder.decode(_iter.next, readerSchema, fieldMapper)
  }

  /**
    * Returns an iterator for values of Try[T], so that any
    * decoding issues are wrapped.
    */
  override def tryIterator: Iterator[Try[T]] = new Iterator[Try[T]] {
    override def hasNext: Boolean = _iter.hasNext
    override def next(): Try[T] = Try(decoder.decode(_iter.next, readerSchema, fieldMapper))
  }

  override def close(): Unit = in.close()
}
