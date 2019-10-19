package com.github.mwegrz.scalautil.akka.stream.scaladsl

import com.typesafe.config.Config
import java.time.Duration

object RestartPolicy {
  def fromConfig(config: Config): RestartPolicy = {
    val minBackoff = config.getDuration("min-backoff")
    val maxBackoff = config.getDuration("max-backoff")
    val randomFactor = config.getDouble("random-factor")
    val maxRestarts = config.getInt("max-restarts")
    RestartPolicy(minBackoff, maxBackoff, randomFactor, maxRestarts)
  }
}

final case class RestartPolicy(minBackoff: Duration, maxBackoff: Duration, randomFactor: Double, maxRestarts: Int)
