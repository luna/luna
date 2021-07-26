package org.enso.librarymanager

import nl.gn0s1s.bump.SemVer
import org.enso.editions.Editions.Repository
import org.enso.editions.{Editions, LibraryName, LibraryVersion}
import org.enso.librarymanager.local.LocalLibraryProvider
import org.enso.testkit.EitherValue
import org.scalatest.Inside
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

import java.nio.file.Path

class LibraryResolverSpec
    extends AnyWordSpec
    with Matchers
    with EitherValue
    with Inside {
  "LibraryResolver" should {
    val mainRepo = Repository("main", "https://example.com/main")
    val parentEdition = Editions.Resolved.Edition(
      parent        = None,
      engineVersion = Some(SemVer(0, 0, 0)),
      repositories  = Map("main" -> mainRepo),
      libraries = Map(
        LibraryName("Standard", "Base") -> Editions.Resolved
          .PublishedLibrary(
            LibraryName("Standard", "Base"),
            SemVer(4, 5, 6),
            mainRepo
          )
      )
    )
    val customRepo = Repository("custom", "https://example.com/custom")
    val currentEdition = Editions.Resolved.Edition(
      parent        = Some(parentEdition),
      engineVersion = None,
      repositories  = Map("custom" -> customRepo),
      libraries = Map(
        LibraryName("Foo", "Main") -> Editions.Resolved
          .PublishedLibrary(
            LibraryName("Foo", "Main"),
            SemVer(1, 0, 0),
            mainRepo
          ),
        LibraryName("Foo", "My") -> Editions.Resolved
          .PublishedLibrary(
            LibraryName("Foo", "My"),
            SemVer(2, 0, 0),
            customRepo
          ),
        LibraryName("Foo", "Local") -> Editions.Resolved.LocalLibrary(
          LibraryName("Foo", "Local")
        )
      )
    )

    case class FakeLocalLibraryProvider(fixtures: Map[LibraryName, Path])
        extends LocalLibraryProvider {
      override def findLibrary(libraryName: LibraryName): Option[Path] =
        fixtures.get(libraryName)
    }

    val localLibraries = Map(
      LibraryName("Foo", "My")        -> Path.of("./Foo/My"),
      LibraryName("Foo", "Local")     -> Path.of("./Foo/Local"),
      LibraryName("Standard", "Base") -> Path.of("./Standard/Base")
    )

    val resolver = LibraryResolver(FakeLocalLibraryProvider(localLibraries))

    "correctly resolve libraries based on the edition" in {
      resolver
        .resolveLibraryVersion(
          libraryName          = LibraryName("Standard", "Base"),
          edition              = currentEdition,
          preferLocalLibraries = false
        )
        .rightValue shouldEqual
      LibraryVersion.Published(SemVer(4, 5, 6), mainRepo)

      resolver
        .resolveLibraryVersion(
          libraryName          = LibraryName("Foo", "Main"),
          edition              = currentEdition,
          preferLocalLibraries = false
        )
        .rightValue shouldEqual
      LibraryVersion.Published(SemVer(1, 0, 0), mainRepo)

      resolver
        .resolveLibraryVersion(
          libraryName          = LibraryName("Foo", "My"),
          edition              = currentEdition,
          preferLocalLibraries = false
        )
        .rightValue shouldEqual
      LibraryVersion.Published(SemVer(2, 0, 0), customRepo)

      resolver
        .resolveLibraryVersion(
          libraryName          = LibraryName("Foo", "Local"),
          edition              = currentEdition,
          preferLocalLibraries = false
        )
        .rightValue shouldEqual
      LibraryVersion.Local
    }

    "correctly handle preference of local libraries" in {
      resolver
        .resolveLibraryVersion(
          libraryName          = LibraryName("Standard", "Base"),
          edition              = currentEdition,
          preferLocalLibraries = true
        )
        .rightValue shouldEqual
      LibraryVersion.Local

      resolver
        .resolveLibraryVersion(
          libraryName          = LibraryName("Foo", "Main"),
          edition              = currentEdition,
          preferLocalLibraries = true
        )
        .rightValue shouldEqual
      LibraryVersion.Published(SemVer(1, 0, 0), mainRepo)

      resolver
        .resolveLibraryVersion(
          libraryName          = LibraryName("Foo", "My"),
          edition              = currentEdition,
          preferLocalLibraries = true
        )
        .rightValue shouldEqual
      LibraryVersion.Local

      resolver
        .resolveLibraryVersion(
          libraryName          = LibraryName("Foo", "Local"),
          edition              = currentEdition,
          preferLocalLibraries = true
        )
        .rightValue shouldEqual
      LibraryVersion.Local
    }

    "not fall back to a local library if it was not defined as such " +
    "explicitly nor `prefer-local-libraries` is set" in {
      val result = resolver.resolveLibraryVersion(
        libraryName          = LibraryName("Foo", "Local"),
        edition              = parentEdition,
        preferLocalLibraries = false
      )
      inside(result) { case Left(error) =>
        error.getMessage should include("not defined")
      }
    }
  }
}
