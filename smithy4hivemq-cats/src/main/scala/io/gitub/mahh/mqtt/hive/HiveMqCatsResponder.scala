package io.gitub.mahh.mqtt.hive

import cats.effect.FiberIO
import cats.effect.IO
import cats.effect.Outcome
import cats.effect.Resource
import cats.effect.kernel.Deferred
import com.hivemq.client.mqtt.MqttGlobalPublishFilter
import com.hivemq.client.mqtt.datatypes.MqttTopic
import com.hivemq.client.mqtt.mqtt5.Mqtt5ClientBuilder
import com.hivemq.client.mqtt.mqtt5.message.publish.Mqtt5Publish
import io.gitub.mahh.mqtt.RequestHandlers
import io.gitub.mahh.mqtt.logging.Logger
import smithy4s.interopcats.monadThrowShim

object HiveMqCatsResponder {

  def resource(
      config: RequestHandlers[IO, MqttTopic],
      clientBuilder: Mqtt5ClientBuilder
  )(using logger: Logger[IO]): Resource[IO, FiberIO[Unit]] = {
    val client = HiveMqCatsClient(clientBuilder.buildAsync())

    val onPublishesSubscribed: IO[Unit] =
      client.connect >>
        client
          .subscribe(HiveMqResponder.buildSubscribe(config.handlers.keys))
          .void

    def handleRequest(msg: Mqtt5Publish): IO[Unit] =
      HiveMqResponder
        .handleRequest[IO](config, client.publish)(msg)
        .recoverWith { case e =>
          logger.error(e)("Caught unhandled error during request-handling")
        }

    val publishesFibre: Resource[IO, FiberIO[Unit]] =
      Deferred[IO, Either[Throwable, Unit]].toResource.flatMap { token =>

        val publishes: IO[Unit] =
          client
            .publishes(MqttGlobalPublishFilter.ALL, onPublishesSubscribed)
            .evalMap(handleRequest)
            .interruptWhen(token)
            .compile
            .drain
            .guaranteeCase {
              case Outcome.Errored(e) =>
                logger.error(e)("error on incoming subscription")
              case _ => IO.unit
            }

        Resource.make(publishes.start)(_ =>
          token.complete(Right(())) >> client.disconnect
        )
      }
    publishesFibre
  }
}
