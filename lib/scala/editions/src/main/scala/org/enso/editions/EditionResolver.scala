package org.enso.editions

import cats.implicits._
import org.enso.editions.EditionResolutionError.{
  EditionResolutionCycle,
  LibraryReferencesUndefinedRepository
}
import org.enso.editions.Editions.{RawEdition, ResolvedEdition}
import org.enso.editions.provider.EditionProvider

import scala.annotation.tailrec

/** A helper class that allows to resolve a [[RawEdition]] into a
  * [[ResolvedEdition]] by loading its parents and resolving the repository
  * references.
  *
  * @param provider an [[EditionProvider]] that is used for loading the
  *                 referenced parent editions
  */
case class EditionResolver(provider: EditionProvider) {

  /** Runs the edition resolution.
    *
    * @param edition the raw edition to resolve
    * @return either a resolution error or the resolved edition
    */
  def resolve(
    edition: RawEdition
  ): Either[EditionResolutionError, ResolvedEdition] =
    resolveEdition(edition, Nil)

  /** A helper method that resolves an edition and keeps a list of already
    * visited edition names to avoid cycles.
    */
  private def resolveEdition(
    edition: RawEdition,
    visitedEditions: List[String]
  ): Either[EditionResolutionError, ResolvedEdition] =
    for {
      parent <- resolveParent(edition.parent, visitedEditions)
      libraries <- resolveLibraries(
        edition.libraries,
        edition.repositories,
        parent
      )
    } yield Editions.Resolved.Edition(
      parent        = parent,
      engineVersion = edition.engineVersion,
      repositories  = edition.repositories,
      libraries     = libraries
    )

  /** A helper method for resolving libraries.
    *
    * @param libraries the raw mapping of libraries
    * @param currentRepositories the mapping of repositories defined in the
    *                            current edition
    * @param parent an optional reference to an (already resolved) parent
    *               edition, which is used if the library references a
    *               repository that was not defined in `currentRepositories`
    * @return either an error indicating a reference to an unknown repository or
    *         a mapping of resolved libraries
    */
  private def resolveLibraries(
    libraries: Map[LibraryName, Editions.Raw.Library],
    currentRepositories: Map[String, Editions.Repository],
    parent: Option[ResolvedEdition]
  ): Either[
    LibraryReferencesUndefinedRepository,
    Map[LibraryName, Editions.Resolved.Library]
  ] = {
    val resolvedPairs: Either[
      LibraryReferencesUndefinedRepository,
      List[(LibraryName, Editions.Resolved.Library)]
    ] =
      libraries.toList.traverse { case (name, library) =>
        val resolved = resolveLibrary(library, currentRepositories, parent)
        resolved.map((name, _))
      }

    resolvedPairs.map(Map.from)
  }

  /** A helper method to resolve a single library.
    *
    * @param library the library to resolve
    * @param currentRepositories the mapping of repositories defined in the
    *                            current edition
    * @param parent an optional reference to an (already resolved) parent
    *               edition, which is used if the library references a
    *               repository that was not defined in `currentRepositories`
    * @return either an error or the resolved library
    */
  @tailrec
  private def resolveLibrary(
    library: Editions.Raw.Library,
    currentRepositories: Map[String, Editions.Repository],
    parent: Option[ResolvedEdition]
  ): Either[
    LibraryReferencesUndefinedRepository,
    Editions.Resolved.Library
  ] = library match {
    case Editions.Raw.LocalLibrary(qualifiedName) =>
      Right(Editions.Resolved.LocalLibrary(qualifiedName))
    case Editions.Raw.PublishedLibrary(
          qualifiedName,
          version,
          repositoryName
        ) =>
      (currentRepositories.get(repositoryName), parent) match {
        case (Some(repository), _) =>
          Right(
            Editions.Resolved
              .PublishedLibrary(qualifiedName, version, repository)
          )
        case (None, Some(parentEdition)) =>
          resolveLibrary(
            library,
            parentEdition.repositories,
            parentEdition.parent
          )
        case (None, None) =>
          Left(
            LibraryReferencesUndefinedRepository(
              libraryName    = library.name,
              repositoryName = repositoryName
            )
          )
      }
  }

  /** A helper function that takes an optional parent name and list of already
    *  seen editions and tries to load the parent (if present), but avoiding
    *  cycles (returning an error if a cycle was encountered).
    */
  private def resolveParent(
    parent: Option[String],
    visitedEditions: List[String]
  ): Either[EditionResolutionError, Option[ResolvedEdition]] = parent match {
    case Some(parentName) =>
      if (visitedEditions.contains(parentName)) {
        val cycle = parentName :: visitedEditions
        Left(EditionResolutionCycle(cycle.reverse))
      } else
        for {
          rawParent <- provider
            .findEditionForName(parentName)
            .toEither
            .left
            .map(EditionResolutionError.CannotLoadEdition(parentName, _))
          res <- resolveEdition(rawParent, parentName :: visitedEditions)
        } yield Some(res)
    case None => Right(None)
  }
}
