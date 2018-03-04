package com.github.mwegrz.scalautil.akka.serialization

import akka.serialization.SerializerWithStringManifest
import com.github.mwegrz.scalautil.avro4s._
import com.sksamuel.avro4s.{ FromRecord, SchemaFor, ToRecord }

abstract class AvroSerializer[A](implicit schemaFor: SchemaFor[A], toRecord: ToRecord[A], fromRecord: FromRecord[A])
    extends SerializerWithStringManifest {
  override def manifest(o: AnyRef): String = o.getClass.getName

  override def toBinary(o: AnyRef): Array[Byte] = o.asInstanceOf[A].toAvro

  override def fromBinary(bytes: Array[Byte], manifest: String): AnyRef = parseAvro[A](bytes).asInstanceOf[AnyRef]
}
