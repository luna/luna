package org.enso.runtimeversionmanager.components

import org.enso.cli.OS
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

class GraalVMComponentConfigurationSpec extends AnyWordSpec with Matchers {

  "RuntimeComponentConfiguration" should {

    "return required components" in {
      val conf     = new GraalVMComponentConfiguration
      val required = Seq(GraalVMComponent.python, GraalVMComponent.R)

      conf.getRequiredComponents(
        GraalVMVersion("21.0.0.2", "11"),
        OS.Linux
      ) should contain theSameElementsAs required

      conf.getRequiredComponents(
        GraalVMVersion("21.0.0.2", "11"),
        OS.MacOS
      ) should contain theSameElementsAs required

      conf.getRequiredComponents(
        GraalVMVersion("21.0.0.2", "11"),
        OS.Windows
      ) should contain theSameElementsAs Seq()

      conf.getRequiredComponents(
        GraalVMVersion("22.0.0.0", "11"),
        OS.Linux
      ) should contain theSameElementsAs required

      conf.getRequiredComponents(
        GraalVMVersion("20.0.0.0", "11"),
        OS.Linux
      ) should contain theSameElementsAs Seq()

    }
  }
}
