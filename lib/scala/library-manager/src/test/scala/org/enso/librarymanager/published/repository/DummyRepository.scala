package org.enso.librarymanager.published.repository

import nl.gn0s1s.bump.SemVer
import org.enso.cli.OS
import org.enso.distribution.FileSystem
import org.enso.editions.Editions.RawEdition
import org.enso.editions.{Editions, LibraryName}
import org.enso.pkg.{Package, PackageManager}
import org.enso.testkit.process.WrappedProcess

import java.io.File
import java.nio.file.{Files, Path}
import scala.util.control.NonFatal

/** A helper class managing a library repository for testing purposes. */
abstract class DummyRepository {

  /** A library used for testing.
    *
    * @param libraryName name of the library
    * @param version version of the library
    * @param mainContent contents of the `Main.enso` file
    */
  case class DummyLibrary(
    libraryName: LibraryName,
    version: SemVer,
    mainContent: String
  )

  /** Name of the repository, as it will be indicated in the generated edition.
    */
  def repoName: String = "test_repo"

  /** Sequence of libraries to create in the repository and include in the
    * edition.
    */
  def libraries: Seq[DummyLibrary]

  /** Creates a directory structure for the repository at the given root and
    * populates it with [[libraries]].
    */
  def createRepository(root: Path): Unit = {
    for (lib <- libraries) {
      val libraryRoot = root
        .resolve("libraries")
        .resolve(lib.libraryName.namespace)
        .resolve(lib.libraryName.name)
        .resolve(lib.version.toString)
      Files.createDirectories(libraryRoot)
      createLibraryProject(libraryRoot, lib)
      val files = Seq(
        ArchiveWriter.TextFile("src/Main.enso", lib.mainContent)
      )
      ArchiveWriter.writeTarArchive(libraryRoot.resolve("main.tgz"), files)
      createManifest(libraryRoot)
    }
  }

  private def createLibraryProject(
    path: Path,
    lib: DummyLibrary
  ): Package[File] = {
    val pkg = PackageManager.Default.create(
      path.toFile,
      name      = lib.libraryName.name,
      namespace = lib.libraryName.namespace,
      version   = lib.version.toString()
    )
    pkg.save().get
    pkg
  }

  private def createManifest(path: Path): Unit = {
    FileSystem.writeTextFile(
      path.resolve("manifest.yaml"),
      s"""archives:
         | - main.tgz
         |""".stripMargin
    )
  }

  /** Creates an edition which contains libraries defined in this repository.
    *
    * @param repoUrl the URL where the repository is going to be accessible; the
    *                URL should include the `libraries` prefix
    */
  def createEdition(repoUrl: String): RawEdition = {
    Editions.Raw.Edition(
      parent       = Some(buildinfo.Info.currentEdition),
      repositories = Map(repoName -> Editions.Repository(repoName, repoUrl)),
      libraries = Map.from(libraries.map { lib =>
        lib.libraryName -> Editions.Raw
          .PublishedLibrary(lib.libraryName, lib.version, repoName)
      })
    )
  }

  private def commandPrefix: Seq[String] =
    if (OS.isWindows) Seq("cmd.exe", "/c") else Seq.empty

  private def npmCommand: String  = if (OS.isWindows) "npm.cmd" else "npm"
  private def nodeCommand: String = if (OS.isWindows) "node.exe" else "node"

  /** Starts a server for the library repository.
    *
    * @param port port to listen on
    * @param root root of the library repository, the same as the argument to
    *             [[createRepository]]
    */
  def startServer(port: Int, root: Path): WrappedProcess = {
    val serverDirectory =
      Path.of("tools/simple-library-server").toAbsolutePath.normalize

    val preinstallCommand = commandPrefix ++ Seq(npmCommand, "install")
    val preinstallExitCode = new ProcessBuilder()
      .command(preinstallCommand: _*)
      .directory(serverDirectory.toFile)
      .inheritIO()
      .start()
      .waitFor()

    if (preinstallExitCode != 0)
      throw new RuntimeException(
        s"Failed to preinstall the Library Repository Server dependencies: " +
        s"npm exited with code $preinstallCommand."
      )

    val command = commandPrefix ++ Seq(
      nodeCommand,
      "main.js",
      "--port",
      port.toString,
      "--root",
      root.toAbsolutePath.normalize.toString
    )
    val rawProcess = (new ProcessBuilder)
      .command(command: _*)
      .directory(serverDirectory.toFile)
      .start()
    val process = new WrappedProcess(command, rawProcess)
    try {
      process.printIO()
      process.waitForMessage(
        "Serving the repository",
        timeoutSeconds = 15,
        process.StdOut
      )
    } catch {
      case NonFatal(e) =>
        process.kill()
        throw e
    }
    process
  }
}
