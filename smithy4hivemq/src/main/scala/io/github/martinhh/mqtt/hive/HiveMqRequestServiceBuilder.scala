package io.github.martinhh.mqtt.hive

import com.hivemq.client.mqtt.datatypes.MqttTopic
import io.github.martinhh.mqtt.RequestServiceBuilder
import io.github.martinhh.mqtt.RequestHandlerBuilderError
import io.github.martinhh.mqtt.RequestHandlerBuilderError.InvalidTopic

object HiveMqRequestServiceBuilder extends RequestServiceBuilder[MqttTopic] {
  override protected def parseTopic(
      topicString: String
  ): Either[InvalidTopic, MqttTopic] =
    parseHiveMqTopic(topicString)
}
