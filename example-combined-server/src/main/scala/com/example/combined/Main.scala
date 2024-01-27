package com.example.combined

import cats.effect.IO
import cats.effect.IOApp
import cats.effect.Ref
import cats.syntax.all.*
import com.example.hivemq.Main as HiveMqExample
import com.example.http4s.Main as Http4sExample
import com.example.services.CountImpl
import com.example.services.HelloWorldImpl
import com.hivemq.client.mqtt.mqtt5.Mqtt5Client
import io.github.martinhh.mqtt.logging.log4cats.loggingFromLog4Cats
import org.typelevel.log4cats.Logger as Log4CatsLogger
import org.typelevel.log4cats.slf4j.Slf4jLogger

object Main extends IOApp.Simple {
  val run: IO[Unit] = {

    Ref
      .of[IO, Int](0)
      .toResource
      .flatMap { counter =>
        val hw = HelloWorldImpl()
        val c = CountImpl(counter)
        val mqtt = {
          given Log4CatsLogger[IO] = Slf4jLogger.getLogger[IO]

          val mqttBuilder =
            Mqtt5Client.builder
              .identifier("cats-based service (mqtt only)")
              .serverHost("localhost")
              .serverPort(1883)
          HiveMqExample.responderResource(hw, c, mqttBuilder)
        }
        val http = Http4sExample.http4sResource(hw, c)
        mqtt.product(http)
      }
      .surround(IO.readLine)
      .void
      .guarantee(IO.println("Goodbye!"))
  }

}
