package org.enso.searcher

/** The object for accessing the database containing the module versions. */
trait VersionsRepo[F[_]] {

  /** Initialize the repo. */
  def init: F[Unit]

  /** Get the module version.
    *
    * @param module the module name
    * @return the version digest
    */
  def getVersion(module: String): F[Option[Array[Byte]]]

  /** Set the module version.
    *
    * @param module the module name
    * @param digest the version digest
    * @return previously recorded version
    */
  def setVersion(module: String, digest: Array[Byte]): F[Option[Array[Byte]]]

  /** Update the version if it differs from the recorded version.
    *
    * @param module the module name
    * @param digest the version digest
    * @return `true` if the version has been updated
    */
  def updateVersion(module: String, digest: Array[Byte]): F[Boolean]

  /** Update the versions in batch.
    *
    * @param versions files with corresponding digests
    */
  def updateVersions(versions: Seq[(String, Array[Byte])]): F[Unit]

  /** Remove the version record.
    *
    * @param module the module name
    */
  def remove(module: String): F[Unit]

  /** Remove a list of version records.
    *
    * @param modules the list of modules to remove
    */
  def remove(modules: Seq[String]): F[Unit]

  /** Clean the repo. */
  def clean: F[Unit]
}
