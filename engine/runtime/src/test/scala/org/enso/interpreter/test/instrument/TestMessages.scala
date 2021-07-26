package org.enso.interpreter.test.instrument

import java.util.UUID

import org.enso.interpreter.runtime.`type`.Constants
import org.enso.polyglot.runtime.Runtime.Api

/** Helper methods for creating test messages. */
object TestMessages {

  /** Create an update response.
    *
    * @param contextId an identifier of the context
    * @param expressionId an identifier of the expression
    * @return the expression update response
    */
  def update(
    contextId: UUID,
    expressionId: UUID
  ): Api.Response =
    Api.Response(
      Api.ExpressionUpdates(
        contextId,
        Set(
          Api.ExpressionUpdate(
            expressionId,
            None,
            None,
            Vector(Api.ProfilingInfo.ExecutionTime(0)),
            false,
            Api.ExpressionUpdate.Payload.Value()
          )
        )
      )
    )

  /** Create an update response.
    *
    * @param contextId an identifier of the context
    * @param expressionId an identifier of the expression
    * @param expressionType a type of the expression
    * @return the expression update response
    */
  def update(
    contextId: UUID,
    expressionId: UUID,
    expressionType: String
  ): Api.Response =
    Api.Response(
      Api.ExpressionUpdates(
        contextId,
        Set(
          Api.ExpressionUpdate(
            expressionId,
            Some(expressionType),
            None,
            Vector(Api.ProfilingInfo.ExecutionTime(0)),
            false,
            Api.ExpressionUpdate.Payload.Value()
          )
        )
      )
    )

  /** Create an update response.
    *
    * @param contextId an identifier of the context
    * @param expressionId an identifier of the expression
    * @param expressionType a type of the expression
    * @param methodPointer a pointer to the method definition
    * @return the expression update response
    */
  def update(
    contextId: UUID,
    expressionId: UUID,
    expressionType: String,
    methodPointer: Api.MethodPointer
  ): Api.Response =
    update(contextId, expressionId, expressionType, methodPointer, false)

  /** Create an update response.
    *
    * @param contextId an identifier of the context
    * @param expressionId an identifier of the expression
    * @param expressionType a type of the expression
    * @param methodPointer a pointer to the method definition
    * @param fromCache whether or not the value for this expression came
    * from the cache
    * @return the expression update response
    */
  def update(
    contextId: UUID,
    expressionId: UUID,
    expressionType: String,
    methodPointer: Api.MethodPointer,
    fromCache: Boolean
  ): Api.Response =
    Api.Response(
      Api.ExpressionUpdates(
        contextId,
        Set(
          Api.ExpressionUpdate(
            expressionId,
            Some(expressionType),
            Some(methodPointer),
            Vector(Api.ProfilingInfo.ExecutionTime(0)),
            fromCache,
            Api.ExpressionUpdate.Payload.Value()
          )
        )
      )
    )

  /** Create an error update response.
    *
    * @param contextId an identifier of the context
    * @param expressionId an identifier of the expression
    * @param payload the error payload
    * @return the expression update response
    */
  def error(
    contextId: UUID,
    expressionId: UUID,
    payload: Api.ExpressionUpdate.Payload
  ): Api.Response =
    errorBuilder(
      contextId,
      expressionId,
      None,
      false,
      payload
    )

  /** Create an error update response.
    *
    * @param contextId an identifier of the context
    * @param expressionId an identifier of the expression
    * @param methodPointer a pointer to the method definition
    * @param payload the error payload
    * @return the expression update response
    */
  def error(
    contextId: UUID,
    expressionId: UUID,
    methodPointer: Api.MethodPointer,
    payload: Api.ExpressionUpdate.Payload
  ): Api.Response =
    error(
      contextId,
      expressionId,
      methodPointer,
      false,
      payload
    )

  /** Create an error update response.
    *
    * @param contextId an identifier of the context
    * @param expressionId an identifier of the expression
    * @param methodPointer a pointer to the method definition
    * @param fromCache whether or not the value for this expression came
    * from the cache
    * @param payload the error payload
    * @return the expression update response
    */
  def error(
    contextId: UUID,
    expressionId: UUID,
    methodPointer: Api.MethodPointer,
    fromCache: Boolean,
    payload: Api.ExpressionUpdate.Payload
  ): Api.Response =
    errorBuilder(
      contextId,
      expressionId,
      Some(methodPointer),
      fromCache,
      payload
    )

  /** Create an error update response.
    *
    * @param contextId an identifier of the context
    * @param expressionId an identifier of the expression
    * @param methodPointerOpt a pointer to the method definition
    * @param fromCache whether or not the value for this expression came
    * from the cache
    * @param payload the error payload
    * @return the expression update response
    */
  private def errorBuilder(
    contextId: UUID,
    expressionId: UUID,
    methodPointerOpt: Option[Api.MethodPointer],
    fromCache: Boolean,
    payload: Api.ExpressionUpdate.Payload
  ): Api.Response =
    Api.Response(
      Api.ExpressionUpdates(
        contextId,
        Set(
          Api.ExpressionUpdate(
            expressionId,
            Some(Constants.ERROR),
            methodPointerOpt,
            Vector(Api.ProfilingInfo.ExecutionTime(0)),
            fromCache,
            payload
          )
        )
      )
    )

  /** Create a panic update response.
    *
    * @param contextId an identifier of the context
    * @param expressionId an identifier of the expression
    * @param payload the error payload
    * @return the expression update response
    */
  def panic(
    contextId: UUID,
    expressionId: UUID,
    payload: Api.ExpressionUpdate.Payload
  ): Api.Response =
    panicBuilder(contextId, expressionId, None, payload)

  /** Create a panic update response.
    *
    * @param contextId an identifier of the context
    * @param expressionId an identifier of the expression
    * @param methodPointer a pointer to the method definition
    * @param payload the error payload
    * @return the expression update response
    */
  def panic(
    contextId: UUID,
    expressionId: UUID,
    methodPointer: Api.MethodPointer,
    payload: Api.ExpressionUpdate.Payload
  ): Api.Response =
    panicBuilder(contextId, expressionId, Some(methodPointer), payload)

  /** Create a panic update response.
    *
    * @param contextId an identifier of the context
    * @param expressionId an identifier of the expression
    * @param methodPointer a pointer to the method definition
    * @param payload the error payload
    * @return the expression update response
    */
  private def panicBuilder(
    contextId: UUID,
    expressionId: UUID,
    methodPointer: Option[Api.MethodPointer],
    payload: Api.ExpressionUpdate.Payload
  ): Api.Response =
    Api.Response(
      Api.ExpressionUpdates(
        contextId,
        Set(
          Api.ExpressionUpdate(
            expressionId,
            Some(Constants.PANIC),
            methodPointer,
            Vector(Api.ProfilingInfo.ExecutionTime(0)),
            false,
            payload
          )
        )
      )
    )

}
