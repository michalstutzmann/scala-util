package com.github.mwegrz.scalautil.akka.serialization

import com.sksamuel.avro4s.{ FromRecord, SchemaFor, ToRecord }
import org.apache.avro.Schema

import scala.reflect.ClassTag

class ResourceAvroSerializer[A](override val identifier: Int, override val currentVersion: Int)(
    implicit schemaFor: SchemaFor[A],
    toRecord: ToRecord[A],
    fromRecord: FromRecord[A],
    classTag: ClassTag[A])
    extends AvroSerializer[A] {
  protected def versionToWriterSchemaResource: PartialFunction[Int, String] = {
    case version =>
      val path = classTag.runtimeClass.getPackage.getName.replaceAll("\\.", "/")
      val name = classTag.runtimeClass.getSimpleName
      s"$path/$name-$version.avsc"
  }

  override protected val versionToWriterSchema: PartialFunction[Int, Schema] = {
    case version =>
      val resource = versionToWriterSchemaResource(version)
      new Schema.Parser().parse(getClass.getClassLoader.getResourceAsStream(resource))
  }
}
