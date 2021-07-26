package org.enso.runtimeversionmanager.config

import io.circe.Json
import nl.gn0s1s.bump.SemVer
import org.enso.distribution.DistributionManager
import org.enso.runtimeversionmanager.test.FakeEnvironment
import org.enso.testkit.WithTemporaryDirectory
import org.scalatest.OptionValues
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

class GlobalConfigurationManagerSpec
    extends AnyWordSpec
    with Matchers
    with WithTemporaryDirectory
    with FakeEnvironment
    with OptionValues {
  def makeConfigManager(): GlobalConfigurationManager = {
    val env                 = fakeInstalledEnvironment()
    val distributionManager = new DistributionManager(env)
    new GlobalConfigurationManager(null, distributionManager) {
      override def defaultVersion: SemVer = SemVer(0, 0, 0)
    }
  }

  "GlobalConfigurationManager" should {
    "allow to edit and remove (even unknown) keys" in {
      val configurationManager = makeConfigManager()
      val value                = Json.fromInt(42)
      configurationManager.updateConfigRaw("unknown-key", value)
      configurationManager.getConfig.original
        .apply("unknown-key")
        .value shouldEqual value
      configurationManager.removeFromConfig("unknown-key")
      configurationManager.getConfig.original.apply(
        "unknown-key"
      ) should not be defined
    }

    "not allow saving an invalid config" in {
      val configurationManager = makeConfigManager()
      intercept[InvalidConfigError] {
        configurationManager.updateConfigRaw(
          "default.enso-version",
          Json.fromString("invalid-version")
        )
      }
    }
  }

}
