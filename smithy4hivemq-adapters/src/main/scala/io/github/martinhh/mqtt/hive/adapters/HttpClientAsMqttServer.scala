package io.github.martinhh.mqtt.hive.adapters

import cats.effect.*
import com.hivemq.client.mqtt.datatypes.MqttTopic
import io.github.martinhh.mqtt.RequestHandlers
import io.github.martinhh.mqtt.RequestHandlers
import io.github.martinhh.mqtt.hive.HiveMqRequestServiceBuilder
import io.github.martinhh.mqtt.hive.HiveMqRequestServiceBuilder
import io.github.martinhh.mqtt.logging.Logger
import io.github.martinhh.mqtt.logging.Logger
import org.http4s.Uri
import org.http4s.client.Client
import smithy4s.capability.MonadThrowLike
import smithy4s.http4s.SimpleRestJsonBuilder

object HttpClientAsMqttServer {

  /** Builds mqtt-request-handlers that delegate to an http-client.
    */
  def buildRequestHandlers(
      http4sClient: Client[IO],
      serverUri: Uri,
      maxArity: Int = 2048
  )(
      service: smithy4s.Service[_],
      moreServices: smithy4s.Service[_]*
  )(using
      Logger[IO],
      MonadThrowLike[IO]
  ): Resource[IO, RequestHandlers[IO, MqttTopic]] = {
    def buildOne(
        service: smithy4s.Service[_]
    ): Resource[IO, RequestHandlers[IO, MqttTopic]] =
      val builder = SimpleRestJsonBuilder
        .withMaxArity(maxArity)
        .apply(service)
        .client(http4sClient)
        .uri(serverUri)
      builder.resource
        .flatMap { impl =>
          given service.type = service
          IO.fromEither(HiveMqRequestServiceBuilder.handlers(impl)).toResource
        }

    moreServices.foldLeft(buildOne(service)) { (result, service) =>
      for {
        lhs <- result
        rhs <- buildOne(service)
        combined <- IO.fromEither(lhs.combine(rhs)).toResource
      } yield combined
    }
  }
}
