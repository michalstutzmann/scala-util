package com.github.mwegrz.scalautil.sms

final case class Sms(recipient: Msisdn, sender: String, message: String)
