package akka.http.mwegrz

import akka.event.LoggingAdapter
import akka.http.impl.engine.ws.WebSocket
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.ws.WebSocketRequest
import akka.http.scaladsl.settings.ClientConnectionSettings
import akka.stream.scaladsl.Keep
import akka.http.impl.engine.ws.WebSocketClientBlueprint.{handshake, simpleTls}

object PingEnabledWebSocketClientBlueprint {

  /** Returns a PingEnabledWebSocketClientLayer that can be materialized once.
    */
  def apply(
      request: WebSocketRequest,
      settings: ClientConnectionSettings,
      log: LoggingAdapter
  ): Http.WebSocketClientLayer =
    (simpleTls.atopMat(handshake(request, settings, log))(Keep.right) atop
      WebSocket.framing atop
      PingEnabledWebSocket.stack(serverSide = false,
                                 maskingRandomFactory = settings.websocketRandomFactory,
                                 log = log)).reversed
}
