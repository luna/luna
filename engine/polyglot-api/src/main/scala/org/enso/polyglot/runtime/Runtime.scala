package org.enso.polyglot.runtime

import com.fasterxml.jackson.annotation.{JsonSubTypes, JsonTypeInfo}
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.dataformat.cbor.CBORFactory
import com.fasterxml.jackson.module.scala.{
  DefaultScalaModule,
  ScalaObjectMapper
}
import org.enso.logger.masking.{MaskedPath, MaskedString, ToLogString}
import org.enso.polyglot.{ModuleExports, Suggestion}
import org.enso.polyglot.data.{Tree, TypeGraph}
import org.enso.text.ContentVersion
import org.enso.text.editing.model
import org.enso.text.editing.model.{Range, TextEdit}

import java.io.File
import java.nio.ByteBuffer
import java.util.UUID
import scala.util.Try

object Runtime {

  /** A common supertype for all Runtime API methods.
    */
  @JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
  @JsonSubTypes(
    Array(
      new JsonSubTypes.Type(
        value = classOf[Api.CreateContextRequest],
        name  = "createContextRequest"
      ),
      new JsonSubTypes.Type(
        value = classOf[Api.CreateContextResponse],
        name  = "createContextResponse"
      ),
      new JsonSubTypes.Type(
        value = classOf[Api.DestroyContextRequest],
        name  = "destroyContextRequest"
      ),
      new JsonSubTypes.Type(
        value = classOf[Api.DestroyContextResponse],
        name  = "destroyContextResponse"
      ),
      new JsonSubTypes.Type(
        value = classOf[Api.PushContextRequest],
        name  = "pushContextRequest"
      ),
      new JsonSubTypes.Type(
        value = classOf[Api.PushContextResponse],
        name  = "pushContextResponse"
      ),
      new JsonSubTypes.Type(
        value = classOf[Api.PopContextRequest],
        name  = "popContextRequest"
      ),
      new JsonSubTypes.Type(
        value = classOf[Api.PopContextResponse],
        name  = "popContextResponse"
      ),
      new JsonSubTypes.Type(
        value = classOf[Api.RecomputeContextRequest],
        name  = "recomputeContextRequest"
      ),
      new JsonSubTypes.Type(
        value = classOf[Api.RecomputeContextResponse],
        name  = "recomputeContextResponse"
      ),
      new JsonSubTypes.Type(
        value = classOf[Api.OpenFileNotification],
        name  = "openFileNotification"
      ),
      new JsonSubTypes.Type(
        value = classOf[Api.EditFileNotification],
        name  = "editFileNotification"
      ),
      new JsonSubTypes.Type(
        value = classOf[Api.CloseFileNotification],
        name  = "closeFileNotification"
      ),
      new JsonSubTypes.Type(
        value = classOf[Api.VisualisationUpdate],
        name  = "visualisationUpdate"
      ),
      new JsonSubTypes.Type(
        value = classOf[Api.AttachVisualisation],
        name  = "attachVisualisation"
      ),
      new JsonSubTypes.Type(
        value = classOf[Api.VisualisationAttached],
        name  = "visualisationAttached"
      ),
      new JsonSubTypes.Type(
        value = classOf[Api.DetachVisualisation],
        name  = "detachVisualisation"
      ),
      new JsonSubTypes.Type(
        value = classOf[Api.VisualisationDetached],
        name  = "visualisationDetached"
      ),
      new JsonSubTypes.Type(
        value = classOf[Api.ModifyVisualisation],
        name  = "modifyVisualisation"
      ),
      new JsonSubTypes.Type(
        value = classOf[Api.VisualisationModified],
        name  = "visualisationModified"
      ),
      new JsonSubTypes.Type(
        value = classOf[Api.ExpressionUpdates],
        name  = "expressionUpdates"
      ),
      new JsonSubTypes.Type(
        value = classOf[Api.RenameProject],
        name  = "renameProject"
      ),
      new JsonSubTypes.Type(
        value = classOf[Api.ProjectRenamed],
        name  = "projectRenamed"
      ),
      new JsonSubTypes.Type(
        value = classOf[Api.ContextNotExistError],
        name  = "contextNotExistError"
      ),
      new JsonSubTypes.Type(
        value = classOf[Api.EmptyStackError],
        name  = "emptyStackError"
      ),
      new JsonSubTypes.Type(
        value = classOf[Api.ModuleNotFound],
        name  = "moduleNotFound"
      ),
      new JsonSubTypes.Type(
        value = classOf[Api.ExecutionUpdate],
        name  = "executionUpdate"
      ),
      new JsonSubTypes.Type(
        value = classOf[Api.ExecutionFailed],
        name  = "executionFailed"
      ),
      new JsonSubTypes.Type(
        value = classOf[Api.ExecutionComplete],
        name  = "executionSuccessful"
      ),
      new JsonSubTypes.Type(
        value = classOf[Api.VisualisationExpressionFailed],
        name  = "visualisationExpressionFailed"
      ),
      new JsonSubTypes.Type(
        value = classOf[Api.VisualisationEvaluationFailed],
        name  = "visualisationEvaluationFailed"
      ),
      new JsonSubTypes.Type(
        value = classOf[Api.VisualisationNotFound],
        name  = "visualisationNotFound"
      ),
      new JsonSubTypes.Type(
        value = classOf[Api.InvalidStackItemError],
        name  = "invalidStackItemError"
      ),
      new JsonSubTypes.Type(
        value = classOf[Api.InitializedNotification],
        name  = "initializedNotification"
      ),
      new JsonSubTypes.Type(
        value = classOf[Api.ShutDownRuntimeServer],
        name  = "shutDownRuntimeServer"
      ),
      new JsonSubTypes.Type(
        value = classOf[Api.RuntimeServerShutDown],
        name  = "runtimeServerShutDown"
      ),
      new JsonSubTypes.Type(
        value = classOf[Api.SuggestionsDatabaseModuleUpdateNotification],
        name  = "suggestionsDatabaseModuleUpdateNotification"
      ),
      new JsonSubTypes.Type(
        value = classOf[Api.InvalidateModulesIndexRequest],
        name  = "invalidateModulesIndexRequest"
      ),
      new JsonSubTypes.Type(
        value = classOf[Api.InvalidateModulesIndexResponse],
        name  = "invalidateModulesIndexResponse"
      ),
      new JsonSubTypes.Type(
        value = classOf[Api.VerifyModulesIndexRequest],
        name  = "verifyModulesIndexRequest"
      ),
      new JsonSubTypes.Type(
        value = classOf[Api.VerifyModulesIndexResponse],
        name  = "verifyModulesIndexResponse"
      ),
      new JsonSubTypes.Type(
        value = classOf[Api.ImportSuggestionRequest],
        name  = "importSuggestionRequest"
      ),
      new JsonSubTypes.Type(
        value = classOf[Api.ImportSuggestionResponse],
        name  = "importSuggestionResponse"
      ),
      new JsonSubTypes.Type(
        value = classOf[Api.GetTypeGraphRequest],
        name  = "getTypeGraphRequest"
      ),
      new JsonSubTypes.Type(
        value = classOf[Api.GetTypeGraphResponse],
        name  = "getTypeGraphResponse"
      ),
      new JsonSubTypes.Type(
        value = classOf[Api.LibraryLoaded],
        name  = "libraryLoaded"
      ),
      new JsonSubTypes.Type(
        value = classOf[Api.ProgressNotification],
        name  = "progressNotification"
      )
    )
  )
  sealed trait Api
  sealed trait ApiEnvelope     extends Api
  sealed trait ApiRequest      extends Api
  sealed trait ApiResponse     extends Api
  sealed trait ApiNotification extends ApiResponse

