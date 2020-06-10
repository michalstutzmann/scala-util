package com.github.mwegrz.scalautil.disruptivetechnologies

final case class ProjectId(value: String) extends AnyVal {
  override def toString: String = value
}
