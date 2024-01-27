package io.github.martinhh.mqtt

import smithy4s.Blob
import smithy4s.capability.MonadThrowLike
import smithy4s.capability.instances.either.*
import smithy4s.client.UnaryClientCodecs
import smithy4s.codecs.PayloadDecoder
import smithy4s.codecs.PayloadEncoder
import smithy4s.codecs.PayloadError
import smithy4s.http.HttpStatusCode
import smithy4s.schema.CachedSchemaCompiler
import smithy4s.schema.ErrorSchema
import smithy4s.schema.OperationSchema
import smithy4s.schema.Schema
import smithy4s.server.UnaryServerCodecs

/** Encoders that encode success- and error response similar to JSON-RPC.
  *
  * Since we use this for MQTT, neither (jsonrpc) protocol version nor
  * (correlation-)id are encoded here. The similarities to JSON-RPC are mainly
  * the encoding of errors and using "result" and "error" as discriminator.
  *
  * @see
  *   [[https://www.jsonrpc.org/specification#response_object]]
  */
object JsonRpcStyleResultEncoding {

  object Success {
    private def schema[A](schemaForA: Schema[A]): Schema[A] =
      Schema.struct[A](
        schemaForA.required[A]("result", identity)
      )(identity)

    def encoder[A](
        schemaForA: Schema[A],
        compiler: CachedSchemaCompiler[PayloadEncoder]
    ): PayloadEncoder[A] = {
      compiler.fromSchema(Success.schema(schemaForA))
    }

    def decoder[A](
      schemaForA: Schema[A],
      compiler: CachedSchemaCompiler[PayloadDecoder]
    ): PayloadDecoder[A] = {
      compiler.fromSchema(Success.schema(schemaForA))
    }
  }

  private case class ErrorDetails[D](
      code: Int,
      message: String,
      data: Option[D]
  )

  case object Error {

    case class UnexpectedError(code: Int, message: String)
      extends Throwable
        with scala.util.control.NoStackTrace

    private val ErrorKey = "error"

    private def schema[D](schemaForD: Schema[D]): Schema[ErrorDetails[D]] =
      Schema.struct[ErrorDetails[D]](
        Schema
          .struct[ErrorDetails[D]](
            Schema.int.required[ErrorDetails[D]]("code", _.code),
            Schema.string.required[ErrorDetails[D]]("message", _.message),
            schemaForD.optional[ErrorDetails[D]]("data", _.data)
          )(ErrorDetails.apply)
          .required[ErrorDetails[D]](ErrorKey, identity)
      )(identity)

    def encoder[E](
        schemaForE: Schema[E],
        unliftError: E => Throwable,
        code: E => Int,
        compiler: CachedSchemaCompiler[PayloadEncoder]
    ): PayloadEncoder[E] = {
      compiler
        .fromSchema(schema(schemaForE))
        .contramap[E] { e =>
          ErrorDetails(
            code(e),
            unliftError(e).getMessage,
            Some(e)
          )
        }
    }
    def encoder[E](
        schemaForE: Schema[E],
        unliftError: E => Throwable,
        compiler: CachedSchemaCompiler[PayloadEncoder]
    ): PayloadEncoder[E] = {
      encoder(
        schemaForE,
        unliftError,
        e => HttpStatusCode.fromSchema(schemaForE).code(e, 500),
        compiler
      )
    }

    def encoder[E](
        errorSchema: ErrorSchema[E],
        compiler: CachedSchemaCompiler[PayloadEncoder]
    ): PayloadEncoder[E] = {
      encoder(errorSchema.schema, errorSchema.unliftError, compiler)
    }

    def throwableEncoder(
        compiler: CachedSchemaCompiler[PayloadEncoder]
    ): PayloadEncoder[Throwable] = {
      compiler
        .fromSchema(schema(Schema.unit))
        .contramap[Throwable] { e =>
          ErrorDetails(
            500,
            e.getMessage,
            None
          )
        }
    }

    def errorAsThrowableDecoder[E](
      schemaForE: Schema[E],
      unliftError: E => Throwable,
      compiler: CachedSchemaCompiler[PayloadDecoder]
    ): PayloadDecoder[Throwable] = {
      val errorDetailsDecoder =
        compiler.fromSchema[ErrorDetails[E]](schema(schemaForE))
      errorDetailsDecoder.map { ed =>
        ed.data.fold(UnexpectedError(ed.code, ed.message))(unliftError)
      }
    }

    def fallbackThrowableDecoder(
      compiler: CachedSchemaCompiler[PayloadDecoder]
    ): PayloadDecoder[Throwable] = {
      compiler.fromSchema[ErrorDetails[Unit]](schema(Schema.unit)).map { ed =>
        UnexpectedError(ed.code, ed.message)
      }
    }

    // simple schema for checking whether we are dealing with a
    // json-object that contains an "error"-object
    private val errorCheckSchema =
      Schema.struct[Tuple1[Unit]](
        Schema.unit.required[Tuple1[Unit]](ErrorKey, _._1)
      )(Tuple1.apply)

    // TODO (if this should ever come to real use): this is quite ugly because we decode
    //  once just to discriminate between error and success (and then decode again thereafter).
    def isError(
      compiler: CachedSchemaCompiler[PayloadDecoder]
    ): Blob => Boolean = {
      val decoder = compiler.fromSchema(errorCheckSchema)
      blob => decoder.decode(Blob(blob.asByteBuffer)).isRight
    }
  }

