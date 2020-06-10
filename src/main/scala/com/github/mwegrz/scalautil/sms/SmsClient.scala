package com.github.mwegrz.scalautil.sms

import scala.concurrent.Future

trait SmsClient {
  def send(sms: Sms): Future[Unit]
}
