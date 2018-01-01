package com.github.mwegrz.scalautil.avro4s

import java.nio.ByteBuffer
import java.time.Instant

import com.sksamuel.avro4s._
import org.apache.avro.{ Conversions, LogicalTypes, Schema, SchemaBuilder }
import org.apache.avro.Schema.Field
import scodec.bits.ByteVector

object codecs {
  /* Fixes https://github.com/sksamuel/avro4s/issues/141 */
  implicit def BigDecimalToValue(implicit sp: ScaleAndPrecision = ScaleAndPrecision(2, 8)): ToValue[BigDecimal] = {
    val decimalConversion = new Conversions.DecimalConversion
    val decimalType = LogicalTypes.decimal(sp.precision, sp.scale)
    new ToValue[BigDecimal] {
      override def apply(value: BigDecimal): ByteBuffer = {
        decimalConversion.toBytes(value.setScale(sp.scale).bigDecimal, null, decimalType)
      }
    }
  }

  implicit val ByteToSchema: ToSchema[Byte] = new ToSchema[Byte] {
    protected val schema = Schema.create(Schema.Type.INT)
  }

  implicit val ByteToValue: ToValue[Byte] = ToValue[Byte]

  implicit object ByteFromValue extends FromValue[Byte] {
    override def apply(value: Any, field: Field): Byte = value.asInstanceOf[Byte]
  }

  implicit object BytechemaFor extends SchemaFor[Byte] {
    private val schema = SchemaBuilder.builder().intType()
    def apply(): org.apache.avro.Schema = schema
  }

  //implicit val ByteFromRecord: FromRecord[Byte] = FromRecord[Byte]
  //implicit val ByteToRecord: ToRecord[Byte] = ToRecord[Byte]

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
