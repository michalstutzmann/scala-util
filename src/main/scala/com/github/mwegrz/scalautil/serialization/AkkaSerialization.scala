package com.github.mwegrz.scalautil.serialization

import akka.actor.{ ActorSystem, ExtendedActorSystem }
import com.github.mwegrz.scalautil.akka.serialization.ResourceAvroSerializer
import com.sksamuel.avro4s.{ SchemaFor, FromRecord, ToRecord }

import scala.language.implicitConversions
import scala.reflect.ClassTag

class AkkaSerialization[Value: ClassTag: SchemaFor: ToRecord: FromRecord]() {
  implicit def serde(actorSystem: ActorSystem): Serde[Value] =
    new AkkaSerializer(actorSystem.asInstanceOf[ExtendedActorSystem])

  class AkkaSerializer(extendedActorSystem: ExtendedActorSystem)
      extends ResourceAvroSerializer[Value](extendedActorSystem)
}
