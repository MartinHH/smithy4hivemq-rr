package io.gitub.mahh.mqtt.hive.adapters

import cats.effect.*
import cats.syntax.all.*
import io.gitub.mahh.mqtt.hive.HiveMqRequestClientBuilder
import org.http4s.HttpRoutes
import smithy4s.http4s.SimpleRestJsonBuilder

object MqttClientAsHttpServer {

  /** Builds http routes that delegate to an mqtt client.
    */
  def buildRoutes(
      makeClient: HiveMqRequestClientBuilder.Make[IO],
      withDocs: Boolean = true
  )(
      service: smithy4s.Service[_],
      moreServices: smithy4s.Service[_]*
  ): Resource[IO, HttpRoutes[IO]] = {
    def buildOne(service: smithy4s.Service[_]): Resource[IO, HttpRoutes[IO]] =
      IO.fromEither(makeClient(service).map(service -> _)).toResource.flatMap {
        case (s: service.type, impl: service.Impl[IO]) =>
          given service.type = s
          SimpleRestJsonBuilder.routes(impl).resource
      }

    val services: Resource[IO, HttpRoutes[IO]] =
      moreServices.foldLeft(buildOne(service)) { (routes, service) =>
        routes <+> buildOne(service)
      }
    def docs: HttpRoutes[IO] =
      smithy4s.http4s.swagger.docs[IO](service, moreServices: _*)

    if (withDocs) services.map(_ <+> docs) else services
  }

}
