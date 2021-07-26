package org.enso.languageserver.search

import enumeratum._
import io.circe.generic.auto._
import io.circe.syntax._
import io.circe.{Decoder, Encoder, Json}
import org.enso.languageserver.filemanager.{FileSystemFailure, Path}
import org.enso.polyglot.Suggestion
import org.enso.searcher.SuggestionEntry
import org.enso.text.editing.model.Position

object SearchProtocol {

  type SuggestionId = Long

  private object CodecField {

    val Type = "type"
  }

  private object CodecType {

    val Add = "Add"

    val Remove = "Remove"

    val Modify = "Modify"
  }

  private object SuggestionType {

    val Module = "module"

    val Atom = "atom"

    val Method = "method"

    val Function = "function"

    val Local = "local"
  }

  implicit val suggestionEncoder: Encoder[Suggestion] =
    Encoder.instance[Suggestion] {
      case module: Suggestion.Module =>
        Encoder[Suggestion.Module]
          .apply(module)
          .deepMerge(Json.obj(CodecField.Type -> SuggestionType.Module.asJson))
      case atom: Suggestion.Atom =>
        Encoder[Suggestion.Atom]
          .apply(atom)
          .deepMerge(Json.obj(CodecField.Type -> SuggestionType.Atom.asJson))
          .dropNullValues

      case method: Suggestion.Method =>
        Encoder[Suggestion.Method]
          .apply(method)
          .deepMerge(
            Json.obj(CodecField.Type -> SuggestionType.Method.asJson)
          )
          .dropNullValues

      case function: Suggestion.Function =>
        Encoder[Suggestion.Function]
          .apply(function)
          .deepMerge(
            Json.obj(CodecField.Type -> SuggestionType.Function.asJson)
          )
          .dropNullValues

      case local: Suggestion.Local =>
        Encoder[Suggestion.Local]
          .apply(local)
          .deepMerge(Json.obj(CodecField.Type -> SuggestionType.Local.asJson))
          .dropNullValues
    }

  implicit val suggestionDecoder: Decoder[Suggestion] =
    Decoder.instance { cursor =>
      cursor.downField(CodecField.Type).as[String].flatMap {
        case SuggestionType.Module =>
          Decoder[Suggestion.Module].tryDecode(cursor)

        case SuggestionType.Atom =>
          Decoder[Suggestion.Atom].tryDecode(cursor)

        case SuggestionType.Method =>
          Decoder[Suggestion.Method].tryDecode(cursor)

        case SuggestionType.Function =>
          Decoder[Suggestion.Function].tryDecode(cursor)

        case SuggestionType.Local =>
          Decoder[Suggestion.Local].tryDecode(cursor)
      }
    }

  /** The object representing a field modification.
    *
    * @param tag the modifying action
    * @param value the updated value
    */
  case class FieldUpdate[A](tag: FieldAction, value: Option[A])

  /** The modifying action on the field. */
  sealed trait FieldAction extends EnumEntry
  object FieldAction extends Enum[FieldAction] with CirceEnum[FieldAction] {

    case object Remove extends FieldAction
    case object Set    extends FieldAction

    override def values = findValues
  }

  /** An operation applied to the suggestion argument. */
  sealed trait SuggestionArgumentUpdate
  object SuggestionArgumentUpdate {

    /** Add the argument to a list.
      *
      * @param index the position of the argument
      * @param argument the argument to add
      */
    case class Add(index: Int, argument: Suggestion.Argument)
        extends SuggestionArgumentUpdate

    /** Remove the argument from a list.
      *
      * @param index the position of the arugment
      */
    case class Remove(index: Int) extends SuggestionArgumentUpdate

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
      name: Option[FieldUpdate[String]]         = None,
      reprType: Option[FieldUpdate[String]]     = None,
      isSuspended: Option[FieldUpdate[Boolean]] = None,
      hasDefault: Option[FieldUpdate[Boolean]]  = None,
      defaultValue: Option[FieldUpdate[String]] = None
    ) extends SuggestionArgumentUpdate

