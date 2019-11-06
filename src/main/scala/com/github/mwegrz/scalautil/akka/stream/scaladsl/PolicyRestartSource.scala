package com.github.mwegrz.scalautil.akka.stream.scaladsl

import akka.NotUsed
import akka.stream.scaladsl.{ RestartSource, Source }

import com.github.mwegrz.scalautil.javaDurationToDuration

object PolicyRestartSource {
  def withBackoff[A](f: () => Source[A, _])(implicit restartPolicy: RestartPolicy): Source[A, NotUsed] = {
    RestartSource.withBackoff(
      minBackoff = restartPolicy.minBackoff,
      maxBackoff = restartPolicy.maxBackoff,
      randomFactor = restartPolicy.randomFactor,
      maxRestarts = restartPolicy.maxRestarts
    )(f)
  }

  def onFailuresWithBackoff[A](f: () => Source[A, _])(implicit restartPolicy: RestartPolicy): Source[A, NotUsed] = {
    RestartSource.onFailuresWithBackoff(
      minBackoff = restartPolicy.minBackoff,
      maxBackoff = restartPolicy.maxBackoff,
      randomFactor = restartPolicy.randomFactor,
      maxRestarts = restartPolicy.maxRestarts
    )(f)
  }
}