  object Api {

    type ContextId       = UUID
    type ExpressionId    = UUID
    type RequestId       = UUID
    type VisualisationId = UUID

    /** Indicates error response.
      */
    sealed trait Error extends ApiResponse

    /** A representation of a pointer to a method definition.
      */
    case class MethodPointer(
      module: String,
      definedOnType: String,
      name: String
    )

    /** A representation of an executable position in code.
      */
    @JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
    @JsonSubTypes(
      Array(
        new JsonSubTypes.Type(
          value = classOf[StackItem.ExplicitCall],
          name  = "explicitCall"
        ),
        new JsonSubTypes.Type(
          value = classOf[StackItem.LocalCall],
          name  = "localCall"
        )
      )
    )
    sealed trait StackItem
    object StackItem {

      /** A call performed at the top of the stack, to initialize the context.
        */
      case class ExplicitCall(
        methodPointer: MethodPointer,
        thisArgumentExpression: Option[String],
        positionalArgumentsExpressions: Vector[String]
      ) extends StackItem
          with ToLogString {

        /** @inheritdoc */
        override def toLogString(shouldMask: Boolean): String =
          s"ExplicitCall(" +
          s"methodPointer=$methodPointer,thisArgumentExpression=" +
          (if (shouldMask) thisArgumentExpression.map(_ => STUB)
           else thisArgumentExpression) +
          ",positionalArgumentExpression=" +
          (if (shouldMask) positionalArgumentsExpressions.map(_ => STUB)
           else positionalArgumentsExpressions) +
          ")"
      }

      /** A call corresponding to "entering a function call". */
      case class LocalCall(expressionId: ExpressionId) extends StackItem
    }

    /** An update about the computed expression.
      *
      * @param expressionId the expression id
      * @param expressionType the type of expression
      * @param methodCall the pointer to a method definition
      * @param profilingInfo profiling information about the execution of this
      * expression
      * @param fromCache whether or not the value for this expression came
      * from the cache
      * @param payload an extra information about the computed value
      */
    case class ExpressionUpdate(
      expressionId: ExpressionId,
      expressionType: Option[String],
      methodCall: Option[MethodPointer],
      profilingInfo: Vector[ProfilingInfo],
      fromCache: Boolean,
      payload: ExpressionUpdate.Payload
    )
    object ExpressionUpdate {

      /** Base trait for expression payloads. */
      @JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
      @JsonSubTypes(
        Array(
          new JsonSubTypes.Type(
            value = classOf[Payload.Value],
            name  = "expressionUpdatePayloadValue"
          ),
          new JsonSubTypes.Type(
            value = classOf[Payload.DataflowError],
            name  = "expressionUpdatePayloadDataflowError"
          ),
          new JsonSubTypes.Type(
            value = classOf[Payload.Panic],
            name  = "expressionUpdatePayloadPanic"
          )
        )
      )
      sealed trait Payload
      object Payload {

        /** An empty payload. Indicates that the expression was computed to a
          * value.
          */
        case class Value() extends Payload

