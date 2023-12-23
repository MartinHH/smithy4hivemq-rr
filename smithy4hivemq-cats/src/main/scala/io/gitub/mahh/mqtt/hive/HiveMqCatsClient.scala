package io.gitub.mahh.mqtt.hive

import cats.effect.IO
import com.hivemq.client.mqtt.MqttGlobalPublishFilter
import com.hivemq.client.mqtt.mqtt5.Mqtt5AsyncClient
import com.hivemq.client.mqtt.mqtt5.message.connect.connack.Mqtt5ConnAck
import com.hivemq.client.mqtt.mqtt5.message.publish.Mqtt5Publish
import com.hivemq.client.mqtt.mqtt5.message.publish.Mqtt5PublishResult
import com.hivemq.client.mqtt.mqtt5.message.subscribe.Mqtt5Subscribe
import com.hivemq.client.mqtt.mqtt5.message.subscribe.suback.Mqtt5SubAck
import fs2.*
import fs2.interop.flow.fromPublisher
import org.reactivestreams.FlowAdapters

class HiveMqCatsClient(private val underlying: Mqtt5AsyncClient)
    extends AnyVal {

  def connect: IO[Mqtt5ConnAck] =
    IO.fromCompletableFuture(IO(underlying.connect()))

  def disconnect: IO[Unit] =
    IO.fromCompletableFuture(IO(underlying.disconnect())).void

  def publish(publish: Mqtt5Publish): IO[Mqtt5PublishResult] =
    IO.fromCompletableFuture(IO(underlying.publish(publish)))

  def subscribe(subscribe: Mqtt5Subscribe): IO[Mqtt5SubAck] =
    IO.fromCompletableFuture(IO(underlying.subscribe(subscribe)))

  def publishes(
      filter: MqttGlobalPublishFilter,
    onSubscribe: IO[Unit] = IO.unit,
    bufferSize: Int = 1
  ): Stream[IO, Mqtt5Publish] = {
    fromPublisher(bufferSize) { subscriber =>
      IO.delay {
        underlying.toRx
          .publishes(filter)
          .subscribe(FlowAdapters.toSubscriber(subscriber))
      } >> onSubscribe
    }
  }

}
