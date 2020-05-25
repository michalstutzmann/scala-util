package com.github.mwegrz.scalautil.disruptivetechnologies

final case class Event(eventId: String, targetName: String, eventType: String, data: String, label: String)
