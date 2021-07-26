package org.enso.runtimeversionmanager.releases.testing

import org.enso.cli.OS
import org.enso.distribution.FileSystem

import java.nio.file.Path
import scala.sys.process.Process

/** A helper that allows to create a package from a directory.
  *
  * It should only be used in test mode.
  */
object TestArchivePackager {

  /** Creates a packaged archive resembling the ones created by release
    * workflow.
    */
  def packArchive(source: Path, destination: Path): Unit = {
    if (buildinfo.Info.isRelease)
      throw new IllegalStateException(
        "Internal TestArchivePackager called in release mode."
      )

    val directoryName = source.getFileName.toString
    if (directoryName.endsWith(".tar.gz") && OS.isUNIX)
      packTarGz(source, destination)
    else if (directoryName.endsWith(".zip") && OS.isWindows)
      packZip(source, destination)
    else {
      throw new IllegalArgumentException(
        s"Fake-archive format $directoryName is not supported on " +
        s"${OS.operatingSystem}."
      )
    }
  }

  private def packTarGz(source: Path, destination: Path): Unit = {
    val files = FileSystem.listDirectory(source)
    val exitCode = Process(
      Seq(
        "tar",
        "-czf",
        destination.toAbsolutePath.toString
      ) ++ files.map(_.getFileName.toString),
      source.toFile
    ).!
    if (exitCode != 0) {
      throw new RuntimeException(
        s"tar failed. Cannot create fake-archive for $source"
      )
    }
  }

  private def packZip(source: Path, destination: Path): Unit = {
    val files = FileSystem.listDirectory(source)
    val exitCode = Process(
      Seq(
        "powershell",
        "Compress-Archive",
        "-Path",
        files.map(_.getFileName.toString).mkString(","),
        "-DestinationPath",
        destination.toAbsolutePath.toString
      ),
      source.toFile
    ).!
    if (exitCode != 0) {
      throw new RuntimeException(
        s"tar failed. Cannot create fake-archive for $source"
      )
    }
  }
}
