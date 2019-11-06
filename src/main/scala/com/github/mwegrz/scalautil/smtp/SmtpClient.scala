package com.github.mwegrz.scalautil.smtp

import com.typesafe.config.Config
import com.github.mwegrz.scalautil.ConfigOps
import courier._

import scala.concurrent.{ ExecutionContext, Future }

object SmtpClient {
  def apply(config: Config)(implicit executionContext: ExecutionContext): SmtpClient =
    new SmtpClient(config.withReferenceDefaults("smtp-client"))(executionContext)
}

class SmtpClient private (config: Config)(implicit executionContext: ExecutionContext) {
  private val host = config.getString("host")
  private val port = config.getInt("port")
  private val username = config.getString("username")
  private val password = config.getString("password")

  private val mailer = Mailer(host, port)
    .auth(true)
    .as(username, password)
    .startTls(true)()

  def sendEmail(email: Email): Future[Unit] = {
    val Email(from, to, subject, content) = email
    val envelope = Envelope
      .from(from)
      .to(to)
      .subject(subject)
      .content(Text(content))

    mailer(envelope)
  }
}
