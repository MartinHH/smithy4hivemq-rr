package com.example.hivemq

import cats.effect.*
import cats.syntax.all.*
import com.example.services.CountImpl
import com.example.services.HelloWorldImpl
import com.hivemq.client.mqtt.datatypes.MqttTopic
import com.hivemq.client.mqtt.mqtt5.Mqtt5Client
import com.hivemq.client.mqtt.mqtt5.Mqtt5ClientBuilder
import com.hivemq.client.mqtt.mqtt5.message.subscribe.suback.Mqtt5SubAck
import hello.CountService
import hello.HelloWorldService
import io.gitub.mahh.mqtt.RequestHandlers
import io.gitub.mahh.mqtt.hive.HiveMqCatsResponder
import io.gitub.mahh.mqtt.hive.HiveMqRequestServiceBuilder
import io.gitub.mahh.mqtt.logging.Logger
import io.gitub.mahh.mqtt.logging.log4cats.loggingFromLog4Cats
import org.typelevel.log4cats.Logger as Log4CatsLogger
import org.typelevel.log4cats.slf4j.Slf4jLogger
import smithy4s.interopcats.monadThrowShim

object Main extends IOApp.Simple {

  def responderResource(
    helloWorldService: HelloWorldService[IO],
    countService: CountService[IO],
      clientBuilder: Mqtt5ClientBuilder
  )(using Logger[IO]): Resource[IO, Mqtt5SubAck] = {
    val config: IO[RequestHandlers[IO, MqttTopic]] =
      for {
        hw <- HiveMqRequestServiceBuilder.handlers(helloWorldService).liftTo[IO]
        c <- HiveMqRequestServiceBuilder.handlers(countService).liftTo[IO]
        combined <- hw.combine(c).liftTo[IO]
      } yield combined
    for {
      conf <- config.toResource
      responder <- HiveMqCatsResponder.resource(conf, clientBuilder)
    } yield responder
  }

  val run: IO[Unit] = {

    given Log4CatsLogger[IO] = Slf4jLogger.getLogger[IO]

    val builder =
      Mqtt5Client.builder
        .identifier("cats-based service (mqtt only)")
        .serverHost("localhost")
        .serverPort(1883)

    val responder: Resource[IO, Mqtt5SubAck] =
      for {
        counter <- Ref.of[IO, Int](0).toResource
        helloWorldService = HelloWorldImpl()
        countService = CountImpl(counter)
        r <- responderResource(helloWorldService, countService, builder)
      } yield r

    val printStarted: IO[Unit] =
      IO.println("Service started - press enter to abort")

    responder
      .productL(printStarted.toResource)
      .surround(IO.readLine)
      .void
      .guarantee(IO.println("Goodbye!"))
  }
}
