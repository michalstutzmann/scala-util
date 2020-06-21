package com.github.mwegrz.scalautil.disruptivetechnologies

final case class ServiceAccount(email: String, keyId: String, secret: String) {
  lazy val projectId: ProjectId = {
    val regex = ".+@(.+)\\.serviceaccount.d21s.com".r("project-id")
    val result = regex.findFirstMatchIn(email).get
    ProjectId(result.group("project-id"))
  }
}
