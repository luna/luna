package org.enso.searcher.sql

import java.util

import org.enso.searcher.VersionsRepo
import slick.jdbc.SQLiteProfile.api._
import slick.jdbc.meta.MTable

import scala.concurrent.{ExecutionContext, Future}

final class SqlVersionsRepo(db: SqlDatabase)(implicit ec: ExecutionContext)
    extends VersionsRepo[Future] {

  /** Initialize the repo. */
  override def init: Future[Unit] =
    db.run(initQuery.transactionally)

  /** @inheritdoc */
  override def getVersion(module: String): Future[Option[Array[Byte]]] =
    db.run(getVersionQuery(module))

  /** @inheritdoc */
  override def setVersion(
    module: String,
    digest: Array[Byte]
  ): Future[Option[Array[Byte]]] =
    db.run(setVersionQuery(module, digest))

  /** @inheritdoc */
  override def updateVersion(
    module: String,
    digest: Array[Byte]
  ): Future[Boolean] =
    db.run(updateVersionQuery(module, digest))

  /** @inheritdoc */
  override def updateVersions(
    versions: Seq[(String, Array[Byte])]
  ): Future[Unit] =
    db.run(updateVersionsQuery(versions).transactionally)

  /** @inheritdoc */
  override def remove(module: String): Future[Unit] =
    db.run(removeQuery(module))

  /** @inheritdoc */
  override def remove(modules: Seq[String]): Future[Unit] =
    db.run(removeModulesQuery(modules))

  /** @inheritdoc */
  override def clean: Future[Unit] =
    db.run(cleanQuery)

  /** Close the database. */
  def close(): Unit =
    db.close()

  /** The query to initialize the repo. */
  private def initQuery: DBIO[Unit] = {
    val table = ModuleVersions
    for {
      tables <- MTable.getTables(table.shaped.value.tableName)
      _      <- if (tables.isEmpty) table.schema.create else DBIO.successful(())
    } yield ()
  }

  /** The query to clean the repo. */
  private def cleanQuery: DBIO[Unit] =
    ModuleVersions.delete >> DBIO.successful(())

  /** The query to get the version digest of the file.
    *
    * @param module the module name
    * @return the version digest
    */
  private def getVersionQuery(module: String): DBIO[Option[Array[Byte]]] = {
    val query = for {
      row <- ModuleVersions
      if row.module === module
    } yield row.digest
    query.result.headOption
  }

  /** The query to set the version digest of the file.
    *
    * @param module the module name
    * @param version the version digest
    * @return the previously recorded vile version
    */
  private def setVersionQuery(
    module: String,
    version: Array[Byte]
  ): DBIO[Option[Array[Byte]]] = {
    val upsertQuery =
      ModuleVersions.insertOrUpdate(ModuleVersionRow(module, version))
    for {
      version <- getVersionQuery(module)
      _       <- upsertQuery
    } yield version
  }

  /** The query to update the version if it differs from the recorded version.
    *
    * @param module the module name
    * @param version the version digest
    * @return `true` if the version has been updated
    */
  private def updateVersionQuery(
    module: String,
    version: Array[Byte]
  ): DBIO[Boolean] =
    for {
      moduleVersion <- getVersionQuery(module)
      versionsEquals = moduleVersion.fold(false)(compareVersions(_, version))
      _ <-
        if (!versionsEquals) setVersionQuery(module, version)
        else DBIO.successful(None)
    } yield !versionsEquals

  /** The query to update the versions in batch.
    *
    * @param versions files with corresponding digests
    */
  private def updateVersionsQuery(
    versions: Seq[(String, Array[Byte])]
  ): DBIO[Unit] =
    if (versions.nonEmpty) {
      def upsertQuery(module: String, version: Array[Byte]) =
        ModuleVersions.insertOrUpdate(ModuleVersionRow(module, version))
      DBIO.sequence(versions.map(Function.tupled(upsertQuery))) >>
      DBIO.successful(())
    } else {
      DBIO.successful(())
    }

  /** The query to remove the version record.
    *
    * @param module the module name
    */
  private def removeQuery(module: String): DBIO[Unit] = {
    val query = for {
      row <- ModuleVersions
      if row.module === module
    } yield row
    query.delete >> DBIO.successful(())
  }

  /** The query to remove multiple module versions.
    *
    * @param modules the list of module names
    */
  private def removeModulesQuery(modules: Seq[String]): DBIO[Unit] = {
    val deleteQuery = ModuleVersions
      .filter(_.module.inSet(modules))
      .delete
    for {
      _ <- deleteQuery
    } yield ()
  }

  private def compareVersions(v1: Array[Byte], v2: Array[Byte]): Boolean =
    util.Arrays.equals(v1, v2)

}
