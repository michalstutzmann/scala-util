package com.github.mwegrz.scalautil.avro4s

import java.time.Instant

import com.sksamuel.avro4s.{ FromValue, ToSchema, ToValue }
import org.apache.avro.Schema
import org.apache.avro.Schema.Field
import scodec.bits.ByteVector

object codecs {
  // Byte Vector
  implicit object ByteVectorToSchema extends ToSchema[ByteVector] {
    override val schema: Schema = Schema.create(Schema.Type.BYTES)
  }

  implicit object ByteVectorToValue extends ToValue[ByteVector] {
    override def apply(value: ByteVector): Array[Byte] = value.toArray
  }

  implicit object ByteVectorFromValue extends FromValue[ByteVector] {
    override def apply(value: Any, field: Field): ByteVector = ByteVector(value.asInstanceOf[Array[Byte]])
  }

  // Instant
  implicit object InstantToSchema extends ToSchema[Instant] {
    override val schema: Schema = Schema.create(Schema.Type.LONG)
  }

  implicit object InstantToValue extends ToValue[Instant] {
    override def apply(value: Instant): Long = value.toEpochMilli
  }

  implicit object InstantFromValue extends FromValue[Instant] {
    override def apply(value: Any, field: Field): Instant = Instant.ofEpochMilli(value.asInstanceOf[Long])
  }

}