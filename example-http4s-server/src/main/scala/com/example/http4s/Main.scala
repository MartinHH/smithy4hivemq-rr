package com.example.http4s

import cats.effect.*
import cats.syntax.all.*
import com.comcast.ip4s.*
import com.example.services.CountImpl
import com.example.services.HelloWorldImpl
import hello.CountService
import hello.HelloWorldService
import org.http4s.*
import org.http4s.ember.server.*
import org.http4s.implicits.*
import org.http4s.server.Server
import smithy4s.http4s.SimpleRestJsonBuilder

import scala.concurrent.duration.*

object Routes {

  private val docs: HttpRoutes[IO] =
    smithy4s.http4s.swagger.docs[IO](HelloWorldService, CountService)

  def buildAll(
      helloWorldService: HelloWorldService[IO],
      countService: CountService[IO]
  ): Resource[IO, HttpRoutes[IO]] =
    for {
      hw <- SimpleRestJsonBuilder.routes(helloWorldService).resource
      c <- SimpleRestJsonBuilder.routes(countService).resource
    } yield hw <+> c <+> docs

  val all =
    for {
      counter <- Ref.of[IO, Int](0).toResource
      routes <- buildAll(HelloWorldImpl(), CountImpl(counter))
    } yield routes
}

object Main extends IOApp.Simple {

  val defaultPort: Port = port"9000"
  val defaultHost: Hostname = host"localhost"

  def defaultServerBuilder(
      port: Port,
      host: Hostname
  ): EmberServerBuilder[IO] =
    EmberServerBuilder
      .default[IO]
      .withPort(port)
      .withHost(host)
      .withShutdownTimeout(1.second)

  def http4sResource(
      helloWorldService: HelloWorldService[IO],
      countService: CountService[IO],
      port: Port = defaultPort,
      host: Hostname = defaultHost
  ): Resource[IO, Server] = {
    val message =
      s"Server started on: $host:$port, press enter to stop"

    val server: Resource[IO, Server] =
      for {
        routes <- Routes.buildAll(helloWorldService, countService)
        builder = defaultServerBuilder(port, host).withHttpApp(
          routes.orNotFound
        )
        server <- builder.build
      } yield server
    server.productL(IO.println(message).toResource)
  }

  val run: IO[Unit] =
    Ref
      .of[IO, Int](0)
      .toResource
      .flatMap { counter =>
        http4sResource(HelloWorldImpl(), CountImpl(counter))
      }
      .surround(IO.readLine)
      .void
      .guarantee(IO.println("Goodbye!"))

}