    implicit val suggestionArgumentActionDecoder
      : Decoder[SuggestionArgumentUpdate] =
      Decoder.instance { cursor =>
        cursor.downField(CodecField.Type).as[String].flatMap {
          case CodecType.Add =>
            Decoder[SuggestionArgumentUpdate.Add].tryDecode(cursor)

          case CodecType.Remove =>
            Decoder[SuggestionArgumentUpdate.Remove].tryDecode(cursor)

          case CodecType.Modify =>
            Decoder[SuggestionArgumentUpdate.Modify].tryDecode(cursor)
        }
      }

    implicit val suggestionArgumentActionEncoder
      : Encoder[SuggestionArgumentUpdate] =
      Encoder.instance[SuggestionArgumentUpdate] {
        case add: SuggestionArgumentUpdate.Add =>
          Encoder[SuggestionArgumentUpdate.Add]
            .apply(add)
            .deepMerge(Json.obj(CodecField.Type -> CodecType.Add.asJson))
            .dropNullValues

        case remove: SuggestionArgumentUpdate.Remove =>
          Encoder[SuggestionArgumentUpdate.Remove]
            .apply(remove)
            .deepMerge(Json.obj(CodecField.Type -> CodecType.Remove.asJson))
            .dropNullValues

        case modify: SuggestionArgumentUpdate.Modify =>
          Encoder[SuggestionArgumentUpdate.Modify]
            .apply(modify)
            .deepMerge(Json.obj(CodecField.Type -> CodecType.Modify.asJson))
            .dropNullValues
      }
  }

  /** Base trait for suggestion database updaetes. */
  sealed trait SuggestionsDatabaseUpdate
  object SuggestionsDatabaseUpdate {

    /** Create or replace the database entry.
      *
      * @param id the suggestion id
      * @param suggestion the new suggestion
      */
    case class Add(id: SuggestionId, suggestion: Suggestion)
        extends SuggestionsDatabaseUpdate

    /** Remove the database entry.
      *
      * @param id the suggestion id
      */
    case class Remove(id: SuggestionId) extends SuggestionsDatabaseUpdate

    /** Modify the database entry.
      *
      * @param id the suggestion id
      * @param externalId the external id to update
      * @param arguments the arguments to update
      * @param module the module name to update
      * @param selfType the self type to update
      * @param returnType the return type to update
      * @param documentation the documentation string to update
      * @param documentationHtml the HTML documentation to update
      * @param scope the scope to update
      * @param reexport the module reexporting the suggestion
      */
    case class Modify(
      id: SuggestionId,
      externalId: Option[FieldUpdate[Suggestion.ExternalId]] = None,
      arguments: Option[Seq[SuggestionArgumentUpdate]]       = None,
      module: Option[FieldUpdate[String]]                    = None,
      selfType: Option[FieldUpdate[String]]                  = None,
      returnType: Option[FieldUpdate[String]]                = None,
      documentation: Option[FieldUpdate[String]]             = None,
      documentationHtml: Option[FieldUpdate[String]]         = None,
      scope: Option[FieldUpdate[Suggestion.Scope]]           = None,
      reexport: Option[FieldUpdate[String]]                  = None
    ) extends SuggestionsDatabaseUpdate

    implicit val decoder: Decoder[SuggestionsDatabaseUpdate] =
      Decoder.instance { cursor =>
        cursor.downField(CodecField.Type).as[String].flatMap {
          case CodecType.Add =>
            Decoder[SuggestionsDatabaseUpdate.Add].tryDecode(cursor)

          case CodecType.Remove =>
            Decoder[SuggestionsDatabaseUpdate.Remove].tryDecode(cursor)

          case CodecType.Modify =>
            Decoder[SuggestionsDatabaseUpdate.Modify].tryDecode(cursor)
        }
      }

    implicit val encoder: Encoder[SuggestionsDatabaseUpdate] =
      Encoder.instance[SuggestionsDatabaseUpdate] {
        case add: SuggestionsDatabaseUpdate.Add =>
          Encoder[SuggestionsDatabaseUpdate.Add]
            .apply(add)
            .deepMerge(Json.obj(CodecField.Type -> CodecType.Add.asJson))
            .dropNullValues

        case remove: SuggestionsDatabaseUpdate.Remove =>
          Encoder[SuggestionsDatabaseUpdate.Remove]
            .apply(remove)
            .deepMerge(Json.obj(CodecField.Type -> CodecType.Remove.asJson))

        case modify: SuggestionsDatabaseUpdate.Modify =>
          Encoder[SuggestionsDatabaseUpdate.Modify]
            .apply(modify)
            .deepMerge(Json.obj(CodecField.Type -> CodecType.Modify.asJson))
            .dropNullValues
      }
  }

  /** The type of a suggestion. */
  sealed trait SuggestionKind extends EnumEntry
  object SuggestionKind
      extends Enum[SuggestionKind]
      with CirceEnum[SuggestionKind] {

    /** A module suggestion. */
    case object Module extends SuggestionKind

    /** An atom suggestion. */
    case object Atom extends SuggestionKind

    /** A method suggestion. */
    case object Method extends SuggestionKind

    /** A function suggestion. */
    case object Function extends SuggestionKind

    /** Local binding suggestion. */
    case object Local extends SuggestionKind

    override val values = findValues

    /** Create API kind from the [[Suggestion.Kind]]
      *
      * @param kind the suggestion kind
      * @return the API kind
      */
    def apply(kind: Suggestion.Kind): SuggestionKind =
      kind match {
        case Suggestion.Kind.Module   => Module
        case Suggestion.Kind.Atom     => Atom
        case Suggestion.Kind.Method   => Method
        case Suggestion.Kind.Function => Function
        case Suggestion.Kind.Local    => Local
      }

    /** Convert from API kind to [[Suggestion.Kind]]
      *
      * @param kind the API kind
      * @return the suggestion kind
      */
    def toSuggestion(kind: SuggestionKind): Suggestion.Kind =
      kind match {
        case Module   => Suggestion.Kind.Module
        case Atom     => Suggestion.Kind.Atom
        case Method   => Suggestion.Kind.Method
        case Function => Suggestion.Kind.Function
        case Local    => Suggestion.Kind.Local
      }
  }

  /** The entry in the suggestions database.
    *
    * @param id the suggestion id
    * @param suggestion the suggestion
    */
  case class SuggestionDatabaseEntry(id: SuggestionId, suggestion: Suggestion)

  object SuggestionDatabaseEntry {

    /** Create the database entry from the polyglot suggestion entry.
      *
      * @param entry the suggestion entry
      * @return the database entry
      */
    def apply(entry: SuggestionEntry): SuggestionDatabaseEntry =
      new SuggestionDatabaseEntry(entry.id, entry.suggestion)

    private object CodecField {

      val Id = "id"

      val Suggestion = "suggestion"
    }

    implicit val encoder: Encoder[SuggestionDatabaseEntry] =
      Encoder.instance { entry =>
        Json.obj(
          CodecField.Id         -> entry.id.asJson,
          CodecField.Suggestion -> Encoder[Suggestion].apply(entry.suggestion)
        )
      }

    implicit val decoder: Decoder[SuggestionDatabaseEntry] =
      Decoder.instance { cursor =>
        for {
          id         <- cursor.downField(CodecField.Id).as[SuggestionId]
          suggestion <- cursor.downField(CodecField.Suggestion).as[Suggestion]
        } yield SuggestionDatabaseEntry(id, suggestion)
      }
  }

  /** A notification about changes in the suggestions database.
    *
    * @param currentVersion current version of the suggestions database
    * @param updates the list of database updates
    */
  case class SuggestionsDatabaseUpdateNotification(
    currentVersion: Long,
    updates: Seq[SuggestionsDatabaseUpdate]
  )

  /** The request to receive contents of the suggestions database. */
  case object GetSuggestionsDatabase

  /** The reply to the [[GetSuggestionsDatabase]] request.
    *
    * @param currentVersion current version of the suggestions database
    * @param entries the entries of the suggestion database
    */
  case class GetSuggestionsDatabaseResult(
    currentVersion: Long,
    entries: Seq[SuggestionDatabaseEntry]
  )

  /** The request to receive the current version of the suggestions database. */
  case object GetSuggestionsDatabaseVersion

  /** The reply to the [[GetSuggestionsDatabaseVersion]] request.
    *
    * @param version current version of the suggestions database
    */
  case class GetSuggestionsDatabaseVersionResult(version: Long)

  /** The completion request.
    *
    * @param file the edited file
    * @param position the cursor position
    * @param selfType filter entries matching the self type
    * @param returnType filter entries matching the return type
    * @param tags filter entries by suggestion type
    */
  case class Completion(
    file: Path,
    position: Position,
    selfType: Option[String],
    returnType: Option[String],
    tags: Option[Seq[SuggestionKind]]
  )

  /** The reply to the [[Completion]] request.
    *
    * @param currentVersion current version of the suggestions database
    * @param results the list of suggestion ids matched the search query
    */
  case class CompletionResult(currentVersion: Long, results: Seq[SuggestionId])

  /** The request returning the info about the suggestion import.
    *
    * @param id the requested suggestion id
    */
  case class Import(id: SuggestionId)

  /** The request returning the info about the suggestion import.
    *
    * @param suggestion the requested suggestion
    */
  case class ImportSuggestion(suggestion: Suggestion)

  /** Base trait for export statements. */
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

    private object CodecType {

      val Qualified = "Qualified"

      val Unqualified = "Unqualified"
    }

    implicit val encoder: Encoder[Export] =
      Encoder.instance {
        case qualified: Qualified =>
          Encoder[Export.Qualified]
            .apply(qualified)
            .deepMerge(Json.obj(CodecField.Type -> CodecType.Qualified.asJson))
            .dropNullValues

        case unqualified: Unqualified =>
          Encoder[Export.Unqualified]
            .apply(unqualified)
            .deepMerge(
              Json.obj(CodecField.Type -> CodecType.Unqualified.asJson)
            )
            .dropNullValues
      }

    implicit val decoder: Decoder[Export] =
      Decoder.instance { cursor =>
        cursor.downField(CodecField.Type).as[String].flatMap {
          case CodecType.Qualified =>
            Decoder[Export.Qualified].tryDecode(cursor)

          case CodecType.Unqualified =>
            Decoder[Export.Unqualified].tryDecode(cursor)
        }
      }
  }

  /** The result of the import request.
    *
    * @param module the definition module of the symbol
    * @param symbol the resolved symbol
    * @param exports the list of re-exports
    */
  case class ImportResult(
    module: String,
    symbol: String,
    exports: Seq[Export]
  )

  /** The request to invalidate the modules index. */
  case object InvalidateModulesIndex

  /** The request to invalidate the suggestions database. */
  case object InvalidateSuggestionsDatabase

  /** The reply to the invalidate request. */
  case object InvalidateSuggestionsDatabaseResult

  /** Base trait for search request errors. */
  sealed trait SearchFailure

  /** Signals about file system error. */
  case class FileSystemError(e: FileSystemFailure) extends SearchFailure

  /** Signals that the project not found in the root directory. */
  case object ProjectNotFoundError extends SearchFailure

  /** Signals that the requested suggestion was not found. */
  case object SuggestionNotFoundError extends SearchFailure

  /** Signals that the module name can not be resolved for the given file.
    *
    * @param file the file path
    */
  case class ModuleNameNotResolvedError(file: Path) extends SearchFailure
}
