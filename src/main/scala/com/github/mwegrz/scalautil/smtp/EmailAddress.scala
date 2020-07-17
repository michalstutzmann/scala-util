package com.github.mwegrz.scalautil.smtp
import javax.mail.internet.InternetAddress
import org.scalactic.Or

import scala.util.Try

object EmailAddress {
  final case class Invalid(cause: Throwable)

  def parse(value: String): Or[List[EmailAddress], Invalid] =
    Or.from(Try(InternetAddress.parse(value))).map(_.toList.map(a => EmailAddress(a.toUnicodeString))).badMap(Invalid)
}

final case class EmailAddress(value: String) extends AnyVal {
  def toInternetAddress: InternetAddress = new InternetAddress(value)
}
