package io.github.martinhh.mqtt.hive

import com.hivemq.client.mqtt.MqttGlobalPublishFilter
import com.hivemq.client.mqtt.datatypes.MqttQos
import com.hivemq.client.mqtt.datatypes.MqttTopic
import com.hivemq.client.mqtt.mqtt5.Mqtt5ClientBuilder
import com.hivemq.client.mqtt.mqtt5.message.publish.Mqtt5Publish
import com.hivemq.client.mqtt.mqtt5.message.subscribe.Mqtt5Subscribe
import com.hivemq.client.mqtt.mqtt5.message.subscribe.Mqtt5Subscription
import io.github.martinhh.mqtt.RequestClientBuilder
import io.github.martinhh.mqtt.RequestHandlerBuilderError.InvalidTopic
import io.github.martinhh.mqtt.RequestHandlerBuilderError
import io.reactivex.disposables.Disposable
import smithy4s.Blob
import smithy4s.capability.MonadThrowLike
import smithy4s.client.UnaryLowLevelClient

import java.util.UUID
import scala.concurrent.Await
import scala.concurrent.Promise
import scala.concurrent.duration.Duration
import scala.util.Try

object HiveMqRequestClientBuilder extends RequestClientBuilder[MqttTopic] {

  type BlockingResult[T] = Either[Throwable, T]

  val defaultCorrelationDataProvider: () => Blob = () =>
    Blob(UUID.randomUUID().toString)

  def makeBlocking(
      clientBuilder: Mqtt5ClientBuilder,
      qos: MqttQos,
      timeout: Duration,
    createResponseTopic: MqttTopic => MqttTopic = _ =>
      MqttTopic.of("foo/bar"),
    correlationDataProvider: () => Blob = defaultCorrelationDataProvider
  )(using
      MonadThrowLike[BlockingResult]
  ): Make[BlockingResult] =
    make(
      RequestClient(clientBuilder),
      _.toBlockingSmithy4sSyncClient(
        timeout,
        qos,
        createResponseTopic,
        correlationDataProvider
      ),
      (c, t) => c.copy(topic = t)
    )

  def subscribeAndRequest(
      qos: MqttQos,
      requestTopic: MqttTopic,
      responseTopic: MqttTopic,
    requestPayload: Blob,
    correlationData: Blob
  ): (Mqtt5Subscribe, Mqtt5Publish) = {
    val subscribe = {
      val subscr =
        Mqtt5Subscription
          .builder()
          .topicFilter(responseTopic.filter())
          .qos(qos)
          .build()
      Mqtt5Subscribe
        .builder()
        .addSubscription(subscr)
        .build()
    }
    val req = Mqtt5Publish
      .builder()
      .topic(requestTopic)
      .payload(requestPayload.asByteBuffer)
      .responseTopic(responseTopic)
      .correlationData(correlationData.asByteBuffer)
      .qos(qos)
      .build()

    subscribe -> req
  }

  private[hive] case class RequestClient(
      clientBuilder: Mqtt5ClientBuilder,
      topic: Option[String] = None
  ) {

    def topicResult: BlockingResult[MqttTopic] =
      topic.fold[BlockingResult[MqttTopic]] {
        // should be unreachable if called from makeClient
        Left(IllegalStateException("run called without topic"))
      }(parseHiveMqTopic)

    def randomIdClientBuilder: Mqtt5ClientBuilder =
      clientBuilder
        .identifier(s"FutureClient-${UUID.randomUUID()}")

    def toBlockingSmithy4sSyncClient(
        timeout: Duration,
        qos: MqttQos,
      createResponseTopic: MqttTopic => MqttTopic,
      correlationDataProvider: () => Blob
    ): UnaryLowLevelClient[BlockingResult, Blob, Blob] =
      new UnaryLowLevelClient[BlockingResult, Blob, Blob] {
        override def run[Output](
            request: Blob
        )(
            responseCB: Blob => BlockingResult[Output]
        ): BlockingResult[Output] = {

          topicResult.flatMap { t =>
            val responseTopic = createResponseTopic(t)
            val client =
              randomIdClientBuilder
                .buildBlocking()

            val correlationData = correlationDataProvider()
            val (subscribe, msg) =
              subscribeAndRequest(
                qos,
                t,
                responseTopic,
                request,
                correlationData
              )

            val p: Promise[Mqtt5Publish] = Promise()

            val disposable: Disposable = client.toRx
              .publishes(MqttGlobalPublishFilter.SUBSCRIBED)
              .subscribe(
                (publish: Mqtt5Publish) => {
                  if (publish.isResponse(responseTopic, correlationData)) {
                    p.trySuccess(publish)
                  }
                },
                (t: Throwable) => {
                  p.tryFailure(t)
                }
              )
            client.connect()
            client.subscribe(subscribe)
            client.publish(msg)
            val received = Try(Await.result(p.future, timeout)).toEither
            disposable.dispose()
            client.disconnect()

            received.flatMap { publish =>
              responseCB(publish.payloadBlob)
            }
          }

        }
      }

  }

  override protected def parseTopic(
      topicString: String
  ): Either[InvalidTopic, MqttTopic] =
    parseHiveMqTopic(topicString)

}
