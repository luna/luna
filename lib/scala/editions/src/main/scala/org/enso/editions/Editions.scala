package org.enso.editions

import nl.gn0s1s.bump.SemVer

import scala.util.Try

/** Defines the general edition structure.
  *
  * We split the data type into two categories: Raw and Resolved editions.
  *
  * Raw editions are directly parsed from a YAML structure and can be serialized
  * back to it. They are just an object model of the underlying YAML format.
  *
  * The raw edition may reference a parent edition or repositories by name.
  * The [[EditionResolver]] takes care of resolving a Raw edition into a
  * Resolved instance by loading and parsing any of its parents and replacing
  * the repository by-name references by actual references to the repository
  * instances.
  */
trait Editions {

  /** The type of nested editions.
    *
    * Raw editions will refer to parents by their name and the Resolved edition
    * will contain a reference to an actual object.
    */
  type NestedEditionType

  /** The type of repository reference in listed libraries.
    *
    * Libraries in Raw editions will refer to repositories by their name and the
    * Resolved variant will contain a reference to the actual repository object
    * loaded from the edition or one of its parents.
    */
  type LibraryRepositoryType

  /** The library description included in the edition. */
  sealed trait Library {

    /** The qualified name of the library.
      *
      * It should consist of a prefix followed by a dot an the library name, for
      * example `Prefix.Library_Name`.
      */
    def name: LibraryName
  }

  /** Represents a local library. */
  case class LocalLibrary(override val name: LibraryName) extends Library

  /** Represents a specific version of the library that is published in a
    * repository.
    *
    * @param name the qualified name of the library
    * @param version the exact version of the library that should be used
    * @param repository the recommended repository to download the library from,
    *                   if it is not yet cached
    */
  case class PublishedLibrary(
    override val name: LibraryName,
    version: SemVer,
    repository: LibraryRepositoryType
  ) extends Library

  /** An Edition describing the library resolution configuration.
    *
    * @param parent a parent edition (if applicable)
    * @param engineVersion an engine version; it should be defined if the
    *                      edition wants to override the setting from the parent
    *                      or if it has no parents
    * @param repositories a mapping of repositories directly defined in the
    *                     edition (does not include ones defined in the parents)
    * @param libraries a mapping of libraries directly defined in the edition
    *                  (does not include ones defined in the parents)
    */
  case class Edition(
    parent: Option[NestedEditionType]              = None,
    engineVersion: Option[SemVer]                  = None,
    repositories: Map[String, Editions.Repository] = Map.empty,
    libraries: Map[LibraryName, Library]           = Map.empty
  ) {
    if (parent.isEmpty && engineVersion.isEmpty)
      throw new IllegalArgumentException(
        "The edition must specify the engine version or a parent edition " +
        "that will imply it."
      )
  }
}

object Editions {

  /** Represents a repository that provides libraries. */
  case class Repository(name: String, url: String)

  object Repository {

    /** A helper function that creates a Repository instance from a raw string
      * URL.
      */
    def make(name: String, url: String): Try[Repository] = Try {
      Repository(name, url)
    }
  }

  /** Implements the Raw editions that can be directly parsed from a YAML
    * configuration.
    *
    * All references are typed as String, because this is what is directly read
    * from the configuration, without any postprocessing.
    */
  object Raw extends Editions {
    override type NestedEditionType     = String
    override type LibraryRepositoryType = String
  }

  /** Implements the Resolved editions which are obtained by analyzing the Raw
    * edition and loading any of its parents.
    */
  object Resolved extends Editions {
    override type NestedEditionType     = this.Edition
    override type LibraryRepositoryType = Repository
  }

  /** An alias for Raw editions. */
  type RawEdition = Raw.Edition

  /** An alias for Resolved editions. */
  type ResolvedEdition = Resolved.Edition

  /** Syntax helpers for resolved edition. */
  implicit class ResolvedEditionOps(edition: ResolvedEdition) {

    /** Resolves the engine version that is implied by this resolved edition. It
      * is either the version override directly specified in the edition or the
      * version implied by its parent.
      */
    def getEngineVersion: SemVer = edition.engineVersion.getOrElse {
      val parent = edition.parent.getOrElse {
        throw new IllegalStateException(
          "Internal error: Resolved edition does not imply an engine version."
        )
      }
      parent.getEngineVersion
    }

    /** Returns a mapping of all libraries defined in the edition, including any
      * libraries defined in parent editions (also taking into account the
      * overrides).
      */
    def getAllDefinedLibraries: Map[LibraryName, LibraryVersion] = {
      val parent =
        edition.parent.map(_.getAllDefinedLibraries).getOrElse(Map.empty)
      edition.libraries.foldLeft(parent) { case (map, (name, lib)) =>
        val version = lib match {
          case Resolved.LocalLibrary(_) => LibraryVersion.Local
          case Resolved.PublishedLibrary(_, version, repository) =>
            LibraryVersion.Published(version, repository)
        }
        map.updated(name, version)
      }
    }
  }

  /** Syntax helpers for a raw edition. */
  implicit class RawEditionOps(edition: RawEdition) {

    /** Checks if the edition configuration consists only of an `extends` field
      * and no other overrides.
      *
      * If this is the case, `package.yaml` can use the shortened edition field
      * syntax.
      */
    def isDerivingWithoutOverrides: Boolean =
      edition.parent.isDefined &&
      edition.engineVersion.isEmpty &&
      edition.libraries.isEmpty &&
      edition.repositories.isEmpty
  }
}
