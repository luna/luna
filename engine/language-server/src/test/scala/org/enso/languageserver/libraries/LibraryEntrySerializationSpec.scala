package org.enso.languageserver.libraries

import io.circe.syntax._
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

class LibraryEntrySerializationSpec extends AnyWordSpec with Matchers {
  "LibraryEntry" should {
    "serialize and deserialize to the same thing" in {
      val entry1 = LibraryEntry("Foo", "Bar", LibraryEntry.LocalLibraryVersion)
      entry1.asJson.as[LibraryEntry] shouldEqual Right(entry1)

      val entry2 = LibraryEntry(
        "Foo",
        "Bar",
        LibraryEntry.PublishedLibraryVersion("1.2.3", "https://example.com/")
      )
      entry2.asJson.as[LibraryEntry] shouldEqual Right(entry2)
    }
  }
}
