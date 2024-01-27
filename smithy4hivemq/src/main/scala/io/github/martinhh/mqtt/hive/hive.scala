package io.github.martinhh.mqtt.hive

import com.hivemq.client.mqtt.datatypes.MqttTopic
import com.hivemq.client.mqtt.mqtt5.message.publish.Mqtt5Publish
import com.hivemq.client.mqtt.mqtt5.message.publish.Mqtt5PublishResult
import io.github.martinhh.mqtt.RequestHandlerBuilderError.InvalidTopic
import smithy4s.Blob

import java.nio.ByteBuffer
import scala.jdk.OptionConverters.*
import scala.util.Try

private[hive] def parseHiveMqTopic(
    topicString: String
): Either[InvalidTopic, MqttTopic] =
  Try(MqttTopic.of(topicString)).toEither.left.map(e =>
    InvalidTopic(topicString, Some(e))
  )

extension (publish: Mqtt5Publish)
  def payloadBlob: Blob =
    publish.getPayload.toScala.fold(Blob.empty)(Blob.apply)

  def responseTopic: Option[MqttTopic] = publish.getResponseTopic.toScala

  def correlationData: Option[Blob] =
    publish.getCorrelationData.toScala.map(Blob.apply)

  def isResponse(responseTopic: MqttTopic, correlationData: Blob): Boolean =
    publish.getTopic == responseTopic &&
      publish.correlationData.exists(_.sameBytesAs(correlationData))

extension (publishResult: Mqtt5PublishResult)
  def error: Option[Throwable] = publishResult.getError.toScala
