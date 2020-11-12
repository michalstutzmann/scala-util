package com.github.mwegrz.scalautil.eltin

import java.net.InetSocketAddress

import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import com.github.mwegrz.scalastructlog.KeyValueLogging
import com.github.mwegrz.scalautil.ConfigOps
import com.typesafe.config.Config
import akka.stream.scaladsl.{ Keep, Sink, Source, Tcp }
import akka.util.{ ByteString, Timeout }

import scala.concurrent.{ ExecutionContext, Future }
import scala.util.{ Failure, Success }

object EltinClient {
  def apply(config: Config)(implicit
      actorSystem: ActorSystem,
      actorMaterializer: ActorMaterializer,
      executionContext: ExecutionContext,
      imeout: Timeout
  ): EltinClient =
    new EltinClient(config.withReferenceDefaults("eltin.client"))
}

class EltinClient private (config: Config)(implicit
    actorSystem: ActorSystem,
    actorMaterializer: ActorMaterializer,
    executionContext: ExecutionContext,
    timeout: Timeout
) extends KeyValueLogging {
  private val host = config.getString("host")
  private val port = config.getInt("port")

  private val tcp = Tcp()

  def set(displayValues: Map[DisplayId, DisplayValue]): Future[Unit] = {
    val command = (displayValues map {
      case (id, DisplayValue(length, value)) =>
        val paddedValue = value.toString.reverse.padTo(length, '0').reverse
        s"<DISP${id.value}>${paddedValue}\r\n"
    }).mkString

    val connection = tcp.outgoingConnection(
      remoteAddress = InetSocketAddress.createUnresolved(host, port),
      connectTimeout = timeout.duration,
      idleTimeout = timeout.duration
    )

    val setting = {
      val ((writing, connecting), reading) = Source(List(ByteString(command)))
        .concatMat(Source.maybe)(Keep.right)
        .viaMat(connection)(Keep.both)
        .toMat(Sink.fold(ByteString.empty)((bytes, byte) => bytes ++ byte))(Keep.both)
        .run()
      for {
        _ <- connecting
        bytes <- reading
      } yield {
        writing.complete(Success(None))
        bytes.utf8String
      }
    }

    log.debug("Setting", "display-values" -> displayValues)

    setting.onComplete {
      case Success(response) =>
        log.debug(
          "Set",
          ("display-values" -> displayValues, "response" -> response.replace("\r", "\\r").replace("\n", "\\n"))
        )
      case Failure(exception) =>
        log.error("Setting failed", exception, "display-values" -> displayValues)
    }
    setting.map(_ => ())
  }
}
