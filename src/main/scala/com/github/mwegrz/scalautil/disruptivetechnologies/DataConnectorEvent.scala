package com.github.mwegrz.scalautil.disruptivetechnologies

object DataConnectorEvent {
  final case class Labels(name: String)
}

final case class DataConnectorEvent(event: Event, labels: DataConnectorEvent.Labels)