  def makeClientCodecs[F[_]](
    encoders: CachedSchemaCompiler[PayloadEncoder],
    decoders: CachedSchemaCompiler[PayloadDecoder]
  )(using
    F: MonadThrowLike[F]
  ): UnaryClientCodecs.Make[F, Blob, Blob] =
    new UnaryClientCodecs.Make[F, Blob, Blob] {
      override def apply[I, E, O, SI, SO](
        schema: OperationSchema[I, E, O, SI, SO]
      ): UnaryClientCodecs[F, Blob, Blob, I, E, O] = {
        val iEnc: PayloadEncoder[I] =
          encoders.fromSchema(schema.input)
        val oDec = Success.decoder(
          schema.output,
          decoders
        )
        val eDec = schema.error.fold {
          // TODO: try to properly decode all known throwables (e.g.PayloadError)
          Error.fallbackThrowableDecoder(
            decoders
          )
        } { errorSchema =>
          Error.errorAsThrowableDecoder(
            errorSchema.schema,
            errorSchema.unliftError,
            decoders
          )
        }
        UnaryClientCodecs[F, Blob, Blob, I, E, O](
          (i: I) => F.pure(iEnc.encode(i)),
          (r: Blob) => F.liftEither(eDec.decode(r)),
          (r: Blob) => F.liftEither(oDec.decode(r))
        )
      }
    }

  def makeServerCodecs[F[_]](
    encoders: CachedSchemaCompiler[PayloadEncoder],
    decoders: CachedSchemaCompiler[PayloadDecoder]
  )(using
    F: MonadThrowLike[F]
  ): UnaryServerCodecs.Make[F, Blob, Blob] =
    new UnaryServerCodecs.Make[F, Blob, Blob] {
      override def apply[I, E, O, SI, SO](
        schema: OperationSchema[I, E, O, SI, SO]
      ): UnaryServerCodecs[F, Blob, Blob, I, E, O] = {
        val iDec: PayloadDecoder[I] =
          decoders.fromSchema(schema.input)
        val eEnc: Option[PayloadEncoder[E]] =
          schema.error.map { e =>
            Error.encoder(e, encoders)
          }
        val oEnc: PayloadEncoder[O] =
          Success.encoder(
            schema.output,
            encoders
          )
        val throwableEnc: PayloadEncoder[Throwable] =
          Error.throwableEncoder(
            encoders
          )
        val payloadErrorEnc: PayloadEncoder[PayloadError] =
          Error.encoder(
            PayloadError.schema,
            identity,
            _ => 400,
            encoders
          )

        UnaryServerCodecs[F, Blob, Blob, I, E, O](
          in => F.liftEither(iDec.decode(in)),
          eEnc.fold[E => F[Blob]] { e =>
            F.raiseError(new NoSuchElementException(s"no error encoder for $e"))
          } { (enc: PayloadEncoder[E]) => (e: E) => F.pure(enc.encode(e)) },
          {
            case pe: PayloadError => F.pure(payloadErrorEnc.encode(pe))
            case t => F.pure(throwableEnc.encode(t))
          },
          o => F.pure(oEnc.encode(o))
        )
      }
    }

}
