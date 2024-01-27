package io.github.martinhh.mqtt

import io.github.martinhh.mqtt.MqttRequest
import io.github.martinhh.mqtt.RequestHandlerBuilderError.InvalidTopic
import io.github.martinhh.mqtt.RequestHandlerBuilderError.InvalidTopic
import io.github.martinhh.mqtt.RequestHandlerBuilderError.MissingMqttRequest
import RequestServiceBuilder.Result
import smithy4s.Blob
import smithy4s.Endpoint
import smithy4s.Hints
import smithy4s.Service
import smithy4s.capability.MonadThrowLike
import smithy4s.client.UnaryClientCompiler
import smithy4s.client.UnaryLowLevelClient
import smithy4s.json.Json

trait RequestClientBuilder[Topic] {

  trait Make[F[_]] {
    def apply[Alg[_[_, _, _, _, _]]](
      service: smithy4s.Service[Alg]
    ): RequestServiceBuilder.Result[service.Impl[F]]
  }

  protected def parseTopic(topicString: String): Either[InvalidTopic, Topic]

  private object InvalidTopic {
    def unapply(
        ep: smithy4s.Endpoint[_, _, _, _, _, _]
    ): Option[RequestHandlerBuilderError.InvalidTopic] =
      ep.schema.hints
        .get[MqttRequest]
        .flatMap(mr => parseTopic(mr.topic).left.toOption)
  }

  def makeCompiler[Alg[_[_, _, _, _, _]], F[_], Client](
      service: smithy4s.Service[Alg],
      client: Client,
      toSmithy4sClient: Client => UnaryLowLevelClient[F, Blob, Blob],
      setTopic: (Client, Option[String]) => Client
  )(implicit
      F: MonadThrowLike[F]
  ): RequestServiceBuilder.Result[service.FunctorEndpointCompiler[F]] = {
    val error = service.endpoints.collectFirst {
      case ep if !ep.schema.hints.has[MqttRequest] => MissingMqttRequest(ep)
      case InvalidTopic(error)                     => error
    }
    error.fold {
      val middleware = new Endpoint.Middleware.Simple[Client] {
        override def prepareWithHints(
            serviceHints: Hints,
            endpointHints: Hints
        ): Client => Client =
          setTopic(_, endpointHints.get[MqttRequest].map(_.topic))
      }
      val isError: Blob => Boolean =
        JsonRpcStyleResultEncoding.Error.isError(Json.payloadCodecs.decoders)

      val makeCodecs =
        JsonRpcStyleResultEncoding.makeClientCodecs(
          Json.payloadCodecs.encoders,
          Json.payloadCodecs.decoders
        )

      Right(
        UnaryClientCompiler[Alg, F, Client, Blob, Blob](
          service,
          client,
          toSmithy4sClient,
          makeCodecs,
          middleware,
          payload => !isError(payload)
        )
      )
    } { e => Left(e) }

  }

  def make[F[_], Client](
      client: Client,
      toSmithy4sClient: Client => UnaryLowLevelClient[F, Blob, Blob],
      setTopic: (Client, Option[String]) => Client
  )(implicit
      F: MonadThrowLike[F]
  ): Make[F] =
    new Make[F] {
      override def apply[Alg[_[_, _, _, _, _]]](
        service: Service[Alg]
      ): Result[service.Impl[F]] = {
        makeCompiler(service, client, toSmithy4sClient, setTopic).map {
          compiler =>
            service.impl(compiler)
        }
      }
    }
}
