package org.enso.editions.provider

import org.enso.editions.{EditionName, EditionSerialization, Editions}

import java.io.FileNotFoundException
import java.nio.file.{Files, Path}
import scala.annotation.tailrec
import scala.collection.Factory
import scala.jdk.StreamConverters.StreamHasToScala
import scala.util.{Failure, Success, Try, Using}

/** An implementation of [[EditionProvider]] that looks for the edition files in
  *  a list of filesystem paths.
  */
class FileSystemEditionProvider(searchPaths: List[Path])
    extends EditionProvider {

  /** @inheritdoc */
  override def findEditionForName(name: String): Try[Editions.Raw.Edition] = {
    val result = findEdition(name, searchPaths)
    result match {
      case Left(EditionNotFound) =>
        Failure(new FileNotFoundException(s"Could not find edition `$name`."))
      case Left(EditionReadError(error)) => Failure(error)
      case Right(value)                  => Success(value)
    }
  }

  @tailrec
  private def findEdition(
    name: String,
    paths: List[Path]
  ): Either[EditionLoadingError, Editions.Raw.Edition] = paths match {
    case head :: tail =>
      val headResult = loadEdition(name, head)
      headResult match {
        case Left(EditionNotFound) =>
          findEdition(name, tail)
        case _ => headResult
      }
    case Nil => Left(EditionNotFound)
  }

  sealed private trait EditionLoadingError
  private case object EditionNotFound extends EditionLoadingError
  private case class EditionReadError(error: Throwable)
      extends EditionLoadingError

  private def loadEdition(
    name: String,
    path: Path
  ): Either[EditionLoadingError, Editions.Raw.Edition] = {
    val fileName    = EditionName(name).toFileName
    val editionPath = path.resolve(fileName)
    if (Files.exists(editionPath)) {
      EditionSerialization
        .loadEdition(editionPath)
        .toEither
        .left
        .map(EditionReadError)
    } else Left(EditionNotFound)
  }

  /** Finds all editions available on the [[searchPaths]]. */
  def findAvailableEditions(): Seq[String] =
    searchPaths.flatMap(findEditionsAt).distinct

  private def findEditionName(path: Path): Option[String] =
    EditionName.fromFilename(path.getFileName.toString).map(_.name)

  private def findEditionsAt(path: Path): Seq[String] =
    listDir(path).filter(Files.isRegularFile(_)).flatMap(findEditionName)

  private def listDir(dir: Path): Seq[Path] =
    if (Files.exists(dir))
      Using(Files.list(dir))(_.toScala(Factory.arrayFactory).toSeq).get
    else Seq()
}
