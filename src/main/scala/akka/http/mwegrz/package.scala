package akka.http

import java.net.InetSocketAddress

import akka.event.LoggingAdapter
import akka.http.scaladsl.Http.OutgoingConnection
import akka.http.scaladsl.model.ws.{ Message, WebSocketRequest, WebSocketUpgradeResponse }
import akka.http.scaladsl.settings.ClientConnectionSettings
import akka.http.scaladsl.{ ClientTransport, ConnectionContext, Http, HttpExt }
import akka.stream.Client
import akka.stream.TLSProtocol.{ SslTlsInbound, SslTlsOutbound }
import akka.stream.scaladsl.{ Flow, Keep }

import scala.concurrent.Future

package object mwegrz {
  implicit class HttpExtOps(httpExt: HttpExt) {
    import httpExt.system

    /** Constructs a flow that once materialized establishes a WebSocket connection to the given Uri.
      *
      * The layer is not reusable and must only be materialized once.
      */
    def pingEnabledWebSocketClientFlow(
        request: WebSocketRequest,
        connectionContext: ConnectionContext = httpExt.defaultClientHttpsContext,
        localAddress: Option[InetSocketAddress] = None,
        settings: ClientConnectionSettings = ClientConnectionSettings(httpExt.system),
        log: LoggingAdapter = httpExt.system.log
    ): Flow[Message, Message, Future[WebSocketUpgradeResponse]] = {
      import request.uri
      require(uri.isAbsolute, s"WebSocket request URI must be absolute but was '$uri'")

      val ctx = uri.scheme match {
        case "ws" ⇒ ConnectionContext.noEncryption()
        case "wss" if connectionContext.isSecure ⇒ connectionContext
        case "wss" ⇒
          throw new IllegalArgumentException(
            "Provided connectionContext is not secure, yet request to secure `wss` endpoint detected!")
        case scheme ⇒
          throw new IllegalArgumentException(
            s"Illegal URI scheme '$scheme' in '$uri' for WebSocket request. " +
              s"WebSocket requests must use either 'ws' or 'wss'")
      }
      val host = uri.authority.host.address
      val port = uri.effectivePort

      httpExt
        .pingEnabledWebSocketClientLayer(request, settings, log)
        .joinMat(_outgoingTlsConnectionLayer(host, port, settings, ctx, ClientTransport.TCP, log))(
          //_outgoingTlsConnectionLayer(host, port, settings, ctx, ClientTransport.TCP(localAddress, settings), log))(
          Keep.left)
    }

    private def _outgoingTlsConnectionLayer(
        host: String,
        port: Int,
        settings: ClientConnectionSettings,
        connectionContext: ConnectionContext,
        transport: ClientTransport,
        log: LoggingAdapter): Flow[SslTlsOutbound, SslTlsInbound, Future[OutgoingConnection]] = {
      val tlsStage =
        httpExt.sslTlsStage(connectionContext, Client, Some(host → port))

      tlsStage.joinMat(transport.connectTo(host, port, settings))(Keep.right)
    }

    /** Constructs a [[akka.http.scaladsl.Http.WebSocketClientLayer]] stage using the configured default [[akka.http.scaladsl.settings.ClientConnectionSettings]],
      * configured using the `akka.http.client` config section.
      *
      * The layer is not reusable and must only be materialized once.
      */
    def pingEnabledWebSocketClientLayer(
        request: WebSocketRequest,
        settings: ClientConnectionSettings = ClientConnectionSettings(httpExt.system),
        log: LoggingAdapter = httpExt.system.log
    ): Http.WebSocketClientLayer =
      PingEnabledWebSocketClientBlueprint(request, settings, log)
  }
}
