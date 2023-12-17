package io.gitub.mahh.mqtt

import hello.mqtt.MqttRequest
import io.gitub.mahh.mqtt.RequestHandlerBuilderError.InvalidTopic
import smithy.api.Readonly
import smithy4s.Blob
import smithy4s.Endpoint
import smithy4s.Service
import smithy4s.capability.MonadThrowLike
import smithy4s.json.Json
import smithy4s.kinds.*
import smithy4s.server.UnaryServerEndpoint

enum RequestHandlerBuilderError(msg: String) extends Throwable(msg) {

  case DuplicateTopic(topic: String)
      extends RequestHandlerBuilderError(s"DuplicateTopic: $topic")

  case MissingMqttRequest(endpoint: Endpoint[_, _, _, _, _, _])
      extends RequestHandlerBuilderError(
        s"Operation not annotated as MqttRequest: $endpoint"
      )

  case InvalidTopic(topicString: String, detail: Option[Throwable])
      extends RequestHandlerBuilderError(s"Not a valid topic: $topicString")
}

/** Builds `RequestHandlers` for `smithy4s.Service`s.
  *
  * @tparam Topic
  *   Type of a parsed topic (we do not want to depend on a specific
  *   mqtt-library here, but we also do not want to re-implement validation of
  *   topics, so we keep that type abstract here).
  */
trait RequestServiceBuilder[Topic] {

  import RequestServiceBuilder.Result

  protected def parseTopic(topicString: String): Either[InvalidTopic, Topic]

  private def extractAndValidateTopics[Alg[_[_, _, _, _, _]]](
      service: Service[Alg]
  ): Result[Map[Topic, service.Endpoint[_, _, _, _, _]]] = {
    type EP = service.Endpoint[_, _, _, _, _]
    service.endpoints.foldLeft[Result[Map[Topic, EP]]](
      Right(Map.empty)
    ) { (acc, ep) =>
      acc.flatMap { map =>
        ep.hints
          .get[MqttRequest]
          .map(_.topic)
          .fold[Result[Map[Topic, EP]]](
            Left(RequestHandlerBuilderError.MissingMqttRequest(ep))
          ) { topicString =>
            parseTopic(topicString).flatMap { topic =>
              if (map.contains(topic)) {
                Left(RequestHandlerBuilderError.DuplicateTopic(topic.toString))
              } else {
                Right(map + (topic -> ep))
              }
            }
          }
      }
    }
  }

  def handlers[F[_], Alg[_[_, _, _, _, _]]](
      impl: FunctorAlgebra[Alg, F]
  )(implicit
      service: smithy4s.Service[Alg],
      F: MonadThrowLike[F]
  ): Result[RequestHandlers[F, Topic]] = {
    extractAndValidateTopics(service).map { topics =>
      val interpreter: FunctorInterpreter[service.Operation, F] =
        service.toPolyFunction[smithy4s.kinds.Kind1[F]#toKind5](impl)

      val makeCodecs = JsonRpcStyleResultEncoding.makeServerCodecs[F](
        Json.payloadCodecs.encoders,
        Json.payloadCodecs.decoders
      )

      def makeRequestHandler[I, E, O, SI, SO](
          endpoint: Endpoint[service.Operation, I, E, O, SI, SO]
      ): Blob => F[Blob] =
        UnaryServerEndpoint(
          interpreter,
          endpoint,
          codecs = makeCodecs(endpoint.schema),
          middleware = identity
        )
      val handlers: Map[Topic, RequestHandlerConfig[F]] =
        topics.map { case (t, ep) =>
          val isReadOnly = ep.hints.has[Readonly]
          t -> RequestHandlerConfig[F](makeRequestHandler(ep), isReadOnly)
        }
      RequestHandlers[F, Topic](handlers)
    }
  }
}

object RequestServiceBuilder {
  type Result[A] = Either[RequestHandlerBuilderError, A]
}