        /** Indicates that the expression was computed to an error.
          *
          * @param trace the list of expressions leading to the root error.
          */
        case class DataflowError(trace: Seq[ExpressionId]) extends Payload

        /** Indicates that the expression failed with the runtime exception.
          *
          * @param message the error message
          * @param trace the stack trace
          */
        case class Panic(
          message: String,
          trace: Seq[ExpressionId]
        ) extends Payload
            with ToLogString {

          /** @inheritdoc */
          override def toLogString(shouldMask: Boolean): String =
            s"Panic(message=${if (shouldMask) STUB else message},trace=$trace)"
        }

      }
    }

    /** An object representing profiling information about an executed
      * expression.
      */
    @JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
    @JsonSubTypes(
      Array(
        new JsonSubTypes.Type(
          value = classOf[ProfilingInfo.ExecutionTime],
          name  = "executionTime"
        )
      )
    )
    sealed trait ProfilingInfo
    object ProfilingInfo {

      /** A representation of the time elapsed during execution.
        *
        * @param nanoTime the time elapsed during execution in nanoseconds
        */
      case class ExecutionTime(nanoTime: Long) extends ProfilingInfo
    }

    /** An object representing invalidated expressions selector.
      */
    @JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
    @JsonSubTypes(
      Array(
        new JsonSubTypes.Type(
          value = classOf[InvalidatedExpressions.All],
          name  = "all"
        ),
        new JsonSubTypes.Type(
          value = classOf[InvalidatedExpressions.Expressions],
          name  = "expressions"
        )
      )
    )
    sealed trait InvalidatedExpressions
    object InvalidatedExpressions {

      /** An object representing invalidation of all expressions.
        */
      case class All() extends InvalidatedExpressions

      /** An object representing invalidation of a list of expressions.
        *
        * @param value a list of expressions to invalidate.
        */
      case class Expressions(value: Vector[ExpressionId])
          extends InvalidatedExpressions
    }

    /** A notification about updated expressions of the context.
      *
      * @param contextId the context's id.
      * @param updates a list of updates.
      */
    case class ExpressionUpdates(
      contextId: ContextId,
      updates: Set[ExpressionUpdate]
    ) extends ApiNotification

    /** Represents a visualisation context.
      *
      * @param visualisationId a visualisation identifier
      * @param contextId a context identifier
      * @param expressionId an expression identifier
      */
    case class VisualisationContext(
      visualisationId: VisualisationId,
      contextId: ContextId,
      expressionId: ExpressionId
    )

    /** A configuration object for properties of the visualisation.
      *
      * @param executionContextId an execution context of the visualisation
      * @param visualisationModule a qualified name of the module containing
      *                            the expression which creates visualisation
      * @param expression the expression that creates a visualisation
      */
    case class VisualisationConfiguration(
      executionContextId: ContextId,
      visualisationModule: String,
      expression: String
    ) extends ToLogString {

      /** @inheritdoc */
      override def toLogString(shouldMask: Boolean): String =
        s"VisualisationConfiguration(" +
        s"executionContextId=$executionContextId," +
        s"visualisationModule=$visualisationModule,expression=" +
        (if (shouldMask) STUB else expression) +
        ")"
    }

    /** An operation applied to the suggestion argument. */
    @JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
    @JsonSubTypes(
      Array(
        new JsonSubTypes.Type(
          value = classOf[SuggestionArgumentAction.Add],
          name  = "suggestionArgumentActionAdd"
        ),
        new JsonSubTypes.Type(
          value = classOf[SuggestionArgumentAction.Remove],
          name  = "suggestionArgumentActionRemove"
        ),
        new JsonSubTypes.Type(
          value = classOf[SuggestionArgumentAction.Modify],
          name  = "suggestionArgumentActionModify"
        )
      )
    )
    sealed trait SuggestionArgumentAction extends ToLogString
    object SuggestionArgumentAction {

      /** Add the argument to a list.
        *
        * @param index the position of the argument
        * @param argument the argument to add
        */
      case class Add(index: Int, argument: Suggestion.Argument)
          extends SuggestionArgumentAction {

        /** @inheritdoc */
        override def toLogString(shouldMask: Boolean): String =
          "Add(" +
          s"index=$index," +
          s"argument=${argument.toLogString(shouldMask)}" +
          ")"
      }

      /** Remove the argument from a list.
        *
        * @param index the position of the arugment
        */
      case class Remove(index: Int) extends SuggestionArgumentAction {

        /** @inheritdoc */
        override def toLogString(shouldMask: Boolean): String =
          s"Remove(index=$index)"
      }

      /** Modify the argument at the specified index.
        *
        * @param index the position of the argument
        * @param name the name to update
        * @param reprType the argument type to update
        * @param isSuspended the suspended flag to update
        * @param hasDefault the default flag to update
        * @param defaultValue the default value to update
        */
      case class Modify(
        index: Int,
        name: Option[String]                 = None,
        reprType: Option[String]             = None,
        isSuspended: Option[Boolean]         = None,
        hasDefault: Option[Boolean]          = None,
        defaultValue: Option[Option[String]] = None
      ) extends SuggestionArgumentAction {

        /** @inheritdoc */
        override def toLogString(shouldMask: Boolean): String =
          "Modify(" +
          s"index=$index," +
          s"name=$name," +
          s"reprType=$reprType," +
          s"isSuspended=$isSuspended," +
          s"hasDefault=$hasDefault,defaultValue=" +
          (if (shouldMask) defaultValue.map(_ => STUB) else defaultValue) +
          ")"
      }
    }

