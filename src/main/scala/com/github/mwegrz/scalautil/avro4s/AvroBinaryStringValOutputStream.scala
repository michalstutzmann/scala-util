package com.github.mwegrz.scalautil.avro4s

import java.io.OutputStream

import com.github.mwegrz.scalautil.StringVal
import com.sksamuel.avro4s.{ AvroOutputStream, SchemaFor, ToValue }
import org.apache.avro.generic.GenericDatumWriter
import org.apache.avro.io.EncoderFactory
import org.apache.avro.util.Utf8

case class AvroBinaryStringValOutputStream[T <: StringVal](os: OutputStream)(implicit schemaFor: SchemaFor[T],
                                                                             toValue: ToValue[T])
    extends AvroOutputStream[T] {

  private val schema = schemaFor()
  protected val datumWriter = new GenericDatumWriter[org.apache.avro.util.Utf8](schema)
  private val encoder = EncoderFactory.get.jsonEncoder(schema, os)

  override def close(): Unit = {
    encoder.flush()
    os.close()
  }

  override def write(t: T): Unit = datumWriter.write(new Utf8(toValue(t).asInstanceOf[String]), encoder)
  override def fSync(): Unit = {}
  override def flush(): Unit = encoder.flush()
}
