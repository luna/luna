package org.enso.filesystem

import scala.jdk.CollectionConverters._
import java.io.{
  BufferedReader,
  BufferedWriter,
  File,
  IOException,
  InputStream,
  OutputStream
}
import java.nio.file.Files
import java.nio.file.attribute.{BasicFileAttributes, FileTime}
import java.util.stream

/** A generic specification of file operations based on an abstract notion
  * of a path.
  *
  * @tparam F the path type of this implementation.
  */
trait FileSystem[F] {

  /** Gets a child path of the given path.
    *
    * @param parent the parent path.
    * @param childName the child name.
    * @return a path representing the `childName` child of `parent`.
    */
  def getChild(parent: F, childName: String): F

  /** Gets a parent of the given path.
    *
    * @param path the path to process.
    * @return `path`'s parent.
    */
  def getParent(path: F): F

  /** Checks if a file specified by given path actually exists.
    *
    * @param file the path to check.
    * @return `true` if file exists, `false` otherwise.
    */
  def exists(file: F): Boolean

  /** Creates a new directory and all non-existent parent directories.
    *
    * @param file the directory to create.
    */
  @throws[IOException]
  def createDirectories(file: F): Unit

  /** Returns a relative path from `parent` to `child`
    *
    * @param parent the parent dir.
    * @param child the child dir.
    * @return a relative path from `parent` to `child`
    */
  def relativize(parent: F, child: F): F

  /** Returns a collection of all segments of the given path.
    *
    * @param file the path to get segments of.
    * @return the `file`'s segments.
    */
  def getSegments(file: F): java.lang.Iterable[String]

  /** Gets the name of the given file.
    *
    * @param file
    * @return
    */
  def getName(file: F): String

  /** Creates a new input stream for the given file.
    *
    * @param file the file to open.
    * @return an input stream for `file`.
    */
  @throws[IOException]
  def newInputStream(file: F): InputStream

  /** Creates a new output stream for the given file.
    *
    * @param file the file to open.
    * @return an output stream for `file`.
    */
  @throws[IOException]
  def newOutputStream(file: F): OutputStream

  /** Creates a new buffered writer for the given file.
    *
    * @param file the file to open.
    * @return a buffered writer for `file`.
    */
  @throws[IOException]
  def newBufferedWriter(file: F): BufferedWriter

  /** Creates a new buffered reader for the given file.
    *
    * @param file the file to open.
    * @return a buffered reader for `file`.
    */
  @throws[IOException]
  def newBufferedReader(file: F): BufferedReader

  /** Lists all entries in the given directory.
    *
    * @param file the directory to list.
    * @return a collection of all entries in `file`.
    */
  @throws[IOException]
  def list(file: F): java.util.stream.Stream[F]

  /** Lists all files in the given directory, recursively.
    *
    * @param file the directory to traverse.
    * @return all files in the `file`'s subtree.
    */
  @throws[IOException]
  def walk(file: F): java.util.stream.Stream[F]

  /** Checks if the file is a directory.
    *
    * @param file the file to check.
    * @return `true` if `file` is a directory, false otherwise.
    */
  def isDirectory(file: F): Boolean

  /** Checks if the file is a regular file.
    *
    * @param file the file to check.
    * @return `true` if `file` is regular, false otherwise.
    */
  def isRegularFile(file: F): Boolean

  /** Get creation time of the file.
    *
    * @param file the file to check.
    * @return the file creation time.
    */
  @throws[IOException]
  def getCreationTime(file: F): FileTime
}

object FileSystem {

  /** Exposes [[FileSystem]] operations through method call syntax.
    * All methods have the same semantics as the corresponding [[FileSystem]]
    * methods.
    */
  implicit class Syntax[F](file: F)(implicit fs: FileSystem[F]) {
    def getParent: F = fs.getParent(file)

    def getChild(child: String): F = fs.getChild(file, child)

    def exists: Boolean = fs.exists(file)

    def createDirectories(): Unit = fs.createDirectories(file)

    def relativize(child: F): F = fs.relativize(file, child)

    def getSegments: java.lang.Iterable[String] = fs.getSegments(file)

    def getName: String = fs.getName(file)

    def newInputStream: InputStream = fs.newInputStream(file)

    def newOutputStream: OutputStream = fs.newOutputStream(file)

    def newBufferedWriter: BufferedWriter = fs.newBufferedWriter(file)

    def newBufferedReader: BufferedReader = fs.newBufferedReader(file)

    def list: java.util.stream.Stream[F] = fs.list(file)

    def walk: java.util.stream.Stream[F] = fs.walk(file)

    def isDirectory: Boolean = fs.isDirectory(file)

    def isRegularFile: Boolean = fs.isRegularFile(file)

    def getCreationTime: FileTime = fs.getCreationTime(file)
  }

  /** A [[File]] based implementation of [[FileSystem]].
    */
  object Default extends FileSystem[File] {
    override def getChild(parent: File, childName: String): File =
      new File(parent, childName)

    override def getParent(path: File): File = path.getParentFile

    override def exists(file: File): Boolean = file.exists()

    override def createDirectories(file: File): Unit = file.mkdirs()

    override def relativize(parent: File, child: File): File =
      parent.toPath.relativize(child.toPath).toFile

    override def getSegments(file: File): java.lang.Iterable[String] =
      file.toPath.iterator().asScala.map(_.toString).toList.asJava

    override def getName(file: File): String = file.getName

    override def newInputStream(file: File): InputStream =
      Files.newInputStream(file.toPath)

    override def newOutputStream(file: File): OutputStream =
      Files.newOutputStream(file.toPath)

    override def newBufferedWriter(file: File): BufferedWriter =
      Files.newBufferedWriter(file.toPath)

    override def newBufferedReader(file: File): BufferedReader =
      Files.newBufferedReader(file.toPath)

    override def list(file: File): stream.Stream[File] =
      Files.list(file.toPath).map(_.toFile)

    override def walk(file: File): stream.Stream[File] =
      Files.walk(file.toPath).map(_.toFile)

    override def isDirectory(file: File): Boolean = file.isDirectory

    override def isRegularFile(file: File): Boolean =
      Files.isRegularFile(file.toPath)

    override def getCreationTime(file: File): FileTime =
      Files
        .readAttributes(file.toPath, classOf[BasicFileAttributes])
        .creationTime()
  }
}
