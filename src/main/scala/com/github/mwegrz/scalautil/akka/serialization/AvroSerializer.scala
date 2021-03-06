package com.github.mwegrz.scalautil.akka.serialization

import java.nio.ByteBuffer

import akka.actor.ExtendedActorSystem
import akka.serialization.BaseSerializer
import com.github.mwegrz.scalautil.avro4s._
import com.github.mwegrz.scalautil.serialization.Serde
import com.sksamuel.avro4s.{ Decoder, Encoder, SchemaFor }
import org.apache.avro.Schema
import scodec.bits.ByteVector

abstract class AvroSerializer[Value: SchemaFor: Encoder: Decoder](
    extendedActorSystem: ExtendedActorSystem
) extends BaseSerializer
    with Serde[Value] {
  override def system: ExtendedActorSystem = extendedActorSystem

  protected def currentVersion: Int

  override final def includeManifest: Boolean = false

  protected def versionToWriterSchema: Map[Int, Schema]

  override def toBinary(o: AnyRef): Array[Byte] = {
    val schema = versionToWriterSchema(currentVersion)
    ByteBuffer.allocate(4).putInt(currentVersion).array() ++ o.asInstanceOf[Value].toAvro(schema)
  }

  override def fromBinary(bytes: Array[Byte], manifest: Option[Class[_]] = None): AnyRef = {
    val previousVersion = ByteBuffer.wrap(bytes.take(4)).getInt
    val writerSchema = versionToWriterSchema(previousVersion)
    val readerSchema = versionToWriterSchema(currentVersion)
    fromAvro[Value](bytes.drop(4), Some(writerSchema), readerSchema).get.asInstanceOf[AnyRef]
  }

  override def valueToBytes(value: Value): ByteVector = ByteVector(toBinary(value.asInstanceOf[AnyRef]))

  override def bytesToValue(binary: ByteVector): Value = fromBinary(binary.toArray).asInstanceOf[Value]
}
