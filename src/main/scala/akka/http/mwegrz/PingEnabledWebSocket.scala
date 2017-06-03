package akka.http.mwegrz

import java.util.Random

import akka.NotUsed
import akka.event.LoggingAdapter
import akka.http.impl.engine.ws.{FrameEvent, FrameEventOrError, FrameStart}
import akka.http.impl.engine.ws.Protocol.Opcode
import akka.http.impl.engine.ws.WebSocket.{frameHandling, masking, messageAPI}
import akka.http.scaladsl.model.ws.Message
import akka.stream.{Attributes, BidiShape, Inlet, Outlet}
import akka.stream.scaladsl.BidiFlow
import akka.stream.stage._
import akka.util.ByteString

import scala.concurrent.duration._

object PingEnabledWebSocket {

  /** A stack of all the higher WS layers between raw frames and the user API.
    */
  def stack(
      serverSide: Boolean,
      maskingRandomFactory: () ⇒ Random,
      closeTimeout: FiniteDuration = 3.seconds,
      log: LoggingAdapter
  ): BidiFlow[FrameEvent, Message, Message, FrameEvent, NotUsed] =
    masking(serverSide, maskingRandomFactory) atop
      pingHandling().reversed atop
      frameHandling(serverSide, closeTimeout, log) atop
      messageAPI(serverSide, closeTimeout)

  object ClientSidePingHandler {
    val PingTimerKey = None
  }

  private def pingHandling(): BidiFlow[FrameEvent, FrameEvent, FrameEventOrError, FrameEvent, NotUsed] =
    BidiFlow.fromGraph(new ClientSidePingHandler)

  private class ClientSidePingHandler
      extends GraphStage[BidiShape[FrameEvent, FrameEvent, FrameEventOrError, FrameEvent]] {
    import ClientSidePingHandler.PingTimerKey

    private val name = getClass.getName
    private val in1  = Inlet[FrameEvent](s"$name.in1")
    private val out1 = Outlet[FrameEvent](s"$name.out1")
    private val in2  = Inlet[FrameEventOrError](s"$name.in2")
    private val out2 = Outlet[FrameEvent](s"$name.out")

    override val shape = BidiShape(in1, out1, in2, out2)

    override def createLogic(inheritedAttributes: Attributes): GraphStageLogic =
      new TimerGraphStageLogic(shape) with StageLogging {

        private var pongReceived = true

        override def preStart(): Unit = {
          schedulePing()
          super.preStart()
        }

        override protected def onTimer(timerKey: Any): Unit = {
          if (!pongReceived) failStage(new Exception("No Pong received"))
          if (isAvailable(out1)) {
            pongReceived = false
            push(out1, FrameEvent.fullFrame(Opcode.Ping, None, ByteString.empty, fin = true))
            schedulePing()
          }
        }

        setHandler(
          in1,
          new InHandler {
            override def onPush(): Unit = {
              val elem = grab(in1)
              push(out1, elem)
            }

            override def onUpstreamFinish(): Unit = super.onUpstreamFinish()

            override def onUpstreamFailure(t: Throwable): Unit =
              super.onUpstreamFailure(t)
          }
        )

        setHandler(out1, new OutHandler {
          override def onPull(): Unit = {
            if (!hasBeenPulled(in1)) pull(in1)
          }

          override def onDownstreamFinish(): Unit = super.onDownstreamFinish()
        })

        setHandler(
          in2,
          new InHandler {
            override def onPush(): Unit = {
              resetPing()
              val elem = grab(in2).asInstanceOf[FrameEvent]
              elem match {
                case f: FrameStart if f.header.opcode == Opcode.Pong =>
                  pongReceived = true
                  pull(in2)
                case _ => push(out2, elem.asInstanceOf[FrameEvent])
              }
            }

            override def onUpstreamFinish(): Unit = super.onUpstreamFinish()

            override def onUpstreamFailure(t: Throwable): Unit =
              super.onUpstreamFailure(t)
          }
        )

        setHandler(out2, new OutHandler {
          override def onPull(): Unit = pull(in2)

          override def onDownstreamFinish(): Unit = super.onDownstreamFinish()
        })

        private def schedulePing() = scheduleOnce(PingTimerKey, 10.seconds)

        private def cancelPing() = cancelTimer(PingTimerKey)

        private def resetPing() = {
          cancelPing()
          schedulePing()
        }
      }
  }
}