    /** An operation applied to the update */
    @JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
    @JsonSubTypes(
      Array(
        new JsonSubTypes.Type(
          value = classOf[SuggestionAction.Add],
          name  = "suggestionActionAdd"
        ),
        new JsonSubTypes.Type(
          value = classOf[SuggestionAction.Remove],
          name  = "suggestionActionRemove"
        ),
        new JsonSubTypes.Type(
          value = classOf[SuggestionAction.Modify],
          name  = "suggestionActionModify"
        )
      )
    )
    sealed trait SuggestionAction extends ToLogString
    object SuggestionAction {

      /** Add the suggestion. */
      case class Add() extends SuggestionAction {

        /** @inheritdoc */
        override def toLogString(shouldMask: Boolean): String =
          "Add()"
      }

      /** Remove the suggestion. */
      case class Remove() extends SuggestionAction {

        /** @inheritdoc */
        override def toLogString(shouldMask: Boolean): String =
          "Remove()"
      }

      /** Modify the suggestion.
        *
        * @param externalId the external id to update
        * @param arguments the arguments to update
        * @param returnType the return type to update
        * @param documentation the documentation string to update
        * @param documentationHtml the HTML documentation to update
        * @param scope the scope to update
        * @param reexport the reexport field to update
        */
      case class Modify(
        externalId: Option[Option[Suggestion.ExternalId]] = None,
        arguments: Option[Seq[SuggestionArgumentAction]]  = None,
        returnType: Option[String]                        = None,
        documentation: Option[Option[String]]             = None,
        documentationHtml: Option[Option[String]]         = None,
        scope: Option[Suggestion.Scope]                   = None,
        reexport: Option[Option[String]]                  = None
      ) extends SuggestionAction
          with ToLogString {

        /** @inheritdoc */
        override def toLogString(shouldMask: Boolean): String =
          "Modify(" +
          s"externalId=$externalId," +
          s"artuments=${arguments.map(_.map(_.toLogString(shouldMask)))}," +
          s"returnType=$returnType" +
          s"documentation=" +
          (if (shouldMask) documentation.map(_.map(_ => STUB))
           else documentation) +
          s"documentationHtml=" +
          (if (shouldMask) documentationHtml.map(_.map(_ => STUB))
           else documentationHtml) +
          s",scope=$scope" +
          s"reexport=$reexport" +
          ")"
      }
    }

    /** An action to apply to the suggestions database. */
    @JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
    @JsonSubTypes(
      Array(
        new JsonSubTypes.Type(
          value = classOf[SuggestionsDatabaseAction.Clean],
          name  = "suggestionsDatabaseActionClean"
        )
      )
    )
    sealed trait SuggestionsDatabaseAction
    object SuggestionsDatabaseAction {

      /** Remove all module entries from the database.
        *
        * @param module the module name
        */
      case class Clean(module: String) extends SuggestionsDatabaseAction
    }

    case class ExportsUpdate(
      exports: ModuleExports,
      action: ExportsAction
    )

    @JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
    @JsonSubTypes(
      Array(
        new JsonSubTypes.Type(
          value = classOf[ExportsAction.Add],
          name  = "exportsActionAdd"
        ),
        new JsonSubTypes.Type(
          value = classOf[ExportsAction.Remove],
          name  = "exportsActionRemove"
        )
      )
    )
    sealed trait ExportsAction
    object ExportsAction {
      case class Add()    extends ExportsAction
      case class Remove() extends ExportsAction
    }

    /** A suggestion update.
      *
      * @param suggestion the original suggestion
      * @param action the operation that is applied to the update
      */
    case class SuggestionUpdate(
      suggestion: Suggestion,
      action: SuggestionAction
    ) extends ToLogString {

      /** @inheritdoc */
      override def toLogString(shouldMask: Boolean): String =
        "SuggestionUpdate(suggestion=" +
        suggestion.toLogString(shouldMask) +
        s",action=${action.toLogString(shouldMask)})"
    }

    @JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
    @JsonSubTypes(
      Array(
        new JsonSubTypes.Type(
          value = classOf[DiagnosticType.Error],
          name  = "diagnosticTypeError"
        ),
        new JsonSubTypes.Type(
          value = classOf[DiagnosticType.Warning],
          name  = "diagnosticTypeWarning"
        )
      )
    )
    sealed trait DiagnosticType
    object DiagnosticType {
      case class Error()   extends DiagnosticType
      case class Warning() extends DiagnosticType
    }

    /** The element in the stack trace.
      *
      * @param functionName the function containing the stack call
      * @param file the location of a file
      * @param location the location of the element in a file
      * @param expressionId the id of an expression
      */
    case class StackTraceElement(
      functionName: String,
      file: Option[File],
      location: Option[Range],
      expressionId: Option[ExpressionId]
    ) extends ToLogString {

      /** @inheritdoc */
      override def toLogString(shouldMask: Boolean): String =
        "StackTraceElement(" +
        s"functionName=$functionName," +
        s"file=${file.map(f => MaskedPath(f.toPath).toLogString(shouldMask))}" +
        s"location=$location," +
        s"expressionId=$expressionId" +
        ")"
    }

