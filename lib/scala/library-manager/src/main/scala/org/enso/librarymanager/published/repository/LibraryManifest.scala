package org.enso.librarymanager.published.repository

import io.circe.Decoder
import org.enso.editions.LibraryName

/** The manifest file containing metadata related to a published library.
  *
  * @param archives sequence of sub-archives that the library package is
  *                 composed of
  * @param dependencies sequence of direct dependencies of the library
  * @param tagLine a short description of the library
  * @param description a longer description of the library, for the Marketplace
  */
case class LibraryManifest(
  archives: Seq[String],
  dependencies: Seq[LibraryName],
  tagLine: Option[String],
  description: Option[String]
)

object LibraryManifest {
  object Fields {
    val archives     = "archives"
    val dependencies = "dependencies"
    val tagLine      = "tag-line"
    val description  = "description"
  }

  /** A [[Decoder]] instance for parsing [[LibraryManifest]]. */
  implicit val decoder: Decoder[LibraryManifest] = { json =>
    for {
      archives <- json.get[Seq[String]](Fields.archives)
      dependencies <- json.getOrElse[Seq[LibraryName]](Fields.dependencies)(
        Seq()
      )
      tagLine     <- json.get[Option[String]](Fields.tagLine)
      description <- json.get[Option[String]](Fields.description)
    } yield LibraryManifest(
      archives     = archives,
      dependencies = dependencies,
      tagLine      = tagLine,
      description  = description
    )
  }

  /** The name of the manifest file as included in the directory associated with
    * a given library in the library repository.
    */
  val filename = "manifest.yaml"
}
