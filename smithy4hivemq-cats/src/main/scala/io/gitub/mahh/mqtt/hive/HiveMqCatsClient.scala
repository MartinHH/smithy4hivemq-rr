package io.gitub.mahh.mqtt.hive

import cats.effect.IO
import cats.effect.Resource
import com.hivemq.client.mqtt.MqttGlobalPublishFilter
import com.hivemq.client.mqtt.mqtt5.Mqtt5AsyncClient
import com.hivemq.client.mqtt.mqtt5.message.connect.connack.Mqtt5ConnAck
import com.hivemq.client.mqtt.mqtt5.message.publish.Mqtt5Publish
import com.hivemq.client.mqtt.mqtt5.message.publish.Mqtt5PublishResult
import com.hivemq.client.mqtt.mqtt5.message.subscribe.Mqtt5Subscribe
import com.hivemq.client.mqtt.mqtt5.message.subscribe.suback.Mqtt5SubAck
import fs2.*
import fs2.interop.reactivestreams.*

class HiveMqCatsClient(private val underlying: Mqtt5AsyncClient)
    extends AnyVal {

  def connect: IO[Mqtt5ConnAck] =
    IO.fromCompletableFuture(IO(underlying.connect()))

  def disconnect: IO[Unit] =
    IO.fromCompletableFuture(IO(underlying.disconnect())).void

  def connectedResource: Resource[IO, Mqtt5ConnAck] =
    Resource.make(connect)(_ => disconnect)

  def publish(publish: Mqtt5Publish): IO[Mqtt5PublishResult] =
    IO.fromCompletableFuture(IO(underlying.publish(publish)))

  def publishes(
      filter: MqttGlobalPublishFilter,
      bufferSize: Int = 16
  ): Stream[IO, Mqtt5Publish] =
    underlying.toRx.publishes(filter).toStreamBuffered(bufferSize)

  def subscribe(subscribe: Mqtt5Subscribe): IO[Mqtt5SubAck] =
    IO.fromCompletableFuture(IO(underlying.subscribe(subscribe)))

}
