package io.github.martinhh.mqtt.logging

/** Logging trait (targeting log4cats as implementation). */
trait Logger[F[_]] {
  def error(message: => String): F[Unit]
  def error(t: Throwable)(message: => String): F[Unit]
  def warn(message: => String): F[Unit]
  def info(message: => String): F[Unit]
  def debug(message: => String): F[Unit]
  def trace(message: => String): F[Unit]
}