    @JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
    @JsonSubTypes(
      Array(
        new JsonSubTypes.Type(
          value = classOf[ExecutionResult.Diagnostic],
          name  = "executionOutcomeDiagnostic"
        ),
        new JsonSubTypes.Type(
          value = classOf[ExecutionResult.Failure],
          name  = "executionOutcomeFailure"
        )
      )
    )
    sealed trait ExecutionResult extends ToLogString
    object ExecutionResult {

      /** A diagnostic object produced as a compilation outcome, like error or
        * warning.
        *
        * @param kind the diagnostic type
        * @param message the diagnostic message
        * @param file the location of a file
        * @param location the location of the diagnostic object in a file
        * @param expressionId the id of related expression
        * @param stack the stack trace
        */
      case class Diagnostic(
        kind: DiagnosticType,
        message: String,
        file: Option[File],
        location: Option[model.Range],
        expressionId: Option[ExpressionId],
        stack: Vector[StackTraceElement]
      ) extends ExecutionResult {

        /** @inheritdoc */
        override def toLogString(shouldMask: Boolean): String =
          "Diagnostic(" +
          s"kind=$kind," +
          s"message=${MaskedString(message).toLogString(shouldMask)}," +
          s"file=${file.map(f => MaskedPath(f.toPath).toLogString(shouldMask))}," +
          s"location=$location," +
          s"expressionId=$expressionId," +
          s"stack=${stack.map(_.toLogString(shouldMask))}" +
          ")"
      }

      case object Diagnostic {

        /** Create an error diagnostic message.
          *
          * @param message the diagnostic message
          * @param file the location of a file
          * @param location the location of the diagnostic object in a file
          * @param expressionId the id of related expression
          * @param stack the stack trace
          * @return the instance of an error [[Diagnostic]] message
          */
        def error(
          message: String,
          file: Option[File]                 = None,
          location: Option[model.Range]      = None,
          expressionId: Option[ExpressionId] = None,
          stack: Vector[StackTraceElement]   = Vector()
        ): Diagnostic =
          new Diagnostic(
            DiagnosticType.Error(),
            message,
            file,
            location,
            expressionId,
            stack
          )

        /** Create a warning diagnostic message.
          *
          * @param message the diagnostic message
          * @param file the location of a file
          * @param location the location of the diagnostic object in a file
          * @param expressionId the id of related expression
          * @param stack the stack trace
          * @return the instance of a warning [[Diagnostic]] message
          */
        def warning(
          message: String,
          file: Option[File],
          location: Option[model.Range]      = None,
          expressionId: Option[ExpressionId] = None,
          stack: Vector[StackTraceElement]   = Vector()
        ): Diagnostic =
          new Diagnostic(
            DiagnosticType.Warning(),
            message,
            file,
            location,
            expressionId,
            stack
          )
      }

      /** A critical failure when attempting to execute a context.
        *
        * @param message the error message
        * @param file the location of a file producing the error
        */
      case class Failure(message: String, file: Option[File])
          extends ExecutionResult {

        /** @inheritdoc */
        override def toLogString(shouldMask: Boolean): String =
          s"Failure(message=$message,file=" +
          file.map(f => MaskedPath(f.toPath).toLogString(shouldMask)) +
          ")"
      }

    }

    @JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
    @JsonSubTypes(
      Array(
        new JsonSubTypes.Type(
          value = classOf[Export.Qualified],
          name  = "exportQualified"
        ),
        new JsonSubTypes.Type(
          value = classOf[Export.Unqualified],
          name  = "exportUnqualified"
        )
      )
    )
    sealed trait Export {
      def module: String
    }
    object Export {

      /** Qualified module re-export.
        *
        * @param module the module name that exports the given module
        * @param alias new module name if the module was renamed in the export
        * clause
        */
      case class Qualified(module: String, alias: Option[String]) extends Export

      /** Unqualified module export.
        *
        * @param module the module name that exports the given module
        */
      case class Unqualified(module: String) extends Export
    }

    /** The notification about the execution status.
      *
      * @param contextId the context's id
      * @param diagnostics the list of diagnostic messages
      */
    case class ExecutionUpdate(
      contextId: ContextId,
      diagnostics: Seq[ExecutionResult.Diagnostic]
    ) extends ApiNotification
        with ToLogString {

      /** @inheritdoc */
      override def toLogString(shouldMask: Boolean): String =
        "ExecutionUpdate(" +
        s"contextId=$contextId,diagnostics=" +
        diagnostics.map(_.toLogString(shouldMask)) +
        ")"
    }

    /** Signals about the critical failure during the context execution.
      *
      * @param contextId the context's id
      * @param failure the error description
      */
    case class ExecutionFailed(
      contextId: ContextId,
      failure: ExecutionResult.Failure
    ) extends ApiNotification
        with ToLogString {

      /** @inheritdoc */
      override def toLogString(shouldMask: Boolean): String =
        "ExecutionFailed(" +
        s"contextId=$contextId,failure=" +
        failure.toLogString(shouldMask) +
        ")"
    }

    /** An event signaling a visualisation update.
      *
      * @param visualisationContext a visualisation context
      * @param data a visualisation data
      */
    case class VisualisationUpdate(
      visualisationContext: VisualisationContext,
      data: Array[Byte]
    ) extends ApiNotification
        with ToLogString {

      override def toLogString(shouldMask: Boolean): String = {
        "VisualisationUpdate(" +
        s"visualisationContext=$visualisationContext,data=" +
        (if (shouldMask) STUB else data.toString()) +
        ")"
      }
    }

