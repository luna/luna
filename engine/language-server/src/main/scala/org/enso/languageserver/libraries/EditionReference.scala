package org.enso.languageserver.libraries

import io.circe.{Decoder, DecodingFailure, Encoder, Json}
import io.circe.syntax._
import io.circe.generic.auto._

/** A reference to an edition - either a named edition or an unnamed one
  * associated with the current project.
  */
sealed trait EditionReference
object EditionReference {

  /** An edition identified by its name. */
  case class NamedEdition(editionName: String) extends EditionReference

  /** The edition associated with the current project. */
  case object CurrentProjectEdition extends EditionReference

  object CodecField {
    val Type        = "type"
    val EditionName = "editionName"
  }

  object CodecType {
    val NamedEdition          = "NamedEdition"
    val CurrentProjectEdition = "CurrentProjectEdition"
  }

  implicit val encoder: Encoder[EditionReference] = {
    case NamedEdition(editionName) =>
      Json.obj(
        CodecField.Type        -> CodecType.NamedEdition.asJson,
        CodecField.EditionName -> editionName.asJson
      )
    case CurrentProjectEdition =>
      Json.obj(CodecField.Type -> CodecType.CurrentProjectEdition.asJson)
  }

  implicit val decoder: Decoder[EditionReference] = { json =>
    val typeCursor = json.downField(CodecField.Type)
    typeCursor.as[String].flatMap {
      case CodecType.NamedEdition =>
        Decoder[NamedEdition].tryDecode(json)
      case CodecType.CurrentProjectEdition => Right(CurrentProjectEdition)
      case unknownType =>
        Left(
          DecodingFailure(
            s"Unknown EditionReference type [$unknownType].",
            typeCursor.history
          )
        )
    }
  }
}
