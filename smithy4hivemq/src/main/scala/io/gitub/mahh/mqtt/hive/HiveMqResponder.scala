package io.gitub.mahh.mqtt.hive

import com.hivemq.client.mqtt.datatypes.MqttQos
import com.hivemq.client.mqtt.datatypes.MqttTopic
import com.hivemq.client.mqtt.mqtt5.message.publish.Mqtt5Publish
import com.hivemq.client.mqtt.mqtt5.message.publish.Mqtt5PublishResult
import com.hivemq.client.mqtt.mqtt5.message.subscribe.Mqtt5Subscribe
import com.hivemq.client.mqtt.mqtt5.message.subscribe.Mqtt5Subscription
import io.gitub.mahh.mqtt.RequestHandlerConfig
import io.gitub.mahh.mqtt.RequestHandlers
import io.gitub.mahh.mqtt.logging.Logger
import smithy4s.Blob
import smithy4s.capability.MonadThrowLike

import java.nio.ByteBuffer
import scala.jdk.OptionConverters.*

object HiveMqResponder {

  enum Error(msg: String) extends Throwable(msg) {
    case EmptyPayload extends Error("Got request with empty payload")
    case UnexpectedTopic(topic: MqttTopic)
        extends Error(s"Got request for unexpected topic: $topic")
  }

  private def buildSubscription(topic: MqttTopic): Mqtt5Subscription =
    Mqtt5Subscription
      .builder()
      .topicFilter(topic.filter())
      .qos(MqttQos.EXACTLY_ONCE)
      .build()

  def buildSubscribe(topics: Iterable[MqttTopic]): Mqtt5Subscribe = {
    Mqtt5Subscribe
      .builder()
      .addSubscriptions(topics.map(buildSubscription).toSeq: _*)
      .build()
  }

  private def executeRequest[F[_]](
      request: Blob,
      handler: RequestHandlerConfig[F],
      responseTopic: Option[MqttTopic],
      correlationData: Option[Blob],
      qos: MqttQos,
      publish: Mqtt5Publish => F[Mqtt5PublishResult]
  )(using F: MonadThrowLike[F], logger: Logger[F]): F[Unit] = {
    def respond(response: Blob) = {
      responseTopic.fold(
        logger.debug("Handled a request without response-topic")
      ) { topic =>
        val msg = Mqtt5Publish
          .builder()
          .topic(topic)
          .payload(response.asByteBuffer)
          .correlationData(correlationData.map(_.asByteBuffer).orNull)
          .qos(qos)
          .build()
        F.flatMap(publish(msg)) { pr =>
          pr.getError.toScala.fold(
            logger.debug("Successfully handled a request")
          )(e => F.raiseError(e))
        }
      }
    }

    if (responseTopic.isEmpty && handler.isReadOnly) {
      logger.debug(
        "Ignoring request without response-topic because operation is read-only"
      )
    } else {
      F.flatMap(handler.handler(request)) { resp =>
        respond(resp)
      }
    }

  }

  def handleRequest[F[_]](
      config: RequestHandlers[F, MqttTopic],
      publish: Mqtt5Publish => F[Mqtt5PublishResult]
  )(
      request: Mqtt5Publish
  )(using F: MonadThrowLike[F], logger: Logger[F]): F[Unit] = {
    val topic = request.getTopic
    val handler: F[RequestHandlerConfig[F]] =
      config.handlers
        .get(topic)
        .fold(F.raiseError(Error.UnexpectedTopic(topic)))(h => F.pure(h))
    val payload: F[ByteBuffer] =
      request.getPayload.toScala.fold(F.raiseError(Error.EmptyPayload))(pl =>
        F.pure(pl)
      )
    F.flatMap(handler) { h =>
      F.flatMap(payload) { pl =>
        executeRequest(
          Blob(pl),
          h,
          request.getResponseTopic.toScala,
          request.getCorrelationData.toScala.map(Blob.apply),
          request.getQos,
          publish
        )
      }
    }
  }

}
