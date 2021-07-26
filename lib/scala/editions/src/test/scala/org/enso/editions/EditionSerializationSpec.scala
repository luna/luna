package org.enso.editions

import nl.gn0s1s.bump.SemVer
import org.scalatest.Inside
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

import scala.util.{Failure, Success}

class EditionSerializationSpec extends AnyWordSpec with Matchers with Inside {
  "EditionSerialization" should {
    "parse an edition that just derives another one" in {
      val parsed = EditionSerialization.parseYamlString(
        """extends: 2021.4
          |""".stripMargin
      )
      inside(parsed) { case Success(edition) =>
        edition.parent should contain("2021.4")
        edition.repositories should be(empty)
        edition.libraries should be(empty)
        edition.engineVersion should be(empty)
      }
    }

    "parse a standalone edition" in {
      val parsed = EditionSerialization.parseYamlString(
        """engine-version: 1.2.3-SNAPSHOT
          |repositories:
          | - name: example
          |   url: https://example.com/
          | - name: foo
          |   url: http://127.0.0.1:8080/root
          |libraries:
          | - name: Foo.Local
          |   repository: local
          | - name: Bar.Baz
          |   repository: example
          |   version: 0.0.0
          | - name: A.B
          |   repository: bar
          |   version: 1.0.1
          |""".stripMargin
      )
      inside(parsed) { case Success(edition) =>
        edition.parent should be(empty)
        edition.repositories.size shouldEqual 2
        edition.repositories("example").name shouldEqual "example"
        edition
          .repositories("example")
          .url shouldEqual "https://example.com/"
        edition.repositories("foo").name shouldEqual "foo"
        edition
          .repositories("foo")
          .url shouldEqual "http://127.0.0.1:8080/root"

        edition.libraries.values should contain theSameElementsAs Seq(
          Editions.Raw.LocalLibrary(LibraryName("Foo", "Local")),
          Editions.Raw.PublishedLibrary(
            LibraryName("Bar", "Baz"),
            SemVer(0, 0, 0),
            "example"
          ),
          Editions.Raw
            .PublishedLibrary(LibraryName("A", "B"), SemVer(1, 0, 1), "bar")
        )
        edition.engineVersion should contain(SemVer(1, 2, 3, Some("SNAPSHOT")))
      }
    }

    "not parse an edition that does not define the engine version nor attempt to derive it" in {
      val parsed = EditionSerialization.parseYamlString(
        """libraries:
          |- name: foo
          |  repository: local
          |""".stripMargin
      )
      inside(parsed) { case Failure(exception) =>
        exception.getMessage should include(
          "The edition must specify at least one of"
        )
      }
    }

    "not allow invalid version combinations for libraries" in {
      val parsed = EditionSerialization.parseYamlString(
        """extends: foo
          |libraries:
          |- name: bar.baz
          |  repository: local
          |  version: 1.2.3-SHOULD-NOT-BE-HERE
          |""".stripMargin
      )
      inside(parsed) { case Failure(exception) =>
        exception.getMessage should include("Version field must not be set")
      }

      val parsed2 = EditionSerialization.parseYamlString(
        """extends: foo
          |libraries:
          |- name: bar.baz
          |  repository: something
          |""".stripMargin
      )
      inside(parsed2) { case Failure(exception) =>
        exception.getMessage should include("Version field is mandatory")
      }
    }
  }
}