    /** Envelope for an Api request.
      *
      * @param requestId the request identifier.
      * @param payload the request payload.
      */
    case class Request(requestId: Option[RequestId], payload: ApiRequest)
        extends ApiEnvelope

    object Request {

      /** A smart constructor for [[Request]].
        *
        * @param requestId the reqest identifier.
        * @param payload the request payload.
        * @return a request object with specified request id and payload.
        */
      def apply(requestId: RequestId, payload: ApiRequest): Request =
        Request(Some(requestId), payload)

      /** A smart constructor for [[Request]].
        *
        * @param payload the request payload.
        * @return a request object without request id and specified payload.
        */
      def apply(payload: ApiRequest): Request =
        Request(None, payload)
    }

    /** Envelope for an Api response.
      *
      * @param correlationId request that initiated the response
      * @param payload response
      */
    case class Response(correlationId: Option[RequestId], payload: ApiResponse)
        extends ApiEnvelope

    object Response {

      /** A smart constructor for [[Response]].
        *
        * @param correlationId the request id triggering this response.
        * @param payload the response payload.
        * @return a response object with specified correlation id and payload.
        */
      def apply(correlationId: RequestId, payload: ApiResponse): Response =
        Response(Some(correlationId), payload)

      /** A smart constructor for [[Response]] that was not triggered by
        * any request (i.e. a notification).
        *
        * @param payload the data carried by the response.
        * @return a response without a correlation id and specified payload.
        */
      def apply(payload: ApiResponse): Response = Response(None, payload)
    }

    /** A Request sent from the client to the runtime server, to create a new
      * execution context with a given id.
      *
      * @param contextId the newly created context's id.
      */
    case class CreateContextRequest(contextId: ContextId) extends ApiRequest

    /** A response sent from the server upon handling the [[CreateContextRequest]]
      *
      * @param contextId the newly created context's id.
      */
    case class CreateContextResponse(contextId: ContextId) extends ApiResponse

    /** A Request sent from the client to the runtime server, to destroy an
      * execution context with a given id.
      *
      * @param contextId the destroyed context's id.
      */
    case class DestroyContextRequest(contextId: ContextId) extends ApiRequest

    /** A success response sent from the server upon handling the
      * [[DestroyContextRequest]]
      *
      * @param contextId the destroyed context's id
      */
    case class DestroyContextResponse(contextId: ContextId) extends ApiResponse

    /** A Request sent from the client to the runtime server, to move
      * the execution context to a new location deeper down the stack.
      *
      * @param contextId the context's id.
      * @param stackItem an item that should be pushed on the stack.
      */
    case class PushContextRequest(contextId: ContextId, stackItem: StackItem)
        extends ApiRequest

    /** A response sent from the server upon handling the [[PushContextRequest]]
      *
      * @param contextId the context's id.
      */
    case class PushContextResponse(contextId: ContextId) extends ApiResponse

    /** A Request sent from the client to the runtime server, to move
      * the execution context up the stack.
      *
      * @param contextId the context's id.
      */
    case class PopContextRequest(contextId: ContextId) extends ApiRequest

    /** A response sent from the server upon handling the [[PopContextRequest]]
      *
      * @param contextId the context's id.
      */
    case class PopContextResponse(contextId: ContextId) extends ApiResponse

    /** A Request sent from the client to the runtime server, to recompute
      * the execution context.
      *
      * @param contextId the context's id.
      * @param expressions the selector specifying which expressions should be
      * recomputed.
      */
    case class RecomputeContextRequest(
      contextId: ContextId,
      expressions: Option[InvalidatedExpressions]
    ) extends ApiRequest

    /** A response sent from the server upon handling the
      * [[RecomputeContextRequest]]
      *
      * @param contextId the context's id.
      */
    case class RecomputeContextResponse(contextId: ContextId)
        extends ApiResponse

    /** An error response signifying a non-existent context.
      *
      * @param contextId the context's id
      */
    case class ContextNotExistError(contextId: ContextId) extends Error

    /** Signals that a module cannot be found.
      *
      * @param moduleName the module name
      */
    case class ModuleNotFound(moduleName: String) extends Error

    /** Signals that execution of a context completed.
      *
      * @param contextId the context's id
      */
    case class ExecutionComplete(contextId: ContextId) extends ApiNotification

    /** Signals that an expression specified in a [[AttachVisualisation]] or
      * a [[ModifyVisualisation]] cannot be evaluated.
      *
      * @param message the reason of the failure
      * @param failure the detailed information about the failure
      */
    case class VisualisationExpressionFailed(
      message: String,
      failure: Option[ExecutionResult.Diagnostic]
    ) extends Error
        with ToLogString {

      /** @inheritdoc */
      override def toLogString(shouldMask: Boolean): String =
        "VisualisationExpressionFailed(" +
        s"message=${MaskedString(message).toLogString(shouldMask)}," +
        s"failure=${failure.map(_.toLogString(shouldMask))}" +
        ")"
    }

