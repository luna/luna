package org.enso.languageserver.libraries

import akka.actor.{Actor, Props}
import com.typesafe.scalalogging.LazyLogging
import org.enso.distribution.{DistributionManager, FileSystem}
import org.enso.editions.{Editions, LibraryName}
import org.enso.languageserver.libraries.LocalLibraryManagerProtocol._
import org.enso.librarymanager.local.LocalLibraryProvider
import org.enso.pkg.PackageManager

import java.io.File
import java.nio.file.Files
import scala.util.{Failure, Success, Try}

/** An Actor that manages local libraries. */
class LocalLibraryManager(
  currentProjectRoot: File,
  distributionManager: DistributionManager
) extends Actor
    with LazyLogging {
  override def receive: Receive = { case request: Request =>
    request match {
      case GetMetadata(_) =>
        logger.warn(
          "Getting local library metadata is currently not implemented."
        )
        sender() ! Success(GetMetadataResponse(None, None))
      case SetMetadata(_, _, _) =>
        logger.error(
          "Setting local library metadata is currently not implemented."
        )
        sender() ! Failure(new NotImplementedError())
      case ListLocalLibraries =>
        sender() ! listLocalLibraries()
      case Create(libraryName, authors, maintainers, license) =>
        sender() ! createLibrary(libraryName, authors, maintainers, license)
      case Publish(_, _, _) =>
        logger.error("Publishing libraries is currently not implemented.")
        sender() ! Failure(new NotImplementedError())
    }
  }

  /** Creates a new local library project.
    *
    * The project is created in the first directory of the local library search
    * path that is writable.
    */
  private def createLibrary(
    libraryName: LibraryName,
    authors: Seq[String],
    maintainers: Seq[String],
    license: String
  ): Try[Unit] = Try {
    // TODO [RW] modify protocol to be able to create Contact instances
    val _ = (authors, maintainers)

    // TODO [RW] make the exceptions more relevant
    val possibleRoots = LazyList
      .from(distributionManager.paths.localLibrariesSearchPaths)
      .filter { path =>
        Try { if (Files.notExists(path)) Files.createDirectories(path) }
        Files.isWritable(path)
      }
    val librariesRoot = possibleRoots.headOption.getOrElse {
      throw new RuntimeException(
        "Cannot find a writable directory on local library path."
      )
    }

    val libraryPath =
      LocalLibraryProvider.resolveLibraryPath(librariesRoot, libraryName)
    if (Files.exists(libraryPath)) {
      throw new RuntimeException("Local library already exists")
    }

    PackageManager.Default.create(
      libraryPath.toFile,
      name      = libraryName.name,
      namespace = libraryName.namespace,
      edition   = findCurrentProjectEdition(),
      license   = license
    )
  }

  /** Lists all local libraries. */
  private def listLocalLibraries(): Try[ListLocalLibrariesResponse] = for {
    libraryNames <- findLocalLibraries()
    libraryEntries = libraryNames.distinct.map { name =>
      LibraryEntry(name.namespace, name.name, LibraryEntry.LocalLibraryVersion)
    }
  } yield ListLocalLibrariesResponse(libraryEntries)

  private def findLocalLibraries(): Try[Seq[LibraryName]] = Try {
    for {
      searchPathRoot <- distributionManager.paths.localLibrariesSearchPaths
      namespaceDir <- FileSystem
        .listDirectory(searchPathRoot)
        .filter(Files.isDirectory(_))
      nameDir <- FileSystem
        .listDirectory(namespaceDir)
        .filter(Files.isDirectory(_))
      namespace = namespaceDir.getFileName.toString
      name      = nameDir.getFileName.toString
    } yield LibraryName(namespace, name)
  }

  /** Finds the edition associated with the current project, if specified in its
    * config.
    */
  private def findCurrentProjectEdition(): Option[Editions.RawEdition] = {
    val pkg = PackageManager.Default.loadPackage(currentProjectRoot).get
    pkg.config.edition
  }
}

object LocalLibraryManager {
  def props(
    currentProjectRoot: File,
    distributionManager: DistributionManager
  ): Props = Props(
    new LocalLibraryManager(currentProjectRoot, distributionManager)
  )
}
