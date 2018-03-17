package com.github.mwegrz.scalautil.akka.serialization

import com.sksamuel.avro4s.{ FromRecord, SchemaFor, ToRecord }
import org.apache.avro.Schema

abstract class ResourceAvroSerializer[A](implicit schemaFor: SchemaFor[A],
                                         toRecord: ToRecord[A],
                                         fromRecord: FromRecord[A])
    extends AvroSerializer[A] {
  protected def versionToWriterSchemaResource: PartialFunction[Int, String]

  override protected val versionToWriterSchema: PartialFunction[Int, Schema] = {
    case version =>
      val resource = versionToWriterSchemaResource(version)
      new Schema.Parser().parse(getClass.getClassLoader.getResourceAsStream(resource))
  }
}
