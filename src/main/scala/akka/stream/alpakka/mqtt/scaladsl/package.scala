package akka.stream.alpakka.mqtt

import akka.Done
import akka.stream.scaladsl.Flow
import scala.concurrent.Future

package object scaladsl {
  type MqttFlow = Flow[MqttMessage, MqttMessage, Future[Done]]
}
