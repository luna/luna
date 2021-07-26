package org.enso.languageserver.libraries

import org.enso.editions.provider.EditionProvider
import org.enso.editions.{DefaultEdition, EditionResolver, Editions}
import org.enso.languageserver.libraries.EditionReference.NamedEdition
import org.enso.pkg.PackageManager

import java.io.File
import scala.util.Try

/** Resolves [[EditionReference]] to a raw or resolved edition. */
class EditionReferenceResolver(
  projectRoot: File,
  editionProvider: EditionProvider,
  editionResolver: EditionResolver
) {
  private lazy val projectPackage =
    PackageManager.Default.loadPackage(projectRoot).get

  /** Loads the raw edition corresponding to the given [[EditionReference]]. */
  def resolveReference(
    editionReference: EditionReference
  ): Try[Editions.RawEdition] = editionReference match {
    case EditionReference.NamedEdition(editionName) =>
      editionProvider.findEditionForName(editionName)
    case EditionReference.CurrentProjectEdition =>
      Try {
        projectPackage.config.edition.getOrElse {
          // TODO [RW] default edition from config (#1864)
          DefaultEdition.getDefaultEdition
        }
      }
  }

  /** Resolves all edition dependencies of an edition identified by
    * [[EditionReference]].
    */
  def resolveEdition(
    editionReference: EditionReference
  ): Try[Editions.ResolvedEdition] = for {
    raw      <- resolveReference(editionReference)
    resolved <- editionResolver.resolve(raw).toTry
  } yield resolved

  /** Resolves all edition dependencies of an edition identified by its name. */
  def resolveEdition(name: String): Try[Editions.ResolvedEdition] =
    resolveEdition(NamedEdition(name))
}
