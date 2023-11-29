package com.example.services

import hello.Greeting
import hello.HelloWorldService
import smithy4s.capability.Zipper

class HelloWorldImpl[F[_]](using F: Zipper[F]) extends HelloWorldService[F] {
  def hello(name: String, town: Option[String]): F[Greeting] = F.pure {
    town match {
      case None    => Greeting(s"Hello " + name + "!")
      case Some(t) => Greeting(s"Hello " + name + " from " + t + "!")
    }
  }
}
