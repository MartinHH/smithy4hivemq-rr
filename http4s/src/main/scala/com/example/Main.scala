package com.example

import hello._
import cats.syntax.all._
import cats.effect._
import cats.effect.syntax.all._
import org.http4s.implicits._
import org.http4s.ember.server._
import org.http4s._
import com.comcast.ip4s._
import smithy4s.http4s.SimpleRestJsonBuilder
import scala.concurrent.duration._

object HelloWorldImpl extends HelloWorldService[IO] {
  def hello(name: String, town: Option[String]): IO[Greeting] = IO.pure {
    town match {
      case None    => Greeting(s"Hello " + name + "!")
      case Some(t) => Greeting(s"Hello " + name + " from " + t + "!")
    }
  }
}

class CountImpl(count: Ref[IO, Int]) extends CountService[IO] {
  def modify(operator: MathOp, operand: Int): IO[Unit] = operator match {
    case MathOp.DIV if operand == 0 => IO.raiseError(DivisionByZero(""))
    case other => count.update(c => CountImpl.enumToOp(other)(c, operand))
  }
  def getCount(): IO[CurrentCount] = count.get.map(CurrentCount.apply)
}

object CountImpl {
  private def enumToOp(mathOp: MathOp): (Int, Int) => Int = {
    mathOp match
      case MathOp.ADD => _ + _
      case MathOp.SUB => _ - _
      case MathOp.MUL => _ * _
      case MathOp.DIV => _ / _
  }
}

object Routes {
  private val helloWorld: Resource[IO, HttpRoutes[IO]] =
    SimpleRestJsonBuilder.routes(HelloWorldImpl).resource

  private val count: Resource[IO, HttpRoutes[IO]] =
    Resource.eval(Ref.of[IO, Int](0)).flatMap { c =>
      SimpleRestJsonBuilder.routes(CountImpl(c)).resource
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
