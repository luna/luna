package org.enso.librarymanager.published.repository

import nl.gn0s1s.bump.SemVer
import org.enso.editions.LibraryName

/** A simple [[DummyRepository]] containing a single library for testing
  * downloads.
  */
class ExampleRepository extends DummyRepository {

  /** The library provided by this repository. */
  val testLib: DummyLibrary = DummyLibrary(
    LibraryName("Foo", "Bar"),
    SemVer(1, 0, 0),
    """baz = 42
      |
      |quux = "foobar"
      |""".stripMargin
  )

  /** @inheritdoc */
  override def libraries: Seq[DummyLibrary] = Seq(testLib)
}
