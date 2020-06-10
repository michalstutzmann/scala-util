package com.github.mwegrz.scalautil.disruptivetechnologies

final case class EventHistoryResponse(events: List[Event], nextPageToken: Option[PageToken])
