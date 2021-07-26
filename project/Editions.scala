import sbt._

object Editions {
  val standardLibraries: Seq[String] = Seq(
    "Standard.Base",
    "Standard.Test",
    "Standard.Table",
    "Standard.Database",
    "Standard.Image",
    "Standard.Geo",
    "Standard.Visualization",
    "Standard.Examples"
  )

  /** Generates a base edition file for the engine release that contains the
    * Standard library and is associated with the current Enso version.
    */
  def writeEditionConfig(
    ensoVersion: String,
    editionName: String,
    libraryVersion: String,
    log: Logger
  ): Unit = {
    val editions = file("distribution") / "editions"
    IO.createDirectory(editions)
    val edition = editions / (editionName + ".yaml")

    for (file <- IO.listFiles(editions)) {
      if (file.getName != edition.getName) {
        IO.delete(file)
        log.warn(s"Removed spurious file in editions directory: $file")
      }
    }

    if (!edition.exists()) {
      val librariesConfigs = standardLibraries.map { libName =>
        s"""  - name: $libName
           |    repository: main
           |    version: $libraryVersion""".stripMargin
      }

      val editionConfig =
        s"""engine-version: $ensoVersion
           |repositories:
           |  - name: main
           |    url: n/a # Library repository is still a work in progress.
           |libraries:
           |${librariesConfigs.mkString("\n")}
           |""".stripMargin
      IO.write(edition, editionConfig)
      log.info(s"Written edition config to $edition")
    } else {
      log.debug(
        "The edition file did already exist, not regenerating. " +
        "Clean to force a rebuild."
      )
    }
  }
}
