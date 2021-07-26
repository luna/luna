package org.enso.launcher

import org.enso.testkit.WithTemporaryDirectory

import java.nio.file.{Files, Path}
import org.scalatest.OptionValues

import scala.jdk.CollectionConverters._
import scala.sys.process._

class PluginManagerSpec
    extends NativeTest
    with OptionValues
    with WithTemporaryDirectory {

  def makePluginCode(name: String): Seq[String] =
    Seq(s"""echo Plugin $name.""")

  def writePlugin(
    path: Path,
    name: String,
    prefixed: Boolean = true
  ): Unit = {
    val shebang = if (isWindows) "@echo off" else "#!/bin/sh"
    val content =
      Seq(shebang) ++ makePluginCode(name)
    val prefix    = if (prefixed) "enso-" else ""
    val extension = if (isWindows) ".bat" else ""
    val filename  = prefix + name + extension
    val filePath  = path.resolve(filename).toAbsolutePath
    Files.write(filePath, content.asJava)
    if (!isWindows) {
      if (Seq("chmod", "+x", filePath.toString).! != 0) {
        println(
          "Error making the plugin writeable, the PluginManagerSpec may fail."
        )
      }
    }
  }

  "PluginManager" should {
    "allow plugins to be listed in help" in {
      val path = getTestDirectory.toAbsolutePath
      writePlugin(path, "plugin1")
      writePlugin(path, "plugin2")
      writePlugin(path, "plugin3", prefixed = false)

      val run = runLauncherWithPath(Seq("help"), path.toString)
      run should returnSuccess
      run.stdout should include("Plugin plugin1.")
      run.stdout should include("Plugin plugin2.")
      run.stdout should not include "plugin3"
    }

    "allow to run a plugin" in {
      val path = getTestDirectory.toAbsolutePath
      writePlugin(path, "plugin1")

      val run = runLauncherWithPath(Seq("plugin1"), path.toString)
      run should returnSuccess
      run.stdout.trim shouldEqual "Plugin plugin1."
    }

    "suggest similar plugin name on typo" in {
      val path = getTestDirectory.toAbsolutePath
      writePlugin(path, "plugin1")
      val run = runLauncherWithPath(Seq("plugin2"), path.toString)
      run.exitCode should not equal 0
      run.stdout should include("plugin1")
    }
  }
}
