package org.enso.editions

import nl.gn0s1s.bump.SemVer
import org.enso.editions.EditionResolutionError.EditionResolutionCycle
import org.enso.editions.Editions.Repository
import org.enso.editions.provider.EditionProvider
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.scalatest.{Inside, OptionValues}

import scala.util.{Failure, Success, Try}

class EditionResolverSpec
    extends AnyWordSpec
    with Matchers
    with Inside
    with OptionValues {
  object FakeEditionProvider extends EditionProvider {
    val mainRepo = Repository("main", "https://example.com/main")
    val editions: Map[String, Editions.RawEdition] = Map(
      "2021.0" -> Editions.Raw.Edition(
        parent        = None,
        engineVersion = Some(SemVer(1, 2, 3)),
        repositories = Map(
          "main" -> mainRepo
        ),
        libraries = Map(
          LibraryName("Standard", "Base") -> Editions.Raw
            .PublishedLibrary(
              LibraryName("Standard", "Base"),
              SemVer(1, 2, 3),
              "main"
            )
        )
      ),
      "cycleA" -> Editions.Raw.Edition(
        parent        = Some("cycleB"),
        engineVersion = Some(SemVer(1, 2, 3)),
        repositories  = Map(),
        libraries     = Map()
      ),
      "cycleB" -> Editions.Raw.Edition(
        parent        = Some("cycleA"),
        engineVersion = Some(SemVer(1, 2, 3)),
        repositories  = Map(),
        libraries     = Map()
      )
    )

    override def findEditionForName(name: String): Try[Editions.Raw.Edition] =
      editions
        .get(name)
        .map(Success(_))
        .getOrElse(
          Failure(new RuntimeException(s"Could not load edition `$name`."))
        )
  }

  val resolver = EditionResolver(FakeEditionProvider)

  "EditionResolver" should {
    "resolve a simple edition" in {
      val repo = Repository("foo", "http://example.com")
      val edition = Editions.Raw.Edition(
        parent        = None,
        engineVersion = Some(SemVer(1, 2, 3)),
        repositories  = Map("foo" -> repo),
        libraries = Map(
          LibraryName("bar", "baz") -> Editions.Raw.LocalLibrary(
            LibraryName("bar", "baz")
          ),
          LibraryName("foo", "bar") -> Editions.Raw
            .PublishedLibrary(LibraryName("foo", "bar"), SemVer(1, 2, 3), "foo")
        )
      )

      inside(resolver.resolve(edition)) { case Right(resolved) =>
        resolved.engineVersion shouldEqual edition.engineVersion
        resolved.parent should be(empty)
        resolved.repositories shouldEqual edition.repositories
        resolved.libraries should have size 2
        resolved.libraries(
          LibraryName("bar", "baz")
        ) shouldEqual Editions.Resolved
          .LocalLibrary(LibraryName("bar", "baz"))
        resolved.libraries(
          LibraryName("foo", "bar")
        ) shouldEqual Editions.Resolved
          .PublishedLibrary(LibraryName("foo", "bar"), SemVer(1, 2, 3), repo)
      }
    }

    "resolve a nested edition" in {
      val edition = Editions.Raw.Edition(
        parent        = Some("2021.0"),
        engineVersion = Some(SemVer(1, 2, 3)),
        repositories  = Map(),
        libraries = Map(
          LibraryName("bar", "baz") -> Editions.Raw.LocalLibrary(
            LibraryName("bar", "baz")
          ),
          LibraryName("foo", "bar") -> Editions.Raw
            .PublishedLibrary(
              LibraryName("foo", "bar"),
              SemVer(1, 2, 3),
              "main"
            )
        )
      )

      inside(resolver.resolve(edition)) { case Right(resolved) =>
        resolved.parent should be(defined)
        resolved.libraries should have size 2
        resolved.libraries(
          LibraryName("bar", "baz")
        ) shouldEqual Editions.Resolved
          .LocalLibrary(LibraryName("bar", "baz"))
        resolved.libraries(
          LibraryName("foo", "bar")
        ) shouldEqual Editions.Resolved
          .PublishedLibrary(
            LibraryName("foo", "bar"),
            SemVer(1, 2, 3),
            FakeEditionProvider.mainRepo
          )
      }
    }

    "correctly handle repository shadowing when resolving libraries" in {
      val localRepo = Repository("main", "http://example.com/local")

      localRepo should not equal FakeEditionProvider.mainRepo

      val edition = Editions.Raw.Edition(
        parent        = Some("2021.0"),
        engineVersion = None,
        repositories  = Map("main" -> localRepo),
        libraries = Map(
          LibraryName("foo", "bar") -> Editions.Raw
            .PublishedLibrary(
              LibraryName("foo", "bar"),
              SemVer(1, 2, 3),
              "main"
            )
        )
      )

      inside(resolver.resolve(edition)) { case Right(resolved) =>
        resolved.parent should be(defined)
        resolved.libraries should have size 1
        resolved.libraries(LibraryName("foo", "bar")) shouldEqual
        Editions.Resolved.PublishedLibrary(
          LibraryName("foo", "bar"),
          SemVer(1, 2, 3),
          localRepo
        )

        resolved.parent.value.libraries(
          LibraryName("Standard", "Base")
        ) shouldEqual
        Editions.Resolved.PublishedLibrary(
          LibraryName("Standard", "Base"),
          SemVer(1, 2, 3),
          FakeEditionProvider.mainRepo
        )
      }
    }

    "avoid cycles in the resolution" in {
      val edition = Editions.Raw.Edition(
        parent        = Some("cycleA"),
        engineVersion = Some(SemVer(1, 2, 3)),
        repositories  = Map(),
        libraries     = Map()
      )

      val failure = resolver.resolve(edition)
      inside(failure) { case Left(EditionResolutionCycle(editions)) =>
        editions shouldEqual Seq("cycleA", "cycleB", "cycleA")
      }
    }
  }
}
