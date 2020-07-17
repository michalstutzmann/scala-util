package com.github.mwegrz.scalautil.smtp

import com.github.mwegrz.scalastructlog.KeyValueLogging
import com.typesafe.config.Config
import com.github.mwegrz.scalautil.ConfigOps
import courier._

import scala.concurrent.{ ExecutionContext, Future }
import scala.util.{ Failure, Success }

object SmtpClient {
  def apply(config: Config)(implicit executionContext: ExecutionContext): SmtpClient =
    new SmtpClient(config.withReferenceDefaults("smtp-client"))(executionContext)
}

class SmtpClient private (config: Config)(implicit executionContext: ExecutionContext) extends KeyValueLogging {
  private val host = config.getString("host")
  private val port = config.getInt("port")
  private val username = config.getString("username")
  private val password = config.getString("password")

  private val mailer = Mailer().session
    .host(host)
    .port(port)
    .auth(true)
    .as(username, password)
    .startTls(true)()

  def sendEmail(email: Email): Future[Unit] = {
    val Email(from, to, subject, content) = email
    val envelope = Envelope
      .from(from.toInternetAddress)
      .to(to.toInternetAddress)
      .subject(subject)
      .content(Text(content))

    log.debug("Sending e-mail")

    val sending = mailer(envelope)
    sending.onComplete {
      case Success(_)         => log.debug("Email sent", "email" -> email)
      case Failure(exception) => log.error("Could not send e-mail", exception, "email" -> email)
    }
    sending
  }
}