    /** Signals that an evaluation of a code responsible for generating
      * visualisation data failed.
      *
      * @param contextId the context's id.
      * @param visualisationId the visualisation identifier
      * @param expressionId the identifier of a visualised expression
      * @param message the reason of the failure
      * @param diagnostic the detailed information about the failure
      */
    case class VisualisationEvaluationFailed(
      contextId: ContextId,
      visualisationId: VisualisationId,
      expressionId: ExpressionId,
      message: String,
      diagnostic: Option[ExecutionResult.Diagnostic]
    ) extends ApiNotification
        with ToLogString {

      /** @inheritdoc */
      override def toLogString(shouldMask: Boolean): String =
        "VisualisationEvaluationFailed(" +
        s"contextId=$contextId," +
        s"visualisationId=$visualisationId," +
        s"expressionId=$expressionId," +
        s"message=${MaskedString(message).toLogString(shouldMask)}," +
        s"diagnostic=${diagnostic.map(_.toLogString(shouldMask))}" +
        ")"
    }

    /** Signals that visualisation cannot be found. */
    case class VisualisationNotFound() extends Error

    /** An error response signifying that stack is empty.
      *
      * @param contextId the context's id
      */
    case class EmptyStackError(contextId: ContextId) extends Error

    /** An error response signifying that stack item is invalid.
      *
      * @param contextId the context's id
      */
    case class InvalidStackItemError(contextId: ContextId) extends Error

    /** A notification sent to the server about switching a file to literal
      * contents.
      *
      * @param path the file being moved to memory.
      * @param contents the current file contents.
      */
    case class OpenFileNotification(
      path: File,
      contents: String
    ) extends ApiRequest
        with ToLogString {

      /** @inheritdoc */
      override def toLogString(shouldMask: Boolean): String =
        "OpenFileNotification(" +
        s"path=${MaskedPath(path.toPath).toLogString(shouldMask)}," +
        s"contents=${MaskedString(contents).toLogString(shouldMask)}," +
        ")"
    }

    /** A notification sent to the server about in-memory file contents being
      * edited.
      *
      * @param path the file being edited.
      * @param edits the diffs to apply to the contents.
      */
    case class EditFileNotification(path: File, edits: Seq[TextEdit])
        extends ApiRequest
        with ToLogString {

      /** @inheritdoc */
      override def toLogString(shouldMask: Boolean): String =
        "EditFileNotification(" +
        s"path=${MaskedPath(path.toPath).toLogString(shouldMask)},edits=" +
        (if (shouldMask) edits.map(_ => STUB) else edits) +
        ")"
    }

    /** A notification sent to the server about dropping the file from memory
      * back to on-disk version.
      *
      * @param path the file being closed.
      */
    case class CloseFileNotification(path: File)
        extends ApiRequest
        with ToLogString {

      /** @inheritdoc */
      override def toLogString(shouldMask: Boolean): String =
        s"CloseFileNotification(path=${MaskedPath(path.toPath).toLogString(shouldMask)})"
    }

    /** Notification sent from the server to the client upon successful
      * initialization. Any messages sent to the server before receiving this
      * message will be dropped.
      */
    case class InitializedNotification() extends ApiResponse

    /** A request sent from the client to the runtime server, to create a new
      * visualisation for an expression identified by `expressionId`.
      *
      * @param visualisationId an identifier of a visualisation
      * @param expressionId an identifier of an expression which is visualised
      * @param visualisationConfig a configuration object for properties of the
      *                            visualisation
      */
    case class AttachVisualisation(
      visualisationId: VisualisationId,
      expressionId: ExpressionId,
      visualisationConfig: VisualisationConfiguration
    ) extends ApiRequest
        with ToLogString {

      /** @inheritdoc */
      override def toLogString(shouldMask: Boolean): String =
        s"AttachVisualisation(" +
        s"visualisationId=$visualisationId," +
        s"expressionId=$expressionId,visualisationConfig=" +
        visualisationConfig.toLogString(shouldMask) +
        ")"
    }

    /** Signals that attaching a visualisation has succeeded.
      */
    case class VisualisationAttached() extends ApiResponse

    /** A request sent from the client to the runtime server, to detach a
      * visualisation from an expression identified by `expressionId`.
      *
      * @param contextId an execution context identifier
      * @param visualisationId an identifier of a visualisation
      * @param expressionId an identifier of an expression which is visualised
      */
    case class DetachVisualisation(
      contextId: ContextId,
      visualisationId: VisualisationId,
      expressionId: ExpressionId
    ) extends ApiRequest

    /** Signals that detaching a visualisation has succeeded.
      */
    case class VisualisationDetached() extends ApiResponse

    /** A request sent from the client to the runtime server, to modify a
      * visualisation identified by `visualisationId`.
      *
      * @param visualisationId     an identifier of a visualisation
      * @param visualisationConfig a configuration object for properties of the
      *                            visualisation
      */
    case class ModifyVisualisation(
      visualisationId: VisualisationId,
      visualisationConfig: VisualisationConfiguration
    ) extends ToLogString
        with ApiRequest {

      /** @inheritdoc */
      override def toLogString(shouldMask: Boolean): String =
        "ModifyVisualisation(" +
        s"visualisationId=$visualisationId,visualisationConfig=" +
        visualisationConfig.toLogString(shouldMask) +
        ")"
    }

    /** Signals that a visualisation modification has succeeded.
      */
    case class VisualisationModified() extends ApiResponse

    /** A request to shut down the runtime server.
      */
    case class ShutDownRuntimeServer() extends ApiRequest

    /** Signals that the runtime server has been shut down.
      */
    case class RuntimeServerShutDown() extends ApiResponse

    /** A request for project renaming.
      *
      * @param namespace the namespace the renamed project belongs to
      * @param oldName the old project name
      * @param newName the new project name
      */
    case class RenameProject(
      namespace: String,
      oldName: String,
      newName: String
    ) extends ApiRequest

