package com.github.mwegrz.scalautil.akka.streams

import java.net.InetSocketAddress
import akka.Done
import akka.actor.{ ActorRef, ActorSystem }
import akka.io.{ IO, Udp }
import akka.stream._
import akka.stream.scaladsl.{ Keep, Sink, Source }
import akka.stream.stage.GraphStageLogic.StageActor
import akka.stream.stage.{ GraphStage, GraphStageLogic, InHandler, OutHandler }
import akka.util.ByteString
import scala.collection.mutable
import scala.concurrent.Future

class UdpFlowStage(localAddr: InetSocketAddress,
                   maxBufferSize: Int,
                   dropped: Sink[(ByteString, InetSocketAddress), Future[Done]])(implicit actorSystem: ActorSystem,
                                                                                 actorMaterializer: ActorMaterializer)
    extends GraphStage[FlowShape[(ByteString, InetSocketAddress), (ByteString, InetSocketAddress)]] {
  private val name = getClass.getName
  private val in = Inlet[(ByteString, InetSocketAddress)](s"$name.in")
  private val out = Outlet[(ByteString, InetSocketAddress)](s"$name.out")

  override val shape = new FlowShape(in, out)

  override def createLogic(inheritedAttributes: Attributes): GraphStageLogic = new GraphStageLogic(shape) {
    private lazy val self: StageActor = getStageActor(binding)

    private val buffer = mutable.Queue.empty[(ByteString, InetSocketAddress)]

    private val (drop, droppedDone) = Source.actorRef(0, OverflowStrategy.dropNew).toMat(dropped)(Keep.both).run()

    private def binding(receive: (ActorRef, Any)) = {
      val (sender, msg) = receive
      msg match {
        case Udp.Bound(_) =>
          self.become(ready(sender))
          pull(in)
      }
    }

    private def ready(socket: ActorRef)(receive: (ActorRef, Any)) = {
      val (_, msg) = receive
      msg match {
        case Udp.Received(bytes, remoteAddr) =>
          if (isAvailable(out))
            push(out, (bytes, remoteAddr))
          else if (buffer.size <= maxBufferSize)
            buffer.enqueue((bytes, remoteAddr))
          else {
            if (!droppedDone.isCompleted) drop ! (bytes, remoteAddr)
          }
        case Udp.Unbind  => socket ! Udp.Unbind
        case Udp.Unbound => completeStage()
        case (bytes: ByteString, remoteAddr: InetSocketAddress) =>
          socket.tell(Udp.Send(bytes, remoteAddr), self.ref)
          pull(in)
      }
    }

    override def preStart(): Unit = IO(Udp).tell(Udp.Bind(self.ref, localAddr), self.ref)

    setHandler(in, new InHandler {
      override def onPush(): Unit = self.ref.tell(grab(in), self.ref)
    })

    setHandler(out, new OutHandler {
      override def onPull(): Unit = if (buffer.nonEmpty) push(out, buffer.dequeue)
    })
  }
}
