package com.github.mwegrz.scalautil.akka.serialization

import java.nio.{ ByteBuffer, IntBuffer }

import akka.serialization.SerializerWithStringManifest
import com.github.mwegrz.scalautil.avro4s._
import com.sksamuel.avro4s.{ FromRecord, SchemaFor, ToRecord }
import org.apache.avro.Schema

abstract class AvroSerializer[A](implicit schemaFor: SchemaFor[A], toRecord: ToRecord[A], fromRecord: FromRecord[A])
    extends SerializerWithStringManifest {
  protected def currentVersion: Int

  protected def versionToWriterSchema: PartialFunction[Int, Schema]

  override def manifest(o: AnyRef): String = currentVersion.toString

  override def toBinary(o: AnyRef): Array[Byte] = o.asInstanceOf[A].toAvro

  def toBinaryWithVersion(a: A): Array[Byte] =
    ByteBuffer.allocate(4).putInt(currentVersion).array() ++ toBinary(a.asInstanceOf[AnyRef])

  override def fromBinary(bytes: Array[Byte], manifest: String): AnyRef = {
    val writerSchema = versionToWriterSchema(manifest.toInt)
    val readerSchema = versionToWriterSchema(currentVersion)
    parseAvro[A](bytes, Some(writerSchema), Some(readerSchema)).asInstanceOf[AnyRef]
  }

  def fromBinaryWithVersion(bytes: Array[Byte]): A = {
    val manifest = ByteBuffer.wrap(bytes.take(4)).getInt.toString
    fromBinary(bytes.drop(4), manifest).asInstanceOf[A]
  }
}