    /** Signals that project has been renamed.
      *
      * @param namespace the namespace of the project
      * @param newName the new project name
      */
    case class ProjectRenamed(namespace: String, newName: String)
        extends ApiResponse

    /** A notification about the changes in the suggestions database.
      *
      * @param module the module name
      * @param version the version of the module
      * @param actions the list of actions to apply to the suggestions database
      * @param exports the list of re-exported symbols
      * @param updates the list of suggestions extracted from module
      */
    case class SuggestionsDatabaseModuleUpdateNotification(
      module: String,
      version: ContentVersion,
      actions: Vector[SuggestionsDatabaseAction],
      exports: Seq[ExportsUpdate],
      updates: Tree[SuggestionUpdate]
    ) extends ApiNotification
        with ToLogString {

      /** @inheritdoc */
      override def toLogString(shouldMask: Boolean): String =
        "SuggestionsDatabaseModuleUpdateNotification(" +
        s"module=$module," +
        s"version=$version," +
        s"actions=$actions," +
        s"exports=$exports" +
        s"updates=${updates.map(_.toLogString(shouldMask))}" +
        ")"
    }

    /** A request to invalidate the indexed flag of the modules. */
    case class InvalidateModulesIndexRequest() extends ApiRequest

    /** Signals that the module indexes has been invalidated. */
    case class InvalidateModulesIndexResponse() extends ApiResponse

    /** A request to verify the modules in the suggestions database.
      *
      * @param modules the list of modules
      */
    case class VerifyModulesIndexRequest(modules: Seq[String])
        extends ApiRequest

    /** A response to the module verification request.
      *
      * @param remove the list of modules to remove from suggestions database.
      */
    case class VerifyModulesIndexResponse(remove: Seq[String])
        extends ApiResponse

    /** A request to return info needed to import the suggestion.
      *
      * @param suggestion the suggestion to import
      */
    case class ImportSuggestionRequest(suggestion: Suggestion)
        extends ApiRequest
        with ToLogString {

      /** @inheritdoc */
      override def toLogString(shouldMask: Boolean): String =
        s"ImportSuggestionRequest(suggestion=${suggestion.toLogString(shouldMask)})"
    }

    /** The result of the import request.
      *
      * @param module the definition module of the symbol
      * @param symbol the resolved symbol
      * @param exports the list of exports of the symbol
      */
    case class ImportSuggestionResponse(
      module: String,
      symbol: String,
      exports: Seq[Export]
    ) extends ApiResponse

    /** A request for the type hierarchy graph. */
    case class GetTypeGraphRequest() extends ApiRequest

    /** The result of the type graph request.
      *
      * @param graph the graph.
      */
    case class GetTypeGraphResponse(graph: TypeGraph) extends ApiResponse

    /** Signals that a new library has been imported, which means its content
      * root should be registered.
      *
      * @param namespace namespace of the loaded library
      * @param name name of the loaded library
      * @param version library version that was selected
      * @param location location on disk of the project root belonging to the
      *                 loaded library
      */
    case class LibraryLoaded(
      namespace: String,
      name: String,
      version: String,
      location: File
    ) extends ApiNotification

    /** A notification containing updates on the progress of long-running tasks.
      *
      * @param payload the actual update contained within this notification
      */
    case class ProgressNotification(
      payload: ProgressNotification.NotificationType
    ) extends ApiNotification

    object ProgressNotification {
      sealed trait NotificationType

      /** Indicates that a new task has been started. */
      case class TaskStarted(
        taskId: UUID,
        relatedOperation: String,
        unit: String,
        total: Option[Long]
      ) extends NotificationType

      /** Indicates that the task has progressed. */
      case class TaskProgressUpdate(
        taskId: UUID,
        message: Option[String],
        done: Long
      ) extends NotificationType

      /** Indicates that the task has been finished. */
      case class TaskFinished(
        taskId: UUID,
        message: Option[String],
        success: Boolean
      ) extends NotificationType
    }

    private lazy val mapper = {
      val factory = new CBORFactory()
      val mapper  = new ObjectMapper(factory) with ScalaObjectMapper
      mapper.registerModule(DefaultScalaModule)
    }

    /** Serializes a Request into a byte buffer.
      *
      * @param message the message to serialize.
      * @return the serialized version of the message.
      */
    def serialize(message: Request): ByteBuffer =
      ByteBuffer.wrap(mapper.writeValueAsBytes(message))

    /** Serializes a Response into a byte buffer.
      *
      * @param message the message to serialize.
      * @return the serialized version of the message.
      */
    def serialize(message: Response): ByteBuffer =
      ByteBuffer.wrap(mapper.writeValueAsBytes(message))

    /** Deserializes a byte buffer into a Request message.
      *
      * @param bytes the buffer to deserialize
      * @return the deserialized message, if the byte buffer can be deserialized.
      */
    def deserializeRequest(bytes: ByteBuffer): Option[Request] =
      Try(mapper.readValue(bytes.array(), classOf[Request])).toOption

    /** Deserializes a byte buffer into a Response message.
      *
      * @param bytes the buffer to deserialize
      * @return the deserialized message, if the byte buffer can be deserialized.
      */
    def deserializeResponse(bytes: ByteBuffer): Option[Response] =
      Try(mapper.readValue(bytes.array(), classOf[Response])).toOption
  }

}
