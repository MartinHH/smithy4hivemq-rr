package com.example.adapters

import cats.effect.*
import cats.syntax.all.*
import com.comcast.ip4s.host
import com.comcast.ip4s.port
import com.hivemq.client.mqtt.datatypes.MqttQos
import com.hivemq.client.mqtt.mqtt5.Mqtt5Client
import io.gitub.mahh.mqtt.hive.HiveMqCatsRequestClientBuilder
import io.gitub.mahh.mqtt.hive.HiveMqRequestClientBuilder
import io.gitub.mahh.mqtt.hive.adapters.MqttClientAsHttpServer
import org.http4s.ember.server.EmberServerBuilder
import org.http4s.server.Server
import smithy4s.interopcats.monadThrowShim

import scala.concurrent.duration.DurationInt

object MqttClientAsHttpServerMain extends IOApp.Simple {

  val httpPort = port"9000"
  val htppHost = host"localhost"

  val run: IO[Unit] = {
    val mqttClientBuilder =
      Mqtt5Client.builder
        .serverHost("localhost")
        .serverPort(1883)

    val makeClient: HiveMqRequestClientBuilder.Make[IO] =
      HiveMqCatsRequestClientBuilder.make(
        mqttClientBuilder,
        MqttQos.EXACTLY_ONCE,
        5.seconds
      )

    val serverBuilder =
      EmberServerBuilder
        .default[IO]
        .withPort(httpPort)
        .withHost(htppHost)
        .withShutdownTimeout(1.second)

    val message =
      s"Server started on: $htppHost:$httpPort, press enter to stop"

    val server: Resource[IO, Server] =
      for {
        routes <- MqttClientAsHttpServer.buildRoutes(makeClient)(
          hello.HelloWorldService,
          hello.CountService
        )
        builder = serverBuilder.withHttpApp(routes.orNotFound)
        server <- builder.build
      } yield server
    server
      .productL(IO.println(message).toResource)
      .surround(IO.readLine)
      .void
      .guarantee(IO.println("Goodbye!"))
  }
}
