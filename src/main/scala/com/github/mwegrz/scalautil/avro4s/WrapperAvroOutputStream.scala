package com.github.mwegrz.scalautil.avro4s

import java.io.OutputStream

import com.sksamuel.avro4s.{ AvroOutputStream, Encoder, FieldMapper }
import org.apache.avro.Schema
import org.apache.avro.generic.{ GenericDatumWriter, GenericRecord }
import org.apache.avro.util.Utf8

class WrapperAvroOutputStream[T](
    os: OutputStream,
    schema: Schema,
    serializer: org.apache.avro.io.Encoder,
    fieldMapper: FieldMapper
)(implicit encoder: Encoder[T])
    extends AvroOutputStream[T] {

  override def close(): Unit = {
    flush()
    os.close()
  }

  override def write(t: T): Unit =
    schema.getType match {
      case Schema.Type.STRING =>
        val datumWriter = new GenericDatumWriter[org.apache.avro.util.Utf8](schema)
        val record = encoder.encode(t, schema, fieldMapper).asInstanceOf[Utf8]
        datumWriter.write(record, serializer)
      case _ =>
        val datumWriter = new GenericDatumWriter[GenericRecord](schema)
        val record = encoder.encode(t, schema, fieldMapper).asInstanceOf[GenericRecord]
        datumWriter.write(record, serializer)
    }

  override def flush(): Unit = serializer.flush()
  override def fSync(): Unit = ()
}
