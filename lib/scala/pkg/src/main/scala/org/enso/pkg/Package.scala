package org.enso.pkg

import cats.Show
import org.enso.editions.{Editions, LibraryName}
import org.enso.filesystem.FileSystem
import org.enso.pkg.validation.NameValidation
import java.io.{File, InputStream, OutputStream}
import java.net.URI

import scala.jdk.CollectionConverters._
import scala.util.{Failure, Try, Using}

object CouldNotCreateDirectory extends Exception

/** Represents a source file with known qualified name.
  *
  * @param qualifiedName the qualified name of this file
  * @param file the location of this file
  */
case class SourceFile[F](qualifiedName: QualifiedName, file: F)

/** Represents an Enso package stored on the hard drive.
  *
  * @param root the root directory of this package
  * @param config the metadata contained in the package configuration
  *  @param fileSystem the file system access module
  */
case class Package[F](
  root: F,
  config: Config,
  implicit val fileSystem: FileSystem[F]
) {
  import FileSystem.Syntax

  val sourceDir   = root.getChild(Package.sourceDirName)
  val configFile  = root.getChild(Package.configFileName)
  val thumbFile   = root.getChild(Package.thumbFileName)
  val polyglotDir = root.getChild(Package.polyglotExtensionsDirName)

  /** Sets the package name.
    *
    * @param newName the new package name
    * @return a package with the updated name
    */
  def setPackageName(newName: String): Package[F] =
    this.copy(config = config.copy(name = newName))

  /** Stores the package metadata on the hard drive. If the package does not exist,
    * creates the required directory structure.
    */
  def save(): Try[Unit] =
    for {
      _ <- Try {
        if (!root.exists) createDirectories()
        if (!sourceDir.exists) createSourceDir()
      }
      _ <- saveConfig()
    } yield ()

  /** Creates the package directory structure.
    */
  def createDirectories(): Unit = {
    val created = Try(root.createDirectories()).map(_ => true).getOrElse(false)
    if (!created) throw CouldNotCreateDirectory
    createSourceDir()
  }

  /** Changes the package name.
    *
    * @param newName the new package name
    * @return The package object with changed name. The old package is not
    *         valid anymore.
    */
  def rename(newName: String): Package[F] = updateConfig(_.copy(name = newName))

  /** Updates the package config.
    *
    * The changes are automatically saved to the filesystem.
    *
    * @param update the function that modifies the config
    * @return The package object with changed config. The old package is not
    *         valid anymore.
    */
  def updateConfig(update: Config => Config): Package[F] = {
    val newPkg = copy(config = update(config))
    newPkg.saveConfig()
    newPkg
  }

  /** Creates the sources directory.
    */
  def createSourceDir(): Unit = {
    Try(sourceDir.createDirectories()).getOrElse(throw CouldNotCreateDirectory)
  }

  /** Saves the config metadata into the package configuration file.
    */
  def saveConfig(): Try[Unit] =
    Using(configFile.newBufferedWriter) { writer =>
      writer.write(config.toYaml)
    }

  /** Gets the location of the package's Main file.
    *
    * @return the location of the Main file.
    */
  def mainFile: F = {
    sourceDir.getChild(Package.mainFileName)
  }

  /** Checks if the package has a thumbnail file.
    *
    * @return `true` if the thumbnail file exists, `false` otherwise.
    */
  def hasThumb: Boolean = thumbFile.exists

  /** Returns the name of this package.
    * @return the name of this package.
    */
  def name: String = config.name

  def namespace: String = config.namespace

  /** A [[LibraryName]] associated with the package. */
  def libraryName: LibraryName = LibraryName(config.namespace, config.name)

  /** Parses a file path into a qualified module name belonging to this
    * package.
    *
    * @param file the source file path to translate into a qualified name.
    * @return a qualified name of the input source path.
    */
  def moduleNameForFile(file: F): QualifiedName = {
    val segments                 = sourceDir.relativize(file).getSegments.asScala.toList
    val dirSegments              = segments.take(segments.length - 1)
    val fileNameWithoutExtension = file.getName.takeWhile(_ != '.')
    QualifiedName(namespace :: name :: dirSegments, fileNameWithoutExtension)
  }

  /** Lists the source files in this package.
    *
    * @return the list of all source files in this package, together with their qualified names.
    */
  def listSources: List[SourceFile[F]] = {
    val sources = sourceDir.walk
      .filter(_.isRegularFile)
      .iterator
      .asScala
      .toList
    sources.map { path => SourceFile(moduleNameForFile(path), path) }
  }

  /** Lists contents of the polyglot extensions directory for a given language.
    *
    * @param languageName the language to list extenstions for
    * @return a list of files and directories contained in the relevant
    *         polyglot extensions directory.
    */
  def listPolyglotExtensions(languageName: String): List[F] = {
    val dir = polyglotDir.getChild(languageName)
    if (!dir.isDirectory) return List()
    dir.list
      .iterator()
      .asScala
      .toList
  }
}

