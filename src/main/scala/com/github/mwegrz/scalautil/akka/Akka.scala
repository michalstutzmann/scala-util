package com.github.mwegrz.scalautil.akka

import java.util.UUID

import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import com.github.mwegrz.app.Shutdownable
import com.github.mwegrz.scalastructlog.KeyValueLogging
import com.typesafe.config.Config
import com.github.mwegrz.scalautil.ConfigOps

import scala.concurrent.{ Await, ExecutionContext }
import scala.concurrent.duration._

class Akka private (config: Config)(implicit executor: ExecutionContext)
    extends Shutdownable
    with KeyValueLogging {
  implicit val actorSystem: ActorSystem = ActorSystem(UUID.randomUUID().toString, config)
  implicit val actorMaterializer: ActorMaterializer = ActorMaterializer()

  log.debug("Initialized")

  override def shutdown(): Unit =
    Await.result(actorSystem.terminate().map(_ => log.debug("Shut down")), Duration.Inf)
}

object Akka {
  def apply(config: Config)(implicit ec: ExecutionContext): Akka =
    new Akka(config.withReferenceDefaults("akka"))
}
