package com.github.mwegrz.scalautil.serialization

import akka.actor.{ ActorSystem, ExtendedActorSystem }
import com.github.mwegrz.scalautil.akka.serialization.ResourceAvroSerializer
import com.sksamuel.avro4s.{ Decoder, Encoder, SchemaFor }

import scala.language.implicitConversions
import scala.reflect.ClassTag

// Does not work because of https://github.com/milessabin/shapeless/issues/837, https://docs.scala-lang.org/sips/byname-implicits.html. Will work in Scala 2.13.
abstract class AkkaSerialization[Value: SchemaFor: Encoder: Decoder: ClassTag](
    currentVersion: Int
) {
  implicit def serde(actorSystem: ActorSystem): Serde[Value] =
    new AkkaSerializer(actorSystem.asInstanceOf[ExtendedActorSystem])

  class AkkaSerializer(extendedActorSystem: ExtendedActorSystem)
      extends ResourceAvroSerializer[Value](extendedActorSystem, currentVersion)
}
