package com.github.mwegrz.scalautil.smtp

import javax.mail.internet.InternetAddress

final case class Email(from: InternetAddress, to: InternetAddress, subject: String, content: String)
