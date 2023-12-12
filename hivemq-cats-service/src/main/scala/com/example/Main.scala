package com.example

import cats.effect.*
import cats.syntax.all.*
import com.example.services.CountImpl
import com.example.services.HelloWorldImpl
import com.hivemq.client.mqtt.datatypes.MqttTopic
import com.hivemq.client.mqtt.mqtt5.Mqtt5Client
import io.gitub.mahh.mqtt.RequestHandlers
import io.gitub.mahh.mqtt.hive.HiveMqCatsResponder
import io.gitub.mahh.mqtt.hive.HiveMqRequestServiceBuilder
import smithy4s.interopcats.monadThrowShim

object Main extends IOApp.Simple {

  val run: IO[Unit] = {

    val config: IO[RequestHandlers[IO, MqttTopic]] =
      for {
        counter <- Ref.of[IO, Int](0)
        helloWorldService = HelloWorldImpl()
        countService = CountImpl(counter)
        hw <- HiveMqRequestServiceBuilder.handlers(helloWorldService).liftTo[IO]
        c <- HiveMqRequestServiceBuilder.handlers(countService).liftTo[IO]
        combined <- hw.combine(c).liftTo[IO]
      } yield combined

    val printStarted: IO[Unit] =
      IO.println("Service started - press enter to abort")

    config
      .flatMap { conf =>
        val builder =
          Mqtt5Client.builder
            .identifier("cats-based service (mqtt only)")
            .serverHost("localhost")
            .serverPort(1883)
        HiveMqCatsResponder
          .resource(conf, builder)
          .productL(printStarted.toResource)
          .surround(IO.readLine)
          .void
          .guarantee(IO.println("Goodbye!"))
      }
  }
}
