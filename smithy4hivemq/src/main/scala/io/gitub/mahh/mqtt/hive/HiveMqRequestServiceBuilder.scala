package io.gitub.mahh.mqtt.hive

import com.hivemq.client.mqtt.datatypes.MqttTopic
import io.gitub.mahh.mqtt.RequestHandlerBuilderError
import io.gitub.mahh.mqtt.RequestHandlerBuilderError.InvalidTopic
import io.gitub.mahh.mqtt.RequestServiceBuilder

object HiveMqRequestServiceBuilder extends RequestServiceBuilder[MqttTopic] {
  override protected def parseTopic(
      topicString: String
  ): Either[InvalidTopic, MqttTopic] =
    parseHiveMqTopic(topicString)
}
