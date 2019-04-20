package com.github.mwegrz.scalautil.akka.serialization

import com.sksamuel.avro4s.SchemaFor
import org.apache.avro.Schema

abstract class AvroSchema[Value: SchemaFor] {
  implicit val avroSchema: Schema = SchemaFor[Value].schema
}
