package com.github.mwegrz.scalautil.akka.stream

import java.net.InetSocketAddress

import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import akka.stream.alpakka.udp.Datagram
import akka.stream.alpakka.udp.scaladsl.Udp
import akka.stream.scaladsl.{ Flow, Keep }
import akka.util.ByteString
import com.github.mwegrz.scalastructlog.KeyValueLogging
import com.typesafe.config.Config
import scodec.bits.ByteVector

import scala.concurrent.{ ExecutionContext, Future }

final case class UdpServer(config: Config)(
    implicit actorSystem: ActorSystem,
    actorMaterializer: ActorMaterializer,
    executionContext: ExecutionContext
) extends KeyValueLogging {
  final case object Bound extends Bound
  trait Bound

  private val localAddress = new InetSocketAddress(config.getString("host"), config.getInt("port"))

  def flow: Flow[(InetSocketAddress, ByteVector), (InetSocketAddress, ByteVector), Future[Bound]] = {
    Flow[(InetSocketAddress, ByteVector)]
      .map {
        case (addr, bytes) =>
          Datagram(ByteString(bytes.toArray), addr)
      }
      .viaMat(
        Udp
          .bindFlow(localAddress)
          .mapMaterializedValue { value =>
            value.map { localAddress =>
              log.debug(s"Bound to ${localAddress.getHostString}:${localAddress.getPort}")
              Bound
            }
          }
          .map { datagram =>
            val bytes = ByteVector(datagram.data.toArray)
            log.debug(
              "Packet received",
              (
                "host" -> datagram.remote.getHostName,
                "port" -> datagram.remote.getPort,
                "data" -> bytes.toHex
              )
            )
            (datagram.remote, bytes)
          }
      )(Keep.right)
  }
}
