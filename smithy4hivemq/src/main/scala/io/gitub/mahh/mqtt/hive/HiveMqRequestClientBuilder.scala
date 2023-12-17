package io.gitub.mahh.mqtt.hive

import com.hivemq.client.mqtt.MqttGlobalPublishFilter
import com.hivemq.client.mqtt.datatypes.MqttQos
import com.hivemq.client.mqtt.datatypes.MqttTopic
import com.hivemq.client.mqtt.mqtt5.Mqtt5ClientBuilder
import com.hivemq.client.mqtt.mqtt5.message.publish.Mqtt5Publish
import com.hivemq.client.mqtt.mqtt5.message.subscribe.Mqtt5Subscribe
import com.hivemq.client.mqtt.mqtt5.message.subscribe.Mqtt5Subscription
import io.gitub.mahh.mqtt.RequestClientBuilder
import io.gitub.mahh.mqtt.RequestHandlerBuilderError
import io.gitub.mahh.mqtt.RequestServiceBuilder.Result
import io.reactivex.disposables.Disposable
import smithy4s.Blob
import smithy4s.Service
import smithy4s.capability.MonadThrowLike
import smithy4s.client.UnaryLowLevelClient

import java.util.UUID
import scala.concurrent.Await
import scala.concurrent.Promise
import scala.concurrent.duration.Duration
import scala.jdk.OptionConverters.*
import scala.util.Try

object HiveMqRequestClientBuilder extends RequestClientBuilder[MqttTopic] {

  type BlockingResult[T] = Either[Throwable, T]

  def blocking[Alg[_[_, _, _, _, _]]](
      service: smithy4s.Service[Alg],
      clientBuilder: Mqtt5ClientBuilder,
      qos: MqttQos,
      timeout: Duration,
      createResponseTopic: MqttTopic => MqttTopic = _ => MqttTopic.of("foo/bar")
  )(using
      MonadThrowLike[BlockingResult]
  ): Result[service.Impl[BlockingResult]] = {
    makeClient[Alg, BlockingResult, RequestClient](
      service,
      RequestClient(clientBuilder: Mqtt5ClientBuilder),
      _.toBlockingSmithy4sSyncClient(
        timeout,
        qos,
        createResponseTopic
      ),
      (c, t) => c.copy(topic = t)
    )
  }

  def subscribeAndRequest(
      qos: MqttQos,
      requestTopic: MqttTopic,
      responseTopic: MqttTopic,
      requestPayload: Blob
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
      .qos(qos)
      .build()

    subscribe -> req
  }

  private case class RequestClient(
      clientBuilder: Mqtt5ClientBuilder,
      topic: Option[String] = None
  ) {

    def toBlockingSmithy4sSyncClient(
        timeout: Duration,
        qos: MqttQos,
        createResponseTopic: MqttTopic => MqttTopic
    ): UnaryLowLevelClient[BlockingResult, Blob, Blob] =
      new UnaryLowLevelClient[BlockingResult, Blob, Blob] {
        override def run[Output](
            request: Blob
        )(
            responseCB: Blob => BlockingResult[Output]
        ): BlockingResult[Output] = {
          val topicResult: BlockingResult[MqttTopic] =
            topic.fold[BlockingResult[MqttTopic]] {
              // should be unreachable
              Left(IllegalStateException("run called without topic"))
            }(parseHiveMqTopic)

          topicResult.flatMap { t =>
            val responseTopic = createResponseTopic(t)
            val client = clientBuilder
              .identifier(s"FutureClient-${UUID.randomUUID()}")
              .buildBlocking()

            val (subscribe, msg) =
              subscribeAndRequest(qos, t, responseTopic, request)

            val p: Promise[Mqtt5Publish] = Promise()

            val disposable: Disposable = client.toRx
              .publishes(MqttGlobalPublishFilter.SUBSCRIBED)
              .subscribe(
                (publish: Mqtt5Publish) => {
                  // TODO: correlation data
                  if (publish.getTopic == responseTopic) {
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
              val blob = publish.getPayload.toScala.fold(Blob.empty)(Blob.apply)
              responseCB(blob)
            }
          }

        }
      }

  }

  override protected def parseTopic(
      topicString: String
  ): Either[RequestHandlerBuilderError.InvalidTopic, MqttTopic] =
    parseHiveMqTopic(topicString)

}
