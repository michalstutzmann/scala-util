package com.github.mwegrz.scalautil.disruptivetechnologies

final case class LiveEventResponse(result: Result)

final case class Result(event: Event)
