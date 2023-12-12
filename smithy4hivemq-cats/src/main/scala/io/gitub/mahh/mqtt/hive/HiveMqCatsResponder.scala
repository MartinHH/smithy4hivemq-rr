package io.gitub.mahh.mqtt.hive

import cats.effect.FiberIO
import cats.effect.IO
import cats.effect.Resource
import cats.effect.kernel.Deferred
import com.hivemq.client.mqtt.MqttGlobalPublishFilter
import com.hivemq.client.mqtt.datatypes.MqttTopic
import com.hivemq.client.mqtt.mqtt5.Mqtt5ClientBuilder
import com.hivemq.client.mqtt.mqtt5.message.subscribe.suback.Mqtt5SubAck
import io.gitub.mahh.mqtt.RequestHandlers
import io.gitub.mahh.mqtt.logging.Logger
import smithy4s.interopcats.monadThrowShim

object HiveMqCatsResponder {

  def resource(
      config: RequestHandlers[IO, MqttTopic],
      clientBuilder: Mqtt5ClientBuilder
  )(using logger: Logger[IO]): Resource[IO, Mqtt5SubAck] = {
    val client = HiveMqCatsClient(clientBuilder.buildAsync())

    val subscribe: Resource[IO, Mqtt5SubAck] =
      client
        .subscribe(HiveMqResponder.buildSubscribe(config.handlers.keys))
        .toResource

    val publishesFibre: Resource[IO, FiberIO[Unit]] =
      Deferred[IO, Either[Throwable, Unit]].toResource.flatMap { token =>

        val publishes: IO[Unit] = client
          .publishes(MqttGlobalPublishFilter.ALL, 1)
          .evalMap { msg =>
            HiveMqResponder.handleRequest[IO](config, client.publish)(msg)
          }
          .interruptWhen(token)
          .compile
          .drain

        Resource.make(publishes.start)(_ => token.complete(Right(())).void)
      }

    for {
      _ <- publishesFibre
      _ <- client.connectedResource
      subAck <- subscribe
    } yield subAck

  }
}
