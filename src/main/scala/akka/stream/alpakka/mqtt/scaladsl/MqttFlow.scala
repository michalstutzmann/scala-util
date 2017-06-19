package akka.stream.alpakka.mqtt.scaladsl

import akka.stream.alpakka.mqtt._
import akka.stream.scaladsl.Flow

object MqttFlow {
  def apply(connectionSettings: MqttSourceSettings,
            bufferSize: Int,
            qos: MqttQoS): MqttFlow =
    Flow.fromGraph(new MqttFlowStage(connectionSettings, bufferSize: Int, qos))
}
