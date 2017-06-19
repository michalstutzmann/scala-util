package com.github.mwegrz.scalautil.time

import java.time.{ Duration, Instant }

class StopWatch() {
  val start: Instant = Instant.now()
  lazy val stop: Duration = Duration.between(start, Instant.now())
}
