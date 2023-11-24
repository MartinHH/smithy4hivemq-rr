package com.example.services

import hello.Greeting
import hello.HelloWorldService

import cats.Applicative

class HelloWorldImpl[F[_]: Applicative] extends HelloWorldService[F] {
  def hello(name: String, town: Option[String]): F[Greeting] = Applicative[F].pure {
    town match {
      case None    => Greeting(s"Hello " + name + "!")
      case Some(t) => Greeting(s"Hello " + name + " from " + t + "!")
    }
  }
}
