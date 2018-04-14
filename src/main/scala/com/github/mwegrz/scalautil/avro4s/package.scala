package com.github.mwegrz.scalautil

import java.io.{ ByteArrayInputStream, ByteArrayOutputStream }

import com.sksamuel.avro4s._
import org.apache.avro.Schema
import org.apache.avro.Schema.Field
import org.codehaus.jackson.map.PropertyNamingStrategy

package object avro4s {
  implicit class AOps[A](c: A) {
    def toAvro(implicit schemaFor: SchemaFor[A], toRecord: ToRecord[A]): Array[Byte] = {
      val baos = new ByteArrayOutputStream()
      val output = AvroOutputStream.binary[A](baos)
      output.write(c)
      output.close()
      baos.toByteArray
    }
  }

  def parseAvro[A](bytes: Array[Byte], writerSchema: Option[Schema] = None, readerSchema: Option[Schema] = None)(
      implicit schemaFor: SchemaFor[A],
      fromRecord: FromRecord[A]): A = {
    val in = new ByteArrayInputStream(bytes)
    val input = new AvroBinaryInputStream[A](in, writerSchema, readerSchema)
    input.iterator.toSeq.head
  }

  def createToSchema[A](s: Schema): ToSchema[A] = new ToSchema[A] {
    override val schema: Schema = s
  }

  def createToValue[A, B](f: A => B): ToValue[A] = new ToValue[A] {
    override def apply(value: A): B = f(value)
  }

  def createFromValue[A](f: (Any, Field) => A): FromValue[A] = new FromValue[A] {
    override def apply(value: Any, field: Field): A = f(value, field)
  }
}
