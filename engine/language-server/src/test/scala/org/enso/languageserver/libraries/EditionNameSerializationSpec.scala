package org.enso.languageserver.libraries

import io.circe.syntax._
import org.enso.languageserver.libraries.EditionReference.{
  CurrentProjectEdition,
  NamedEdition
}
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

class EditionNameSerializationSpec extends AnyWordSpec with Matchers {
  "EditionName" should {
    "serialize and deserialize to the same thing" in {
      val edition1: EditionReference = CurrentProjectEdition
      edition1.asJson.as[EditionReference] shouldEqual Right(edition1)

      val edition2: EditionReference = NamedEdition("Foo-Bar")
      edition2.asJson.as[EditionReference] shouldEqual Right(edition2)
    }
  }
}
