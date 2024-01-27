package io.github.martinhh.mqtt.logging.log4cats

import io.github.martinhh.mqtt.logging.Logger

given loggingFromLog4Cats[F[_]](using
    l: org.typelevel.log4cats.Logger[F]
): Logger[F] =
  new Logger[F] {
    override def error(message: => String): F[Unit] =
      l.error(message)

    override def error(t: Throwable)(message: => String): F[Unit] =
      l.error(t)(message)

    override def warn(message: => String): F[Unit] =
      l.warn(message)

    override def info(message: => String): F[Unit] =
      l.info(message)

    override def debug(message: => String): F[Unit] =
      l.debug(message)

    override def trace(message: => String): F[Unit] =
      l.trace(message)
  }
