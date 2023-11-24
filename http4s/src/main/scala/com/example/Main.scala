package com.example

import cats.effect.*
import cats.syntax.all.*
import com.comcast.ip4s.*
import com.example.services.CountImpl
import com.example.services.HelloWorldImpl
import com.example.services.ThreadSafeVar
import hello.CountService
import hello.HelloWorldService
import org.http4s.*
import org.http4s.ember.server.*
import org.http4s.implicits.*
import smithy4s.http4s.SimpleRestJsonBuilder

import scala.concurrent.duration.*

/** Adapts [cats.effect.Ref] to our [ThreadSafeVar] */
class RefAsThreadSafeVar[F[_], A](ref: Ref[F, A]) extends ThreadSafeVar[F, A] {
  override def get: F[A] = ref.get
  override def update(f: A => A): F[Unit] = ref.update(f)
}

object Routes {
  private val helloWorld: Resource[IO, HttpRoutes[IO]] =
    SimpleRestJsonBuilder.routes(HelloWorldImpl[IO]()).resource

  private val count: Resource[IO, HttpRoutes[IO]] =
    Resource.eval(Ref.of[IO, Int](0)).flatMap { c =>
      SimpleRestJsonBuilder.routes(CountImpl(RefAsThreadSafeVar(c))).resource
    }

  private val docs: HttpRoutes[IO] =
    smithy4s.http4s.swagger.docs[IO](HelloWorldService, CountService)

  val all: Resource[IO, HttpRoutes[IO]] =
    for {
      hw <- helloWorld
      c <- count
    } yield hw <+> c <+> docs
}

object Main extends IOApp.Simple {
  val run = Routes.all
    .flatMap { routes =>
      val thePort = port"9000"
      val theHost = host"localhost"
      val message =
        s"Server started on: $theHost:$thePort, press enter to stop"

      EmberServerBuilder
        .default[IO]
        .withPort(thePort)
        .withHost(theHost)
        .withHttpApp(routes.orNotFound)
        .withShutdownTimeout(1.second)
        .build
        .productL(IO.println(message).toResource)
    }
    .surround(IO.readLine)
    .void
    .guarantee(IO.println("Goodbye!"))

}
