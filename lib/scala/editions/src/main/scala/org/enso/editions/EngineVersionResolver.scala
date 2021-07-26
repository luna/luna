package org.enso.editions

import nl.gn0s1s.bump.SemVer
import org.enso.editions.Editions.RawEdition
import org.enso.editions.provider.EditionProvider

/** A helper class which resolves the engine version that is entailed by the
  * edition configuration.
  *
  * It requires an [[EditionProvider]] instance, because the edition may not
  * specify the engine version directly, but it can just rely on what is
  * specified in a parent edition, so it needs a way to load the parent
  * editions.
  */
case class EngineVersionResolver(editionProvider: EditionProvider) {
  private val editionResolver = EditionResolver(editionProvider)

  /** Returns the [[EnsoVersion]] that is entailed by the provided edition (or a
    * resolution error if the edition cannot be resolved).
    */
  def resolveEnsoVersion(
    edition: RawEdition
  ): Either[EditionResolutionError, SemVer] = {
    for {
      edition <- editionResolver.resolve(edition)
    } yield edition.getEngineVersion
  }
}
