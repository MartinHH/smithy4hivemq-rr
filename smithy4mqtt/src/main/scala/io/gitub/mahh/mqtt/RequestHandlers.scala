package io.gitub.mahh.mqtt

import io.gitub.mahh.mqtt.RequestHandlerBuilderError.DuplicateTopic

import smithy4s.Blob

/** Contains handler and related metadata for a single operation. */
case class RequestHandlerConfig[F[_]](
    handler: Blob => F[Blob],
    isReadOnly: Boolean
)

/** Contains handlers and related metadata for multiple request-topics. */
sealed trait RequestHandlers[F[_], Topic] {
  def handlers: Map[Topic, RequestHandlerConfig[F]]

  def combine(
      that: RequestHandlers[F, Topic]
  ): RequestHandlers.CombineResult[F, Topic]
}

object RequestHandlers {

  type CombineResult[F[_], T] =
    Either[RequestHandlerBuilderError.DuplicateTopic, RequestHandlers[F, T]]

  private case class RequestHandlersImpl[F[_], Topic](
      handlers: Map[Topic, RequestHandlerConfig[F]]
  ) extends RequestHandlers[F, Topic] {
    override def combine(
        that: RequestHandlers[F, Topic]
    ): CombineResult[F, Topic] = {
      val duplicates = handlers.keySet.intersect(that.handlers.keySet)
      duplicates.headOption
        .fold[Either[
          RequestHandlerBuilderError.DuplicateTopic,
          RequestHandlers[F, Topic]
        ]] {
          Right(copy(handlers ++ that.handlers))
        } { topic =>
          Left(RequestHandlerBuilderError.DuplicateTopic(topic.toString))
        }
    }
  }

  def apply[F[_], Topic](
      handlers: Map[Topic, RequestHandlerConfig[F]]
  ): RequestHandlers[F, Topic] =
    RequestHandlersImpl(handlers)
}
