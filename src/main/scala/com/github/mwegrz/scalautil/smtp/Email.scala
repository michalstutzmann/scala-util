package com.github.mwegrz.scalautil.smtp

final case class Email(from: EmailAddress, to: EmailAddress, subject: String, content: String)
