package akka.stream.alpakka.mqtt

import java.util.concurrent.Semaphore

import akka.Done
import akka.stream._
import akka.stream.stage._
import org.eclipse.paho.client.mqttv3.{ IMqttAsyncClient, IMqttToken, MqttMessage => PahoMqttMessage }

import scala.collection.mutable
import scala.concurrent.{ Future, Promise }
import scala.util.{ Failure, Success, Try }

object MqttFlowStage {
  final object NoClientException extends Exception("No MQTT client.")
}

class MqttFlowStage(sourceSettings: MqttSourceSettings, bufferSize: Int, qos: MqttQoS)
    extends GraphStageWithMaterializedValue[FlowShape[MqttMessage, MqttMessage], Future[Done]] {
  import MqttFlowStage.NoClientException
  import MqttConnectorLogic._

  private val in = Inlet[MqttMessage](s"MqttFlow.in")
  private val out = Outlet[MqttMessage](s"MqttFlow.out")
  override val shape = FlowShape.of(in, out)
  override protected def initialAttributes: Attributes = Attributes.name("MqttFlow")

  override def createLogicAndMaterializedValue(inheritedAttributes: Attributes): (GraphStageLogic, Future[Done]) = {
    val subscriptionPromise = Promise[Done]

    (new GraphStageLogic(shape) with MqttConnectorLogic {
      private val queue = mutable.Queue[MqttMessage]()

      private val mqttSubscriptionCallback: Try[IMqttToken] => Unit = { conn =>
        subscriptionPromise.complete(conn.map { _ =>
          Done
        })
        pull(in)
      }

      private val backpressure = new Semaphore(bufferSize)

      private var mqttClient: Option[IMqttAsyncClient] = None

      private val onMessage = getAsyncCallback[MqttMessage] { message =>
        require(queue.size <= bufferSize)
        if (isAvailable(out)) {
          pushMessage(message)
        } else {
          queue.enqueue(message)
        }
      }

      private val onPublished = getAsyncCallback[Try[IMqttToken]] {
        case Success(token) => pull(in)
        case Failure(ex) => failStage(ex)
      }

      override val connectionSettings: MqttConnectionSettings = sourceSettings.connectionSettings

      setHandler(
        in,
        new InHandler {
          override def onPush(): Unit = {
            val msg = grab(in)
            val pahoMsg = new PahoMqttMessage(msg.payload.toArray)
            pahoMsg.setQos(qos.byteValue)
            mqttClient match {
              case Some(client) =>
                client.publish(msg.topic, pahoMsg, msg, onPublished.invoke _)
              case None => failStage(NoClientException)
            }
          }
        }
      )

      setHandler(
        out,
        new OutHandler {
          override def onPull(): Unit =
            if (queue.nonEmpty) {
              pushMessage(queue.dequeue())
            }
        }
      )

      override def handleConnection(client: IMqttAsyncClient): Unit = {
        val (topics, qos) = sourceSettings.subscriptions.unzip
        mqttClient = Some(client)
        if (topics.nonEmpty) {
          client.subscribe(topics.toArray, qos.map(_.byteValue.toInt).toArray, (), mqttSubscriptionCallback)
        } else {
          subscriptionPromise.complete(Success(Done))
          pull(in)
        }
      }

      override def onMessage(message: MqttMessage): Unit = {
        backpressure.acquire()
        onMessage.invoke(message)
      }

      def pushMessage(message: MqttMessage): Unit = {
        push(out, message)
        backpressure.release()
      }

      override def handleConnectionLost(ex: Throwable): Unit = {
        failStage(ex)
        subscriptionPromise.tryFailure(ex)
      }

      override def postStop(): Unit = mqttClient foreach { c =>
        Try(c.close())
      }

    }, subscriptionPromise.future)
  }
}
