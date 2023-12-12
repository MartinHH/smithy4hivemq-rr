package io.gitub.mahh.mqtt

import smithy4s.codecs.PayloadEncoder
import smithy4s.http.HttpStatusCode
import smithy4s.schema.CachedSchemaCompiler
import smithy4s.schema.ErrorSchema
import smithy4s.schema.Schema

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
  }

  private case class ErrorDetails[D](
      code: Int,
      message: String,
      data: Option[D]
  )

  case object Error {

    private def schema[D](schemaForD: Schema[D]): Schema[ErrorDetails[D]] =
      Schema.struct[ErrorDetails[D]](
        Schema
          .struct[ErrorDetails[D]](
            Schema.int.required[ErrorDetails[D]]("code", _.code),
            Schema.string.required[ErrorDetails[D]]("message", _.message),
            schemaForD.optional[ErrorDetails[D]]("data", _.data)
          )(ErrorDetails.apply)
          .required[ErrorDetails[D]]("error", identity)
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
  }

}
