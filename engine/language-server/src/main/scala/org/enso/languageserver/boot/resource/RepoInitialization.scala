package org.enso.languageserver.boot.resource

import java.io.IOException
import java.nio.file.{Files, NoSuchFileException}

import akka.event.EventStream
import org.enso.languageserver.data.DirectoriesConfig
import org.enso.languageserver.event.InitializedEvent
import org.enso.searcher.sql.{SqlDatabase, SqlSuggestionsRepo, SqlVersionsRepo}
import org.slf4j.LoggerFactory

import scala.concurrent.{ExecutionContext, Future}
import scala.util.control.NonFatal
import scala.util.{Failure, Success}

/** Initialization of the Language Server repositories.
  *
  * @param directoriesConfig configuration of language server directories
  * @param eventStream akka events stream
  * @param suggestionsRepo the suggestions repo
  * @param versionsRepo the versions repo
  */
class RepoInitialization(
  directoriesConfig: DirectoriesConfig,
  eventStream: EventStream,
  suggestionsRepo: SqlSuggestionsRepo,
  versionsRepo: SqlVersionsRepo
)(implicit ec: ExecutionContext)
    extends InitializationComponent {

  private val log = LoggerFactory.getLogger(this.getClass)

  /** @inheritdoc */
  override def init(): Future[InitializationComponent.Initialized.type] =
    for {
      _ <- suggestionsRepoInit
      _ <- versionsRepoInit
    } yield InitializationComponent.Initialized

  private def suggestionsRepoInit: Future[Unit] = {
    val initAction =
      for {
        _ <- Future {
          log.info("Initializing suggestions repo.")
        }
        _ <- suggestionsRepo.init.recoverWith { case NonFatal(error) =>
          recoverInitError(error, suggestionsRepo.db)
        }
        _ <- Future {
          log.info("Initialized Suggestions repo.")
        }
      } yield ()
    initAction.onComplete {
      case Success(()) =>
        eventStream.publish(InitializedEvent.SuggestionsRepoInitialized)
      case Failure(ex) =>
        log.error(
          s"Failed to initialize SQL suggestions repo. ${ex.getMessage}"
        )
    }
    initAction
  }

  private def versionsRepoInit: Future[Unit] = {
    val initAction =
      for {
        _ <- Future {
          log.info("Initializing versions repo.")
        }
        _ <- versionsRepo.init
        _ <- Future {
          log.info("Initialized Versions repo.")
        }
      } yield ()
    initAction.onComplete {
      case Success(()) =>
        eventStream.publish(InitializedEvent.FileVersionsRepoInitialized)
      case Failure(ex) =>
        log.error(s"Failed to initialize SQL versions repo. ${ex.getMessage}")
    }
    initAction
  }

  private def recoverInitError(
    error: Throwable,
    db: SqlDatabase
  ): Future[Unit] =
    for {
      _ <- Future {
        log.warn(
          s"Failed to initialize the suggestions database " +
          s"${directoriesConfig.suggestionsDatabaseFile}. " +
          s"${error.getMessage}"
        )
      }
      _ <- Future {
        db.close()
        System.gc()
      }
      _ <- clearDatabaseFile()
      _ <- Future(db.open())
      _ <- Future {
        log.info("Retrying database initialization.")
      }
      _ <- suggestionsRepo.init
    } yield ()

  private def clearDatabaseFile(retries: Int = 0): Future[Unit] = {
    Future {
      log.info(s"Clear database file. Attempt #${retries + 1}.")
      Files.delete(directoriesConfig.suggestionsDatabaseFile.toPath)
    }.recoverWith {
      case _: NoSuchFileException =>
        log.warn(
          s"Failed to delete the database file. Attempt #${retries + 1}. " +
          s"File does not exist " +
          s"${directoriesConfig.suggestionsDatabaseFile}"
        )
        Future.successful(())
      case error: IOException =>
        log.error(
          s"Failed to delete the database file. Attempt #${retries + 1}. " +
          s"${error.getMessage}"
        )
        if (retries < RepoInitialization.MaxRetries) {
          Thread.sleep(1000)
          clearDatabaseFile(retries + 1)
        } else {
          Future.failed(error)
        }
    }
  }

}

object RepoInitialization {

  val MaxRetries = 3
}