/** A class responsible for creating and parsing package structures.
  * @param fileSystem the file system to use.
  * @tparam F the type of paths used by `fileSystem`.
  */
class PackageManager[F](implicit val fileSystem: FileSystem[F]) {
  import FileSystem.Syntax

  /** Creates a new Package in a given location and with config file.
    *
    * @param root the root location of the package.
    * @param config the config for the new package.
    * @return a package object representing the newly created package.
    */
  def create(
    root: F,
    config: Config,
    template: Template
  ): Package[F] = {
    val pkg = Package(root, config, fileSystem)
    pkg.save()
    copyResources(pkg, template)
    pkg
  }

  /** Creates a new Package in a given location and with given name. Leaves all the other config fields blank.
    *
    * @param root  the root location of the package.
    * @param name the name for the new package.
    * @param version version of the newly-created package.
    * @param template the template for the new package.
    * @param edition the edition to use for the project; if not specified, it
    *                will not specify any, meaning that the current default one
    *                will be used
    * @return a package object representing the newly created package.
    */
  def create(
    root: F,
    name: String,
    namespace: String                    = "local",
    version: String                      = "0.0.1",
    template: Template                   = Template.Default,
    edition: Option[Editions.RawEdition] = None,
    authors: List[Contact]               = List(),
    maintainers: List[Contact]           = List(),
    license: String                      = ""
  ): Package[F] = {
    val config = Config(
      name                 = NameValidation.normalizeName(name),
      namespace            = namespace,
      version              = version,
      license              = license,
      authors              = authors,
      edition              = edition,
      preferLocalLibraries = true,
      maintainers          = maintainers
    )
    create(root, config, template)
  }

  /** Tries to parse package structure from a given root location.
    *
    * @param root the root location to get package info from.
    * @return `Some(pkg)` if the location represents a package, `None`
    *        otherwise.
    */
  def fromDirectory(root: F): Option[Package[F]] =
    loadPackage(root).toOption

  /** Loads the package structure at the given root location and reports any
    * errors.
    *
    * @param root the root location to get package info from.
    * @return `Success(pkg)` if the location represents a valid package, and
    *        `Failure` otherwise. If the package file or its parent directory do
    *        not exist, a `PackageNotFound` exception is returned. Otherwise,
    *        the exception that made it not possible to load the package is
    *        returned.
    */
  def loadPackage(root: F): Try[Package[F]] = {
    val result =
      if (!root.exists) Failure(PackageManager.PackageNotFound())
      else {
        def readConfig(file: F): Try[String] =
          if (file.exists)
            Using(file.newBufferedReader) { reader =>
              reader.lines().iterator().asScala.mkString("\n")
            }
          else Failure(PackageManager.PackageNotFound())

        val configFile = root.getChild(Package.configFileName)
        for {
          resultStr <- readConfig(configFile)
          result    <- Config.fromYaml(resultStr)
        } yield Package(root, result, fileSystem)
      }
    result.recoverWith {
      case packageLoadingException: PackageManager.PackageLoadingException =>
        Failure(packageLoadingException)
      case decodingError: io.circe.Error =>
        val errorMessage =
          implicitly[Show[io.circe.Error]].show(decodingError)
        Failure(
          PackageManager.PackageLoadingFailure(
            s"Cannot decode the package config: $errorMessage",
            decodingError
          )
        )
      case otherError =>
        Failure(
          PackageManager.PackageLoadingFailure(
            s"Cannot load the package: $otherError",
            otherError
          )
        )
    }
  }

