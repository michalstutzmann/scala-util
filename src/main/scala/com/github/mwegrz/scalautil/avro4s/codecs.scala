package com.github.mwegrz.scalautil.avro4s

import java.time.{ Duration, Instant }

import akka.http.scaladsl.model.Uri
import com.github.mwegrz.scalautil.StringVal
import com.sksamuel.avro4s._
import org.apache.avro.{ Schema, SchemaBuilder }
import org.apache.avro.Schema.Field
import org.apache.avro.util.Utf8
import pl.iterators.kebs.macros.CaseClass1Rep
import scodec.bits.ByteVector

import scala.collection.JavaConverters._

object codecs {
  implicit def stringValToSchema[CC <: StringVal with Product](implicit rep: CaseClass1Rep[CC, String],
                                                               subschema: ToSchema[String]): ToSchema[CC] =
    new ToSchema[CC] {
      override protected val schema = subschema()
    }

  implicit def stringValToValue[CC <: StringVal with Product](implicit rep: CaseClass1Rep[CC, String],
                                                              delegate: ToValue[String]): ToValue[CC] =
    new ToValue[CC] {
      override def apply(value: CC) = delegate(rep.unapply(value))
    }

  implicit def stringValFromValue[CC <: StringVal with Product](implicit rep: CaseClass1Rep[CC, String],
                                                                delegate: FromValue[String]): FromValue[CC] =
    new FromValue[CC] {
      override def apply(value: Any, field: Schema.Field) = rep.apply(delegate(value, field))
    }

  implicit val ByteToSchema: ToSchema[Byte] = new ToSchema[Byte] {
    protected val schema: Schema = Schema.create(Schema.Type.INT)
  }

  implicit val ByteToValue: ToValue[Byte] = ToValue[Byte]

  implicit object ByteFromValue extends FromValue[Byte] {
    override def apply(value: Any, field: Field): Byte = value.asInstanceOf[Byte]
  }

  implicit object BytechemaFor extends SchemaFor[Byte] {
    private val schema = SchemaBuilder.builder().intType()
    def apply(): org.apache.avro.Schema = schema
  }

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

  implicit val InstantToSchema: ToSchema[Instant] = createToSchema[Instant](Schema.create(Schema.Type.STRING))
  implicit val InstantToValue: ToValue[Instant] = createToValue[Instant, String](_.toString())
  implicit val InstantFromValue: FromValue[Instant] =
    createFromValue[Instant]((value, _) => Instant.parse(value.asInstanceOf[Utf8].toString))

  implicit val DurationToSchema: ToSchema[Duration] = createToSchema[Duration](Schema.create(Schema.Type.STRING))
  implicit val DurationToValue: ToValue[Duration] = createToValue[Duration, String](_.toString())
  implicit val DurationFromValue: FromValue[Duration] =
    createFromValue[Duration]((value, _) => Duration.parse(value.asInstanceOf[Utf8].toString))

  implicit val UriToSchema: ToSchema[Uri] = createToSchema[Uri](Schema.create(Schema.Type.STRING))
  implicit val UriToValue: ToValue[Uri] = createToValue[Uri, String](_.toString())
  implicit val UriFromValue: FromValue[Uri] =
    createFromValue[Uri]((value, _) => Uri(value.asInstanceOf[Utf8].toString))

  implicit def TypedKeyMapToSchema[K, V](implicit valueToSchema: ToSchema[V]): ToSchema[Map[K, V]] = {
    new ToSchema[Map[K, V]] {
      override protected val schema: Schema = Schema.createMap(valueToSchema())
    }
  }

  implicit def TypedKeyMapToValue[K, V](implicit keyToValue: ToValue[K], valueToValue: ToValue[V]): ToValue[Map[K, V]] =
    new ToValue[Map[K, V]] {
      override def apply(value: Map[K, V]): java.util.Map[String, V] = {
        value.map { case (k, v) => (keyToValue(k), valueToValue(v)) }.asInstanceOf[Map[String, V]].asJava
      }
    }

  implicit def TypedKeyMapFromValue[K, V](implicit keyFromValue: FromValue[K],
                                          valueFromValue: FromValue[V]): FromValue[Map[K, V]] =
    new FromValue[Map[K, V]] {
      override def apply(value: Any, field: Field): Map[K, V] = value match {
        case map: java.util.Map[_, _] => map.asScala.toMap.map { case (k, v) => keyFromValue(k) -> valueFromValue(v) }
        case other                    => sys.error("Unsupported map " + other)
      }
    }
}
