package com.github.mwegrz.scalautil.akka.stream.scaladsl

import akka.NotUsed
import akka.stream.scaladsl.{ Flow, RestartFlow }
import com.github.mwegrz.scalautil.javaDurationToDuration

object PolicyRestartFlow {
  def withBackoff[A, B](f: () => Flow[A, B, _])(implicit restartPolicy: RestartPolicy): Flow[A, B, NotUsed] = {
    RestartFlow.withBackoff(
      minBackoff = restartPolicy.minBackoff,
      maxBackoff = restartPolicy.maxBackoff,
      randomFactor = restartPolicy.randomFactor,
      maxRestarts = restartPolicy.maxRestarts
    )(f)
  }

  def onFailuresWithBackoff[A, B](
      f: () => Flow[A, B, _]
  )(implicit restartPolicy: RestartPolicy): Flow[A, B, NotUsed] = {
    RestartFlow.onFailuresWithBackoff(
      minBackoff = restartPolicy.minBackoff,
      maxBackoff = restartPolicy.maxBackoff,
      randomFactor = restartPolicy.randomFactor,
      maxRestarts = restartPolicy.maxRestarts
    )(f)
  }
}
