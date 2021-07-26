package org.enso.editions

import io.circe.Decoder

/** A helper type to handle special parsing logic of edition names.
  *
  * The issue is that if an edition is called `2021.4` and it is written
  * unquoted inside of a YAML file, that is treated as a floating point
  * number, so special care must be taken to correctly parse it.
  */
case class EditionName(name: String) extends AnyVal {

  /** Returns the name of the file that is associated with the edition name. */
  def toFileName: String = name + EditionName.editionSuffix
}

object EditionName {

  /** A helper method for constructing an [[EditionName]]. */
  def apply(name: String): EditionName = new EditionName(name)

  /** A [[Decoder]] instance for [[EditionName]] that accepts not only strings
    * but also numbers as valid edition names.
    */
  implicit val editionNameDecoder: Decoder[EditionName] = { json =>
    json
      .as[String]
      .orElse(json.as[Int].map(_.toString))
      .orElse(json.as[Float].map(_.toString))
      .map(EditionName(_))
  }

  /** The filename suffix that is used to create a filename corresponding to a
    * named edition.
    */
  val editionSuffix = ".yaml"

  /** Creates an [[EditionName]] from the corresponding filename.
    *
    * Returns None if the filename does not correspond to an edition.
    */
  def fromFilename(filename: String): Option[EditionName] =
    if (filename.endsWith(editionSuffix))
      Some(EditionName(filename.stripSuffix(editionSuffix)))
    else None
}
