package io.gitub.mahh.mqtt.hive

import com.hivemq.client.mqtt.datatypes.MqttTopic
import io.gitub.mahh.mqtt.RequestHandlerBuilderError.InvalidTopic

import scala.util.Try

private[hive] def parseHiveMqTopic(
    topicString: String
): Either[InvalidTopic, MqttTopic] =
  Try(MqttTopic.of(topicString)).toEither.left.map(e =>
    InvalidTopic(topicString, Some(e))
  )