  /** Tries to parse package structure from a given root location or creates a new package if it fails.
    *
    * @param root the package root location.
    * @return the package object for the package found or created in the root location.
    */
  def getOrCreate(root: F): Package[F] = {
    val existing = fromDirectory(root)
    existing.getOrElse(create(root, generateName(root)))
  }

  /** Generates a name for the package, by normalizing the last segment of its root path.
    *
    * @param file the root location of the package.
    * @return the generated package name.
    */
  def generateName(file: F): String = {
    val dirname = file.getName
    NameValidation.normalizeName(dirname)
  }

  /** Copy the template resources to the package.
    *
    * @param template the template to copy the resources from
    * @param pkg the package to copy the resources to
    */
  private def copyResources(pkg: Package[F], template: Template): Unit =
    template match {
      case Template.Default =>
        val mainEnsoPath = new URI(s"/default/src/${Package.mainFileName}")

        copyResource(
          mainEnsoPath,
          pkg.sourceDir.getChild(Package.mainFileName)
        )

      case Template.Example =>
        val helloTxtPath = new URI("/example/hello.txt")
        val mainEnsoPath = new URI(s"/example/src/${Package.mainFileName}")

        copyResource(
          helloTxtPath,
          pkg.root.getChild("hello.txt")
        )
        copyResource(
          mainEnsoPath,
          pkg.sourceDir.getChild(Package.mainFileName)
        )
    }

  /** Copy the resource to provided resource.
    *
    * @param from the source
    * @param to the destination
    */
  private def copyResource(from: URI, to: F): Unit = {
    val fromStream = getClass.getResourceAsStream(from.toString)
    val toStream   = to.newOutputStream
    try PackageManager.copyStream(fromStream, toStream)
    finally {
      fromStream.close()
      toStream.close()
    }
  }

}

object PackageManager {
  val Default = new PackageManager[File]()(FileSystem.Default)

  /** A general exception indicating that a package cannot be loaded. */
  class PackageLoadingException(message: String, cause: Throwable)
      extends RuntimeException(message, cause) {

    /** @inheritdoc */
    override def toString: String = message
  }

  /** The error indicating that the requested package does not exist. */
  case class PackageNotFound()
      extends PackageLoadingException(s"The package file does not exist.", null)

  /** The error indicating that the package exists, but cannot be loaded. */
  case class PackageLoadingFailure(message: String, cause: Throwable)
      extends PackageLoadingException(message, cause)

  /** The error indicating that the project name is invalid. */
  case class InvalidNameException(message: String)
      extends RuntimeException(message)

  private def copyStream(in: InputStream, out: OutputStream): Unit = {
    val buffer = Array.ofDim[Byte](4096)
    var length = in.read(buffer)
    while (length != -1) {
      out.write(buffer, 0, length)
      length = in.read(buffer)
    }
  }

}

/** A companion object for static methods on the [[Package]] class.
  */
object Package {
  val fileExtension             = "enso"
  val configFileName            = "package.yaml"
  val sourceDirName             = "src"
  val polyglotExtensionsDirName = "polyglot"
  val mainFileName              = "Main.enso"
  val thumbFileName             = "thumb.png"
}
