package src.main.scala.licenses.report

import java.nio.file.{Files, Path}

import sbt._
import src.main.scala.licenses.{
  AttachedFile,
  AttachmentStatus,
  CopyrightMention,
  DistributionDescription,
  ReviewedSummary
}

import scala.annotation.tailrec

/** Defines writing the notices package for distribution.
  */
object PackageNotices {

  /** Creates the notices package based on the `summary` in `destination`.
    *
    * The `destination` directory is cleaned before creating the new notices.
    *
    * It copies licences, files and gathers copyright notices that were set to
    * be included in the review and generates a summary `NOTICE` file.
    */
  def create(
    description: DistributionDescription,
    summary: ReviewedSummary,
    destination: File
  ): Unit = {
    val ReviewedSummary(dependencies, noticeHeader, additionalFiles) = summary
    IO.delete(destination)
    IO.createDirectory(destination)
    if (IO.listFiles(destination).nonEmpty) {
      throw new RuntimeException(
        s"Could not clean ${destination}, cannnot continue creating the " +
        s"package."
      )
    }

    val artifactName = description.artifactName
    val mainNotice   = new StringBuilder
    mainNotice.append(noticeHeader)

    val licensesRoot      = destination / "licenses"
    val processedLicenses = collection.mutable.Set[Path]()

    writeFiles(destination, additionalFiles)

    for (dependency <- dependencies.sortBy(_.information.packageName)) {
      val name        = dependency.information.moduleInfo.name
      val licenseName = dependency.information.license.name
      mainNotice.append(
        s"\n\n'$name', licensed under the $licenseName, " +
        s"is distributed with the $artifactName.\n"
      )

      dependency.licenseReview match {
        case LicenseReview.NotReviewed =>
        case LicenseReview.Default(path, _) =>
          val name = path.getFileName.toString
          if (!processedLicenses.contains(path)) {
            val destination = licensesRoot / name
            IO.copyFile(path.toFile, destination)
            if (!destination.exists()) {
              throw new RuntimeException(s"Failed to copy the license $path")
            }
            processedLicenses.add(path)
          }
          mainNotice.append(
            s"The license file can be found at `licenses/$name`.\n"
          )
        case LicenseReview.Custom(_) =>
          mainNotice.append(
            s"The license information can be found along with the copyright notices.\n"
          )
      }

      val packageName = dependency.information.packageName
      val packageRoot = destination / packageName

      val files      = dependency.files.filter(_._2.included).map(_._1)
      val copyrights = dependency.copyrights.filter(_._2.included)

      if (files.size + copyrights.size > 0) {
        mainNotice.append(
          s"Copyright notices related to this dependency can be found in the " +
          s"directory `$packageName`.\n"
        )
        IO.createDirectory(packageRoot)
      }

      writeFiles(packageRoot, files)

      def renderCopyright(
        copyright: CopyrightMention,
        status: AttachmentStatus
      ): String =
        status match {
          case AttachmentStatus.Keep => copyright.content
          case AttachmentStatus.KeepWithContext =>
            if (copyright.contexts.size != 1) {
              throw new IllegalStateException(
                "`KeepWithContext` can only be used for copyrights that " +
                s"have exactly one context: `${copyright.content}`"
              )
            }
            copyright.contexts.head
          case AttachmentStatus.Added =>
            copyright.contexts.head
          case _ =>
            throw new IllegalStateException(
              "Only `included` copyrights should be present at this point."
            )
        }

      if (copyrights.nonEmpty) {
        val compiledCopyrights = copyrights
          .map { case (m, s) =>
            renderCopyright(m, s)
          }
          .sorted
          .mkString("\n\n")
        val freeName = findFreeName(packageRoot, gatheredNoticesFilename)
        IO.write(freeName, compiledCopyrights + "\n")
      }
    }

    IO.write(destination / "NOTICE", mainNotice.toString() + "\n")
  }

  /** Name of the generated file that contains concatenated copyright notices that were found in the
    * project.
    */
  val gatheredNoticesFilename = "NOTICES"

  /** Finds a filename that is not taken.
    *
    * First tries the `name` and adds increasing numerical suffixes if the
    * previous names were taken.
    */
  @tailrec
  def findFreeName(root: File, name: String, counter: Int = 0): File = {
    val actualName = if (counter > 0) s"$name.$counter" else name
    val file       = root / actualName
    if (Files.exists(file.toPath)) findFreeName(root, name, counter + 1)
    else file
  }

  /** Writes attached files to the given directory.
    */
  def writeFiles(root: File, files: Seq[AttachedFile]): Unit = {
    for (attachedFile <- files) {
      val file = findFreeName(root, attachedFile.path.getFileName.toString)
      if (attachedFile.content == Review.directoryMark) {
        IO.copyDirectory(attachedFile.path.toFile, file)
      } else {
        IO.write(file, attachedFile.content)
      }
    }
  }
}
