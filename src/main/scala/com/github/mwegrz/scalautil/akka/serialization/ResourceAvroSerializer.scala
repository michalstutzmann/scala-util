package com.github.mwegrz.scalautil.akka.serialization

import akka.actor.ExtendedActorSystem
import com.sksamuel.avro4s.{ Decoder, Encoder, SchemaFor }
import org.apache.avro.SchemaCompatibility.SchemaCompatibilityType
import org.apache.avro.{ Schema, SchemaCompatibility }

import scala.reflect.ClassTag

class ResourceAvroSerializer[A: SchemaFor: Encoder: Decoder: ClassTag](
    extendedActorSystem: ExtendedActorSystem,
    override val currentVersion: Int
) extends AvroSerializer[A](extendedActorSystem) {
  private val classTag = implicitly[ClassTag[A]]
  private val packageName = classTag.runtimeClass.getPackage.getName
  private val path = packageName.replaceAll("\\.", "/")
  private val name = classTag.runtimeClass.getName.stripPrefix(s"$packageName.")

  protected def versionToWriterSchemaResource: PartialFunction[Int, String] = {
    case version => s"$path/$name-$version.avsc"
  }

  override protected val versionToWriterSchema: Map[Int, Schema] =
    Range
      .inclusive(1, currentVersion)
      .map { version =>
        val resource = versionToWriterSchemaResource(version)
        val schema = new Schema.Parser().parse(getClass.getClassLoader.getResourceAsStream(resource))
        (version, schema)
      }
      .toMap

  require(isBackwardsCompatible, "current schema is not backward compatible")

  def isBackwardsCompatible: Boolean =
    if (currentVersion > 1) {
      SchemaCompatibility
        .checkReaderWriterCompatibility(
          versionToWriterSchema(currentVersion),
          versionToWriterSchema(currentVersion - 1)
        )
        .getType == SchemaCompatibilityType.COMPATIBLE
    } else true
}
