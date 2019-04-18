package com.github.mwegrz.scalautil.oauth2

import scala.concurrent.Future

trait Oauth2Client {
  def obtainToken: Future[TokenObtained]
}
