package org.enso.librarymanager.dependencies

import org.enso.editions.{Editions, LibraryName, LibraryVersion}
import org.enso.librarymanager.LibraryResolver
import org.enso.librarymanager.local.LocalLibraryProvider
import org.enso.librarymanager.published.PublishedLibraryProvider
import org.enso.librarymanager.published.repository.LibraryManifest
import org.enso.librarymanager.published.repository.RepositoryHelper.RepositoryMethods
import org.enso.yaml.YamlHelper

import java.nio.file.Files
import scala.util.Try

class DependencyResolver(
  localLibraryProvider: LocalLibraryProvider,
  publishedLibraryProvider: PublishedLibraryProvider,
  edition: Editions.ResolvedEdition,
  preferLocalLibraries: Boolean,
  versionResolver: LibraryResolver
) {
  def findDependencies(libraryName: LibraryName): Try[Set[Dependency]] =
    findDependencies(libraryName, Set.empty)

  private def findDependencies(
    libraryName: LibraryName,
    parents: Set[LibraryName]
  ): Try[Set[Dependency]] = Try {
    if (parents.contains(libraryName)) {
      throw new IllegalStateException(
        "An illegal dependency cycle has been detected."
      )
    }

    val version = versionResolver
      .resolveLibraryVersion(libraryName, edition, preferLocalLibraries)
      .getOrElse {
        throw new RuntimeException("TODO errors")
      }

    version match {
      case LibraryVersion.Local =>
        // TODO [RW] can we skip deps of local? we cannot effectively find them
        //  without parsing the whole library which is costly
        Set(
          Dependency(
            libraryName,
            version,
            localLibraryProvider.findLibrary(libraryName).isDefined
          )
        )

      case publishedVersion @ LibraryVersion.Published(semver, _) =>
        val itself = Dependency(
          libraryName,
          version,
          publishedLibraryProvider
            .findCachedLibrary(libraryName, semver)
            .isDefined
        )

        val manifest = getManifest(libraryName, publishedVersion)

        Set(itself) | manifest.dependencies.toSet.flatMap(
          findDependencies(_, parents + libraryName).get
        )
    }
  }

  private def getManifest(
    libraryName: LibraryName,
    version: LibraryVersion.Published
  ): LibraryManifest = {
    val cachedManifest = publishedLibraryProvider
      .findCachedLibrary(libraryName, version.version)
      .flatMap { libraryPath =>
        val manifestPath = libraryPath.resolve(LibraryManifest.filename)
        if (Files.exists(manifestPath)) Some(manifestPath) else None
      }
      .flatMap { manifestPath =>
        YamlHelper.load[LibraryManifest](manifestPath).toOption
      }
    cachedManifest.getOrElse {
      version.repository
        .accessLibrary(libraryName, version.version)
        .downloadManifest()
        .force()
    }
  }
}
