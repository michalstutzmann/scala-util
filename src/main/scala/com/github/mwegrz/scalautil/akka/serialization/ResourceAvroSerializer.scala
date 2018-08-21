package com.github.mwegrz.scalautil.akka.serialization

import akka.actor.ExtendedActorSystem
import com.sksamuel.avro4s.{ FromRecord, SchemaFor, ToRecord }
import org.apache.avro.Schema

import scala.reflect.ClassTag

class ResourceAvroSerializer[A: SchemaFor: ToRecord: FromRecord: ClassTag](extendedActorSystem: ExtendedActorSystem)
    extends AvroSerializer[A](extendedActorSystem) {

  protected def versionToWriterSchemaResource: PartialFunction[Int, String] = {
    case version =>
      val classTag = implicitly[ClassTag[A]]
      val packageName = classTag.runtimeClass.getPackage.getName
      val path = packageName.replaceAll("\\.", "/")
      val name = classTag.runtimeClass.getName.stripPrefix(s"$packageName.")
      s"$path/$name-$version.avsc"
  }

  override protected val versionToWriterSchema: PartialFunction[Int, Schema] = {
    case version =>
      val resource = versionToWriterSchemaResource(version)
      new Schema.Parser().parse(getClass.getClassLoader.getResourceAsStream(resource))
  }
}
