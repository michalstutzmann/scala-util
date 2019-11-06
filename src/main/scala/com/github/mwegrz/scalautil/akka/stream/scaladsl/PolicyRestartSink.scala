package com.github.mwegrz.scalautil.akka.stream.scaladsl

import akka.NotUsed
import akka.stream.scaladsl.{ RestartSink, Sink }
import com.github.mwegrz.scalautil.javaDurationToDuration

object PolicyRestartSink {
  def withBackoff[A](f: () => Sink[A, _])(implicit restartPolicy: RestartPolicy): Sink[A, NotUsed] = {
    RestartSink.withBackoff(
      minBackoff = restartPolicy.minBackoff,
      maxBackoff = restartPolicy.maxBackoff,
      randomFactor = restartPolicy.randomFactor,
      maxRestarts = restartPolicy.maxRestarts
    )(f)
  }
}
