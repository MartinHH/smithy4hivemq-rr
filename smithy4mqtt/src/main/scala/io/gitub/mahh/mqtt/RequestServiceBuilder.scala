package io.gitub.mahh.mqtt

import hello.mqtt.MqttRequest
import io.gitub.mahh.mqtt.RequestHandlerBuilderError.InvalidTopic
import smithy4s.Blob
import smithy4s.Endpoint
import smithy4s.Service
import smithy4s.capability.MonadThrowLike
import smithy4s.codecs.PayloadDecoder
import smithy4s.codecs.PayloadEncoder
import smithy4s.codecs.PayloadError
import smithy4s.json.Json
import smithy4s.kinds.*
import smithy4s.schema.OperationSchema
import smithy4s.server.UnaryServerCodecs
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

  private def makeServerCodecs[F[_], I, E, O](
      schema: OperationSchema[I, E, O, _, _]
  )(implicit
      F: MonadThrowLike[F]
  ): UnaryServerCodecs[F, Blob, Blob, I, E, O] = {
    val iDec: PayloadDecoder[I] =
      Json.payloadCodecs.decoders.fromSchema(schema.input)
    val eEnc: Option[PayloadEncoder[E]] =
      schema.error.map { e =>
        JsonRpcStyleResultEncoding.Error.encoder(e, Json.payloadCodecs.encoders)
      }
    val oEnc: PayloadEncoder[O] =
      JsonRpcStyleResultEncoding.Success.encoder(
        schema.output,
        Json.payloadCodecs.encoders
      )
    val throwableEnc: PayloadEncoder[Throwable] =
      JsonRpcStyleResultEncoding.Error.throwableEncoder(
        Json.payloadCodecs.encoders
      )
    val payloadErrorEnc: PayloadEncoder[PayloadError] =
      JsonRpcStyleResultEncoding.Error.encoder(
        PayloadError.schema,
        identity,
        _ => 400,
        Json.payloadCodecs.encoders
      )

    UnaryServerCodecs[F, Blob, Blob, I, E, O](
      in => F.liftEither(iDec.decode(in)),
      eEnc.fold[E => F[Blob]] { e =>
        F.raiseError(new NoSuchElementException(s"no error encoder for $e"))
      } { (enc: PayloadEncoder[E]) => (e: E) => F.pure(enc.encode(e)) },
      {
        case pe: PayloadError => F.pure(payloadErrorEnc.encode(pe))
        case t                => F.pure(throwableEnc.encode(t))
      },
      o => F.pure(oEnc.encode(o))
    )
  }

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

      def makeRequestHandler[I, E, O, SI, SO](
          endpoint: Endpoint[service.Operation, I, E, O, SI, SO]
      ): Blob => F[Blob] =
        UnaryServerEndpoint(
          interpreter,
          endpoint,
          codecs = makeServerCodecs(endpoint.schema),
          middleware = identity
        )
      val handlers: Map[Topic, RequestHandlerConfig[F]] =
        topics.map { case (t, ep) =>
          t -> RequestHandlerConfig[F](makeRequestHandler(ep))
        }
      RequestHandlers[F, Topic](handlers)
    }
  }
}

object RequestServiceBuilder {
  type Result[A] = Either[RequestHandlerBuilderError, A]
}
