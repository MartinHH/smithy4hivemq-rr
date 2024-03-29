package io.github.martinhh.mqtt.hive

import cats.effect.*
import com.hivemq.client.mqtt.MqttGlobalPublishFilter
import com.hivemq.client.mqtt.datatypes.MqttQos
import com.hivemq.client.mqtt.datatypes.MqttTopic
import com.hivemq.client.mqtt.mqtt5.Mqtt5ClientBuilder
import com.hivemq.client.mqtt.mqtt5.message.publish.Mqtt5PublishResult
import io.github.martinhh.mqtt.hive.*
import HiveMqRequestClientBuilder.RequestClient
import HiveMqRequestClientBuilder.subscribeAndRequest
import smithy4s.Blob
import smithy4s.capability.MonadThrowLike
import smithy4s.client.UnaryLowLevelClient

import scala.concurrent.duration.FiniteDuration

object HiveMqCatsRequestClientBuilder {

  private def toIOSmithy4sClient(
      timeout: FiniteDuration,
      qos: MqttQos,
    createResponseTopic: MqttTopic => MqttTopic,
    correlationDataProvider: () => Blob
  )(
      requestClient: RequestClient
  ): UnaryLowLevelClient[IO, Blob, Blob] =
    new UnaryLowLevelClient[IO, Blob, Blob] {
      override def run[Output](
          request: Blob
      )(responseCB: Blob => IO[Output]): IO[Output] = {
        val topic: IO[MqttTopic] = IO.fromEither(requestClient.topicResult)
        val client =
          HiveMqCatsClient(requestClient.randomIdClientBuilder.buildAsync())

        val correlationData = correlationDataProvider()

        def response(
            responseTopic: MqttTopic,
            sendRequest: IO[Unit]
        ): IO[Output] =
          client
            .publishes(MqttGlobalPublishFilter.SUBSCRIBED, sendRequest)
            .collectFirst {
              case r if r.isResponse(responseTopic, correlationData) =>
                r.payloadBlob
            }
            .evalMap(responseCB)
            .timeout(timeout)
            .compile
            .onlyOrError

        def connectAndSend(
            requestTopic: MqttTopic,
            responseTopic: MqttTopic
        ): IO[Mqtt5PublishResult] = {
          val (subscribe, msg) =
            HiveMqRequestClientBuilder.subscribeAndRequest(
              qos,
              requestTopic,
              responseTopic,
              request,
              correlationData
            )
          for {
            _ <- client.connect
            _ <- client.subscribe(subscribe)
            pubRes <- client.publish(msg)
          } yield pubRes
        }

        val result =
          for {
            t <- topic
            responseTopic = createResponseTopic(t)
            sendRequest = connectAndSend(t, responseTopic)
            s <- response(responseTopic, sendRequest.void)
          } yield s
        result.guarantee(client.disconnect)
      }
    }

  def make(
      clientBuilder: Mqtt5ClientBuilder,
      qos: MqttQos,
      timeout: FiniteDuration,
    createResponseTopic: MqttTopic => MqttTopic = _ =>
      MqttTopic.of("foo/bar"),
    correlationDataProvider: () => Blob =
    HiveMqRequestClientBuilder.defaultCorrelationDataProvider
  )(using
      MonadThrowLike[IO]
  ): HiveMqRequestClientBuilder.Make[IO] = HiveMqRequestClientBuilder.make(
    RequestClient(clientBuilder),
    toIOSmithy4sClient(
      timeout,
      qos,
      createResponseTopic,
      correlationDataProvider
    ),
    (c, t) => c.copy(topic = t)
  )
}
