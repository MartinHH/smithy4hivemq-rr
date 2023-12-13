package com.example.services

import cats.effect.IO
import hello.Greeting
import hello.HelloWorldService

class HelloWorldImpl extends HelloWorldService[IO] {
  def hello(name: String, town: Option[String]): IO[Greeting] = IO {
    town match {
      case None    => Greeting(s"Hello " + name + "!")
      case Some(t) => Greeting(s"Hello " + name + " from " + t + "!")
    }
  }
}
