package com.github.mwegrz.scalautil.disruptivetechnologies

final case class EventResponse(events: List[Event], nextPageToken: PageToken)
