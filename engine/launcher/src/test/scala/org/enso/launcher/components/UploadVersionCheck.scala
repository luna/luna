package org.enso.launcher.components

import nl.gn0s1s.bump.SemVer
import org.enso.launcher.Constants
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

class UploadVersionCheck extends AnyWordSpec with Matchers {
  "Constants.uploadIntroducedVersion" should {
    "correctly compare with nearby versions" in {
      assert(
        SemVer("0.2.17-SNAPSHOT.2021-07-23").get >=
        Constants.uploadIntroducedVersion
      )

      assert(
        SemVer("0.2.17").get >
        Constants.uploadIntroducedVersion
      )

      assert(
        SemVer("0.2.16").get <
        Constants.uploadIntroducedVersion
      )
    }
  }
}
