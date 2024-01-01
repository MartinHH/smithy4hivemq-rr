package com.example.adapters

import cats.effect.*
import com.hivemq.client.mqtt.mqtt5.Mqtt5Client
import io.gitub.mahh.mqtt.hive.HiveMqCatsResponder
import io.gitub.mahh.mqtt.hive.adapters.HttpClientAsMqttServer
import io.gitub.mahh.mqtt.logging.log4cats.loggingFromLog4Cats
import org.http4s.Uri
import org.http4s.ember.client.EmberClientBuilder
import org.typelevel.log4cats.Logger as Log4CatsLogger
import org.typelevel.log4cats.slf4j.Slf4jLogger
import smithy4s.interopcats.monadThrowShim

object HttpClientAsMqttServerMain extends IOApp.Simple {

  val run: IO[Unit] = {
    given Log4CatsLogger[IO] = Slf4jLogger.getLogger[IO]

    val clientBuilder =
      Mqtt5Client.builder
        .identifier("cats-based client for http-server")
        .serverHost("localhost")
        .serverPort(1883)

    val responder: Resource[IO, FiberIO[Unit]] =
      for {
        client <- EmberClientBuilder.default[IO].build
        handlers <- HttpClientAsMqttServer.buildRequestHandlers(
          client,
          Uri.unsafeFromString("http://localhost:9000")
        )(hello.HelloWorldService, hello.CountService)
        fibre <- HiveMqCatsResponder.resource(handlers, clientBuilder)
      } yield fibre
    val printStarted: IO[Unit] =
      IO.println("Service started - press enter to abort")

    responder
      .use(_.join)
      .race(printStarted >> IO.readLine)
      .void
      .guarantee(IO.println("Goodbye!"))
  }
}
