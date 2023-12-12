package io.gitub.mahh.mqtt.hive

import com.hivemq.client.mqtt.datatypes.MqttTopic
import io.gitub.mahh.mqtt.RequestHandlerBuilderError
import io.gitub.mahh.mqtt.RequestHandlerBuilderError.InvalidTopic
import io.gitub.mahh.mqtt.RequestServiceBuilder

import scala.util.Try

object HiveMqRequestServiceBuilder extends RequestServiceBuilder[MqttTopic] {
  override protected def parseTopic(
      topicString: String
  ): Either[InvalidTopic, MqttTopic] =
    Try(MqttTopic.of(topicString)).toEither.left.map(e =>
      InvalidTopic(topicString, Some(e))
    )
}
