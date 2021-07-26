package org.enso.searcher.sql

import java.util.UUID

import org.enso.polyglot.{ExportedSymbol, Suggestion}
import org.enso.polyglot.data.Tree
import org.enso.polyglot.runtime.Runtime.Api.{
  ExportsAction,
  ExportsUpdate,
  SuggestionAction,
  SuggestionArgumentAction,
  SuggestionUpdate,
  SuggestionsDatabaseAction
}
import org.enso.searcher.data.QueryResult
import org.enso.searcher.{SuggestionEntry, SuggestionsRepo}
import slick.jdbc.SQLiteProfile.api._
import slick.jdbc.meta.MTable
import slick.relational.RelationalProfile

import scala.collection.immutable.HashMap
import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success}

/** The object for accessing the suggestions database. */
final class SqlSuggestionsRepo(val db: SqlDatabase)(implicit
  ec: ExecutionContext
) extends SuggestionsRepo[Future] {

  /** The query returning the arguments joined with the corresponding
    * suggestions.
    */
  private val joined: Query[
    (Rep[Option[ArgumentsTable]], SuggestionsTable),
    (Option[ArgumentRow], SuggestionRow),
    Seq
  ] =
    Arguments
      .joinRight(Suggestions)
      .on(_.suggestionId === _.id)

  /** Initialize the repo. */
  override def init: Future[Unit] =
    db.run(initQuery.transactionally)

  /** @inheritdoc */
  override def clean: Future[Unit] =
    db.run(cleanQuery)

  /** @inheritdoc */
  override def getAll: Future[(Long, Seq[SuggestionEntry])] =
    db.run(getAllQuery)

  /** @inheritdoc */
  override def getAllMethods(
    calls: Seq[(String, String, String)]
  ): Future[Seq[Option[Long]]] =
    db.run(getAllMethodsQuery(calls))

  override def getAllModules: Future[Seq[String]] =
    db.run(getAllModulesQuery)

  /** @inheritdoc */
  override def search(
    module: Option[String],
    selfType: Seq[String],
    returnType: Option[String],
    kinds: Option[Seq[Suggestion.Kind]],
    position: Option[Suggestion.Position]
  ): Future[(Long, Seq[Long])] =
    db.run(
      searchQuery(module, selfType, returnType, kinds, position)
    )

  /** @inheritdoc */
  override def select(id: Long): Future[Option[Suggestion]] =
    db.run(selectQuery(id))

  /** @inheritdoc */
  override def insert(suggestion: Suggestion): Future[Option[Long]] =
    db.run(insertQuery(suggestion))

  /** @inheritdoc */
  override def insertAll(
    suggestions: Seq[Suggestion]
  ): Future[(Long, Seq[Option[Long]])] =
    db.run(insertAllQuery(suggestions).transactionally)

  /** @inheritdoc */
  override def applyTree(
    tree: Tree[SuggestionUpdate]
  ): Future[Seq[QueryResult[SuggestionUpdate]]] =
    db.run(applyTreeQuery(tree).transactionally)

  /** @inheritdoc */
  override def applyActions(
    actions: Seq[SuggestionsDatabaseAction]
  ): Future[Seq[QueryResult[SuggestionsDatabaseAction]]] =
    db.run(applyActionsQuery(actions).transactionally)

  /** @inheritdoc */
  override def applyExports(
    updates: Seq[ExportsUpdate]
  ): Future[Seq[QueryResult[ExportsUpdate]]] =
    db.run(applyExportsQuery(updates).transactionally)

  /** @inheritdoc */
  override def remove(suggestion: Suggestion): Future[Option[Long]] =
    db.run(removeQuery(suggestion))

  /** @inheritdoc */
  override def removeModules(modules: Seq[String]): Future[(Long, Seq[Long])] =
    db.run(removeByModuleQuery(modules))

  /** @inheritdoc */
  override def removeAll(
    suggestions: Seq[Suggestion]
  ): Future[(Long, Seq[Option[Long]])] =
    db.run(removeAllQuery(suggestions).transactionally)

  /** @inheritdoc */
  override def update(
    suggestion: Suggestion,
    externalId: Option[Option[Suggestion.ExternalId]],
    arguments: Option[Seq[SuggestionArgumentAction]],
    returnType: Option[String],
    documentation: Option[Option[String]],
    documentationHtml: Option[Option[String]],
    scope: Option[Suggestion.Scope],
    reexport: Option[Option[String]]
  ): Future[(Long, Option[Long])] =
    db.run(
      updateQuery(
        suggestion,
        externalId,
        arguments,
        returnType,
        documentation,
        documentationHtml,
        scope,
        reexport
      )
    )

  /** @inheritdoc */
  override def updateAll(
    expressions: Seq[(Suggestion.ExternalId, String)]
  ): Future[(Long, Seq[Option[Long]])] =
    db.run(updateAllQuery(expressions).transactionally)

  /** @inheritdoc */
  override def renameProject(
    oldName: String,
    newName: String
  ): Future[
    (
      Long,
      Seq[(Long, String)],
      Seq[(Long, String)],
      Seq[(Long, String)],
      Seq[(Long, Int, String)]
    )
  ] =
    db.run(renameProjectQuery(oldName, newName).transactionally)

  /** @inheritdoc */
  override def currentVersion: Future[Long] =
    db.run(currentVersionQuery)

  /** Close the database. */
  def close(): Unit =
    db.close()

  /** Get the database schema version.
    *
    * @return the schema version of the database
    */
  def getSchemaVersion: Future[Long] =
    db.run(currentSchemaVersionQuery)

  /** Set the database schema version.
    *
    * @param version the database schema version
    * @return the schema version of the database
    */
  def setSchemaVersion(version: Long): Future[Long] =
    db.run(setSchemaVersionQuery(version))

  /** Remove the database schema version. */
  def clearSchemaVersion: Future[Unit] =
    db.run(clearSchemaVersionQuery)

  /** Insert suggestions in a batch.
    *
    * @param suggestions the list of suggestions to insert
    * @return the current database size
    */
  private[sql] def insertBatch(suggestions: Array[Suggestion]): Future[Int] =
    db.run(insertBatchQuery(suggestions).transactionally)

  /** The query to initialize the repo. */
  private def initQuery: DBIO[Unit] = {
    type RelationalTable[A] = RelationalProfile#Table[A]
    def checkVersion(version: Long) =
      if (version == SchemaVersion.CurrentVersion) {
        DBIO.successful(())
      } else {
        DBIO.failed(new InvalidSchemaVersion(version))
      }
    def createSchema(table: TableQuery[RelationalTable[_]]) =
      for {
        tables <- MTable.getTables(table.shaped.value.tableName)
        _      <- if (tables.isEmpty) table.schema.create else DBIO.successful(())
      } yield ()

    val tables: Seq[TableQuery[RelationalTable[_]]] =
      Seq(Suggestions, Arguments, SuggestionsVersion, SchemaVersion)
        .asInstanceOf[Seq[TableQuery[RelationalTable[_]]]]
    val initSchemas =
      for {
        _       <- DBIO.sequence(tables.map(createSchema))
        version <- initSchemaVersionQuery
      } yield version

    for {
      versionAttempt <- currentSchemaVersionQuery.asTry
      version        <- versionAttempt.fold(_ => initSchemas, DBIO.successful)
      _              <- checkVersion(version)
    } yield ()
  }

  /** The query to clean the repo. */
  private def cleanQuery: DBIO[Unit] = {
    for {
      _ <- Suggestions.delete
      _ <- Arguments.delete
      _ <- SuggestionsVersion.delete
    } yield ()
  }

  /** The query to get all suggestions.
    *
    * @return the current database version with the list of suggestion entries
    */
  private def getAllQuery: DBIO[(Long, Seq[SuggestionEntry])] = {
    val query = for {
      suggestions <- joined.result.map(joinedToSuggestionEntries)
      version     <- currentVersionQuery
    } yield (version, suggestions)
    query
  }

  /** The query to get the suggestions by the method call info.
    *
    * @param calls the triples containing module name, self type, method name
    * @return the list of found suggestion ids
    */
  def getAllMethodsQuery(
    calls: Seq[(String, String, String)]
  ): DBIO[Seq[Option[Long]]] =
    if (calls.isEmpty) {
      DBIO.successful(Seq())
    } else {
      val query = Suggestions
        .filter { row =>
          calls
            .map { case (module, selfType, name) =>
              row.module === module && row.selfType === selfType && row.name === name
            }
            .reduce(_ || _)
        }
        .map(row => (row.id, row.module, row.selfType, row.name))
      query.result.map { tuples =>
        val result = tuples.map { case (id, module, selfType, name) =>
          (module, selfType, name) -> id
        }.toMap
        calls.map(result.get)
      }
    }

  /** The query to get all module names.
    *
    * @return the list of distinct module names.
    */
  def getAllModulesQuery: DBIO[Seq[String]] = {
    Suggestions.map(_.module).distinct.result
  }

  /** The query to search suggestion by various parameters.
    *
    * @param module the module name search parameter
    * @param selfType the selfType search parameter, ordered by specificity
    *                 with the most specific type first
    * @param returnType the returnType search parameter
    * @param kinds the list suggestion kinds to search
    * @param position the absolute position in the text
    * @return the list of suggestion ids, ranked by specificity (as for
    *         `selfType`)
    */
  private def searchQuery(
    module: Option[String],
    selfType: Seq[String],
    returnType: Option[String],
    kinds: Option[Seq[Suggestion.Kind]],
    position: Option[Suggestion.Position]
  ): DBIO[(Long, Seq[Long])] = {
    val typeSorterMap: HashMap[String, Int] = HashMap(selfType.zipWithIndex: _*)
    val searchAction =
      if (
        module.isEmpty &&
        selfType.isEmpty &&
        returnType.isEmpty &&
        kinds.isEmpty &&
        position.isEmpty
      ) {
        DBIO.successful(Seq())
      } else {
        val query =
          searchQueryBuilder(module, selfType, returnType, kinds, position)
            .map(r => (r.id, r.selfType))
        query.result
      }
    val query = for {
      resultsWithTypes <- searchAction
      // This implementation should be revisited if it ever becomes a
      // performance bottleneck. It may be possible to encode the same logic in
      // the database query itself.
      results = resultsWithTypes
        .sortBy { case (_, ty) => typeSorterMap.getOrElse(ty, -1) }
        .map(_._1)
      version <- currentVersionQuery
    } yield (version, results)
    query
  }

  /** The query to select the suggestion by id.
    *
    * @param id the id of a suggestion
    * @return return the suggestion
    */
  private def selectQuery(id: Long): DBIO[Option[Suggestion]] = {
    val query = for {
      (argument, suggestion) <- joined
      if suggestion.id === id
    } yield (argument, suggestion)
    query.result.map(coll => joinedToSuggestions(coll).headOption)
  }

  /** The query to insert the suggestion
    *
    * @param suggestion the suggestion to insert
    * @return the id of an inserted suggestion
    */
  private def insertQuery(suggestion: Suggestion): DBIO[Option[Long]] = {
    val (suggestionRow, args) = toSuggestionRow(suggestion)
    val query = for {
      id <- Suggestions.returning(Suggestions.map(_.id)) += suggestionRow
      _ <- Arguments ++= args.zipWithIndex.map { case (argument, ix) =>
        toArgumentRow(id, ix, argument)
      }
      _ <- incrementVersionQuery
    } yield id
    query.asTry.map {
      case Failure(_)  => None
      case Success(id) => Some(id)
    }
  }

  /** The query to insert a list of suggestions
    *
    * @param suggestions the suggestions to insert
    * @return the list of inserted suggestion ids
    */
  private def insertAllQuery(
    suggestions: Seq[Suggestion]
  ): DBIO[(Long, Seq[Option[Long]])] = {
    val query = for {
      ids     <- DBIO.sequence(suggestions.map(insertQuery))
      version <- currentVersionQuery
    } yield (version, ids)
    query
  }

  /** The query to apply the suggestion updates.
    *
    * @param tree the sequence of updates
    * @return the result of applying updates with the new database version
    */
  private def applyTreeQuery(
    tree: Tree[SuggestionUpdate]
  ): DBIO[Seq[QueryResult[SuggestionUpdate]]] = {
    val queries = tree.toVector.map {
      case update @ SuggestionUpdate(suggestion, action) =>
        val query = action match {
          case SuggestionAction.Add() =>
            insertQuery(suggestion)
          case SuggestionAction.Remove() =>
            removeQuery(suggestion)
          case SuggestionAction.Modify(
                extId,
                args,
                returnType,
                doc,
                docHtml,
                scope,
                reexport
              ) =>
            updateSuggestionQuery(
              suggestion,
              extId,
              args,
              returnType,
              doc,
              docHtml,
              scope,
              reexport
            )
        }
        query.map(rs => QueryResult(rs.toSeq, update))
    }
    DBIO.sequence(queries)
  }

  /** The query to apply the sequence of actions on the database.
    *
    * @param actions the list of actions
    * @return the result of applying actions
    */
  private def applyActionsQuery(
    actions: Seq[SuggestionsDatabaseAction]
  ): DBIO[Seq[QueryResult[SuggestionsDatabaseAction]]] = {
    val removeActions = actions.map {
      case act @ SuggestionsDatabaseAction.Clean(module) =>
        for {
          ids <- removeModulesQuery(Seq(module))
        } yield QueryResult[SuggestionsDatabaseAction](ids, act)
    }
    DBIO.sequence(removeActions)
  }

  /** The query that applies the sequence of export updates.
    *
    * @param updates the list of export updates
    * @return the result of applying actions
    */
  private def applyExportsQuery(
    updates: Seq[ExportsUpdate]
  ): DBIO[Seq[QueryResult[ExportsUpdate]]] = {
    def depth(module: String): Int =
      module.count(_ == '.')

    def updateSuggestionReexport(module: String, symbol: ExportedSymbol) = {
      val moduleDepth = depth(module)
      sql"""
          update suggestions
          set reexport = $module
          where module = ${symbol.module}
            and name = ${symbol.name}
            and kind = ${SuggestionKind(symbol.kind)}
            and (
              reexport is null or
              length(reexport) - length(replace(reexport, '.', '')) > $moduleDepth
            )
          returning id
         """.as[Long]
    }

    def unsetSuggestionReexport(module: String, symbol: ExportedSymbol) =
      sql"""
          update suggestions
          set reexport = null
          where module = ${symbol.module}
            and name = ${symbol.name}
            and kind = ${SuggestionKind(symbol.kind)}
            and reexport = $module
          returning id
         """.as[Long]

    val actions = updates.flatMap { update =>
      val symbols = update.exports.symbols.toSeq
      update.action match {
        case ExportsAction.Add() =>
          symbols.map { symbol =>
            for {
              ids <- updateSuggestionReexport(update.exports.module, symbol)
            } yield QueryResult(ids, update)
          }
        case ExportsAction.Remove() =>
          symbols.map { symbol =>
            for {
              ids <- unsetSuggestionReexport(update.exports.module, symbol)
            } yield QueryResult(ids, update)
          }
      }
    }

    for {
      rs <- DBIO.sequence(actions)
      _ <-
        if (rs.flatMap(_.ids).nonEmpty) incrementVersionQuery
        else DBIO.successful(())
    } yield rs
  }

  /** The query to select the suggestion.
    *
    * @param raw the suggestion converted to the row form
    * @return the database query
    */
  private def selectSuggestionQuery(
    raw: SuggestionRow
  ): Query[SuggestionsTable, SuggestionRow, Seq] = {
    Suggestions
      .filter(_.module === raw.module)
      .filter(_.kind === raw.kind)
      .filter(_.name === raw.name)
      .filter(_.selfType === raw.selfType)
      .filter(_.scopeStartLine === raw.scopeStartLine)
      .filter(_.scopeStartOffset === raw.scopeStartOffset)
      .filter(_.scopeEndLine === raw.scopeEndLine)
      .filter(_.scopeEndOffset === raw.scopeEndOffset)
  }

  /** The query to remove the suggestion.
    *
    * @param suggestion the suggestion to remove
    * @return the id of removed suggestion
    */
  private def removeQuery(suggestion: Suggestion): DBIO[Option[Long]] = {
    val (raw, _)    = toSuggestionRow(suggestion)
    val selectQuery = selectSuggestionQuery(raw)
    val deleteQuery = for {
      rows <- selectQuery.result
      n    <- selectQuery.delete
      _    <- if (n > 0) incrementVersionQuery else DBIO.successful(())
    } yield rows.flatMap(_.id).headOption
    deleteQuery
  }

  /** The query to remove the suggestions by module name
    *
    * @param modules the module names to remove
    * @return the current database version and a list of removed suggestion ids
    */
  private def removeByModuleQuery(
    modules: Seq[String]
  ): DBIO[(Long, Seq[Long])] = {
    val deleteQuery = for {
      ids <- removeModulesQuery(modules)
      version <-
        if (ids.nonEmpty) incrementVersionQuery else currentVersionQuery
    } yield version -> ids
    deleteQuery
  }

  /** The query to remove the suggestions by module name
    *
    * @param modules the module names to remove
    * @return the list of removed suggestion ids
    */
  private def removeModulesQuery(
    modules: Seq[String]
  ): DBIO[Seq[Long]] = {
    val selectQuery = Suggestions.filter(_.module.inSet(modules))
    for {
      rows <- selectQuery.map(_.id).result
      _    <- selectQuery.delete
    } yield rows
  }

  /** The query to remove a list of suggestions.
    *
    * @param suggestions the suggestions to remove
    * @return the list of removed suggestion ids
    */
  private def removeAllQuery(
    suggestions: Seq[Suggestion]
  ): DBIO[(Long, Seq[Option[Long]])] = {
    val query = for {
      ids     <- DBIO.sequence(suggestions.map(removeQuery))
      version <- currentVersionQuery
    } yield (version, ids)
    query
  }

  /** The query to update a suggestion.
    *
    * @param externalId the external id of a suggestion
    * @param returnType the new return type
    * @return the id of updated suggestion
    */
  private def updateByExternalIdQuery(
    externalId: Suggestion.ExternalId,
    returnType: String
  ): DBIO[Option[Long]] = {
    val selectQuery = Suggestions
      .filter { row =>
        row.externalIdLeast === externalId.getLeastSignificantBits &&
        row.externalIdMost === externalId.getMostSignificantBits
      }
    for {
      id <- selectQuery.map(_.id).result.headOption
      _  <- selectQuery.map(_.returnType).update(returnType)
    } yield id
  }

  /** The query to update the suggestion.
    *
    * @param suggestion the key suggestion
    * @param externalId the external id to update
    * @param arguments the arguments to update
    * @param returnType the return type to update
    * @param documentation the documentation string to update
    * @param documentationHtml the Html documentation string to update
    * @param scope the scope to update
    */
  private def updateQuery(
    suggestion: Suggestion,
    externalId: Option[Option[Suggestion.ExternalId]],
    arguments: Option[Seq[SuggestionArgumentAction]],
    returnType: Option[String],
    documentation: Option[Option[String]],
    documentationHtml: Option[Option[String]],
    scope: Option[Suggestion.Scope],
    reexport: Option[Option[String]]
  ): DBIO[(Long, Option[Long])] =
    for {
      idOpt <- updateSuggestionQuery(
        suggestion,
        externalId,
        arguments,
        returnType,
        documentation,
        documentationHtml,
        scope,
        reexport
      )
      version <- currentVersionQuery
    } yield (version, idOpt)

  /** The query to update the suggestion.
    *
    * @param suggestion the key suggestion
    * @param externalId the external id to update
    * @param arguments the arguments to update
    * @param returnType the return type to update
    * @param documentation the documentation string to update
    * @param documentationHtml the Html documentation string to update
    * @param scope the scope to update
    */
  private def updateSuggestionQuery(
    suggestion: Suggestion,
    externalId: Option[Option[Suggestion.ExternalId]],
    arguments: Option[Seq[SuggestionArgumentAction]],
    returnType: Option[String],
    documentation: Option[Option[String]],
    documentationHtml: Option[Option[String]],
    scope: Option[Suggestion.Scope],
    reexport: Option[Option[String]]
  ): DBIO[Option[Long]] = {
    val (raw, _) = toSuggestionRow(suggestion)
    val query    = selectSuggestionQuery(raw)

    val updateQ = for {
      r1 <- DBIO.sequenceOption {
        externalId.map { extIdOpt =>
          query
            .map(r => (r.externalIdLeast, r.externalIdMost))
            .update(
              (
                extIdOpt.map(_.getLeastSignificantBits),
                extIdOpt.map(_.getMostSignificantBits)
              )
            )
        }
      }
      r2 <- DBIO.sequenceOption {
        returnType.map(tpe => query.map(_.returnType).update(tpe))
      }
      r3 <- DBIO.sequenceOption {
        documentation.map(doc => query.map(_.documentation).update(doc))
      }
      r4 <- DBIO.sequenceOption {
        documentationHtml.map(doc => query.map(_.documentationHtml).update(doc))
      }
      r5 <- DBIO.sequenceOption {
        scope.map { s =>
          query
            .map(r =>
              (
                r.scopeStartLine,
                r.scopeStartOffset,
                r.scopeEndLine,
                r.scopeEndOffset
              )
            )
            .update(
              (s.start.line, s.start.character, s.end.line, s.end.character)
            )
        }
      }
      r6 <- DBIO.sequenceOption {
        arguments.map { args =>
          def updateArgs(suggestionId: Long): DBIO[Seq[Int]] =
            DBIO.sequence(
              args.map(updateSuggestionArgumentQuery(suggestionId, _))
            )
          for {
            idOpt <- query.map(_.id).result.headOption
            r     <- DBIO.sequenceOption(idOpt.map(updateArgs))
          } yield r.map(_.sum)
        }
      }
      r7 <- DBIO.sequenceOption {
        reexport.map { reexportOpt =>
          query.map(_.reexport).update(reexportOpt)
        }
      }
    } yield (r1 ++ r2 ++ r3 ++ r4 ++ r5 ++ r6.flatten ++ r7).sum
    for {
      id <- query.map(_.id).result.headOption
      n  <- updateQ
      _  <- if (n > 0) incrementVersionQuery else DBIO.successful(())
    } yield if (n > 0) id else None
  }

  private def updateSuggestionArgumentQuery(
    suggestionId: Long,
    action: SuggestionArgumentAction
  ): DBIO[Int] = {
    val argsQuery = Arguments.filter(_.suggestionId === suggestionId)
    action match {
      case SuggestionArgumentAction.Add(index, argument) =>
        for {
          _ <- argsQuery.filter(_.index === index).delete
          n <- Arguments += toArgumentRow(suggestionId, index, argument)
        } yield n
      case SuggestionArgumentAction.Remove(index) =>
        for {
          n <- argsQuery.filter(_.index === index).delete
        } yield n
      case SuggestionArgumentAction.Modify(
            index,
            nameOpt,
            tpeOpt,
            suspendedOpt,
            defaultOpt,
            valueOpt
          ) =>
        val argQuery = argsQuery.filter(_.index === index)
        for {
          r1 <- DBIO.sequenceOption {
            nameOpt.map(name => argQuery.map(_.name).update(name))
          }
          r2 <- DBIO.sequenceOption {
            tpeOpt.map(tpe => argQuery.map(_.tpe).update(tpe))
          }
          r3 <- DBIO.sequenceOption {
            suspendedOpt.map(suspended =>
              argQuery.map(_.isSuspended).update(suspended)
            )
          }
          r4 <- DBIO.sequenceOption {
            defaultOpt.map(default =>
              argQuery.map(_.hasDefault).update(default)
            )
          }
          r5 <- DBIO.sequenceOption {
            valueOpt.map(value => argQuery.map(_.defaultValue).update(value))
          }
        } yield Seq(r1, r2, r3, r4, r5).flatten.sum
    }
  }

  /** The query to update a list of suggestions by external id.
    *
    * @param expressions the list of expressions to update
    * @return the current database version with the list of updated suggestion ids
    */
  private def updateAllQuery(
    expressions: Seq[(Suggestion.ExternalId, String)]
  ): DBIO[(Long, Seq[Option[Long]])] = {
    val query = for {
      ids <- DBIO.sequence(
        expressions.map(Function.tupled(updateByExternalIdQuery))
      )
      version <-
        if (ids.exists(_.nonEmpty)) incrementVersionQuery
        else currentVersionQuery
    } yield (version, ids)
    query
  }

  /** The query to update the project name.
    *
    * @param oldName the old name of the project
    * @param newName the new project name
    * @return the current database version and lists of suggestion ids
    * with updated module name, self type, return type and arguments
    */
  private def renameProjectQuery(
    oldName: String,
    newName: String
  ): DBIO[
    (
      Long,
      Seq[(Long, String)],
      Seq[(Long, String)],
      Seq[(Long, String)],
      Seq[(Long, Int, String)]
    )
  ] = {
    def updateQuery(column: String) =
      sqlu"""update suggestions
          set #$column = $newName || substr(#$column, length($oldName) + 1)
          where #$column like '#$oldName.%'"""
    val argumentsUpdateQuery =
      sqlu"""update arguments
          set type = $newName || substr(type, length($oldName) + 1)
          where type like '#$oldName.%'"""
    def noop[A] = DBIO.successful(Seq[A]())

    val selectUpdatedModulesQuery = Suggestions
      .filter(row => row.module.like(s"$newName.%"))
      .map(row => (row.id, row.module))
      .result
    val selectUpdatedSelfTypesQuery = Suggestions
      .filter(_.selfType.like(s"$newName.%"))
      .map(row => (row.id, row.selfType))
      .result
    val selectUpdatedReturnTypesQuery = Suggestions
      .filter(_.returnType.like(s"$newName.%"))
      .map(row => (row.id, row.returnType))
      .result
    val selectUpdatedArgumentsQuery = Arguments
      .filter(_.tpe.like(s"$newName.%"))
      .map(row => (row.suggestionId, row.index, row.tpe))
      .result

    for {
      n1            <- updateQuery("module")
      moduleIds     <- if (n1 > 0) selectUpdatedModulesQuery else noop
      n2            <- updateQuery("self_type")
      selfTypeIds   <- if (n2 > 0) selectUpdatedSelfTypesQuery else noop
      n3            <- updateQuery("return_type")
      returnTypeIds <- if (n3 > 0) selectUpdatedReturnTypesQuery else noop
      n4            <- argumentsUpdateQuery
      argumentIds   <- if (n4 > 0) selectUpdatedArgumentsQuery else noop
      version <-
        if (n1 > 0 || n2 > 0 || n3 > 0 || n4 > 0) incrementVersionQuery
        else currentVersionQuery
    } yield (version, moduleIds, selfTypeIds, returnTypeIds, argumentIds)
  }

  /** The query to get current version of the repo. */
  private def currentVersionQuery: DBIO[Long] = {
    for {
      versionOpt <- SuggestionsVersion.result.headOption
    } yield versionOpt.flatMap(_.id).getOrElse(0L)
  }

  /** The query to increment the current version of the repo. */
  private def incrementVersionQuery: DBIO[Long] = {
    val incrementQuery = for {
      version <- SuggestionsVersion.returning(
        SuggestionsVersion.map(_.id)
      ) += SuggestionsVersionRow(None)
      _ <- SuggestionsVersion.filterNot(_.id === version).delete
    } yield version
    incrementQuery
  }

  /** The query to get current version of the repo. */
  private def currentSchemaVersionQuery: DBIO[Long] = {
    for {
      versionOpt <- SchemaVersion.result.headOption
    } yield versionOpt.flatMap(_.id).getOrElse(0L)
  }

  /** The query to initialize the [[SchemaVersion]] table. */
  private def initSchemaVersionQuery: DBIO[Long] = {
    setSchemaVersionQuery(SchemaVersion.CurrentVersion)
  }

  /** The query setting the schema version.
    *
    * @param version the schema version.
    * @return the current value of the schema version
    */
  private def setSchemaVersionQuery(version: Long): DBIO[Long] = {
    val query = for {
      _ <- SchemaVersion.delete
      _ <- SchemaVersion += SchemaVersionRow(Some(version))
    } yield version
    query
  }

  /** The query to delete the schema version. */
  private def clearSchemaVersionQuery: DBIO[Unit] =
    for {
      _ <- SchemaVersion.delete
    } yield ()

  /** The query to insert suggestions in a batch.
    *
    * @param suggestions the list of suggestions to insert
    * @return the current size of the database
    */
  private def insertBatchQuery(
    suggestions: Array[Suggestion]
  ): DBIO[Int] = {
    val rows = suggestions.map(toSuggestionRow)
    for {
      _    <- (Suggestions ++= rows.map(_._1)).asTry
      size <- Suggestions.length.result
    } yield size
  }

  /** Create a search query by the provided parameters.
    *
    * Even if the module is specified, the response includes all available
    * global symbols (atoms and method).
    *
    * @param module the module name search parameter
    * @param selfTypes the selfType search parameter
    * @param returnType the returnType search parameter
    * @param kinds the list suggestion kinds to search
    * @param position the absolute position in the text
    * @return the search query
    */
  private def searchQueryBuilder(
    module: Option[String],
    selfTypes: Seq[String],
    returnType: Option[String],
    kinds: Option[Seq[Suggestion.Kind]],
    position: Option[Suggestion.Position]
  ): Query[SuggestionsTable, SuggestionRow, Seq] = {
    Suggestions
      .filterOpt(module) { case (row, value) =>
        row.scopeStartLine === ScopeColumn.EMPTY || row.module === value
      }
      .filterIf(selfTypes.nonEmpty) { row => row.selfType.inSet(selfTypes) }
      .filterOpt(returnType) { case (row, value) =>
        row.returnType === value
      }
      .filterOpt(kinds) { case (row, value) =>
        row.kind inSet value.map(SuggestionKind(_))
      }
      .filterOpt(position) { case (row, value) =>
        (row.scopeStartLine === ScopeColumn.EMPTY) ||
        (
          row.scopeStartLine <= value.line &&
          row.scopeStartOffset <= value.character &&
          row.scopeEndLine >= value.line &&
          row.scopeEndOffset >= value.character
        )
      }
  }

  /** Convert the rows of suggestions joined with arguments to a list of
    * suggestions.
    */
  private def joinedToSuggestions(
    coll: Seq[(Option[ArgumentRow], SuggestionRow)]
  ): Seq[Suggestion] = {
    coll
      .groupBy(_._2)
      .view
      .mapValues(_.flatMap(_._1))
      .map(Function.tupled(toSuggestion))
      .toSeq
  }

  /** Convert the rows of suggestions joined with arguments to a list of
    * suggestion entries.
    */
  private def joinedToSuggestionEntries(
    coll: Seq[(Option[ArgumentRow], SuggestionRow)]
  ): Seq[SuggestionEntry] = {
    coll
      .groupBy(_._2)
      .view
      .mapValues(_.flatMap(_._1))
      .map(Function.tupled(toSuggestionEntry))
      .toSeq
  }

  /** Convert the suggestion to a row in the suggestions table. */
  private def toSuggestionRow(
    suggestion: Suggestion
  ): (SuggestionRow, Seq[Suggestion.Argument]) =
    suggestion match {
      case Suggestion.Module(module, doc, docHtml, reexport) =>
        val row = SuggestionRow(
          id                = None,
          externalIdLeast   = None,
          externalIdMost    = None,
          kind              = SuggestionKind.MODULE,
          module            = module,
          name              = module,
          selfType          = SelfTypeColumn.EMPTY,
          returnType        = "",
          scopeStartLine    = ScopeColumn.EMPTY,
          scopeStartOffset  = ScopeColumn.EMPTY,
          scopeEndLine      = ScopeColumn.EMPTY,
          scopeEndOffset    = ScopeColumn.EMPTY,
          documentation     = doc,
          documentationHtml = docHtml,
          reexport          = reexport
        )
        row -> Seq()
      case Suggestion.Atom(
            expr,
            module,
            name,
            args,
            returnType,
            doc,
            docHtml,
            reexport
          ) =>
        val row = SuggestionRow(
          id                = None,
          externalIdLeast   = expr.map(_.getLeastSignificantBits),
          externalIdMost    = expr.map(_.getMostSignificantBits),
          kind              = SuggestionKind.ATOM,
          module            = module,
          name              = name,
          selfType          = SelfTypeColumn.EMPTY,
          returnType        = returnType,
          documentation     = doc,
          documentationHtml = docHtml,
          scopeStartLine    = ScopeColumn.EMPTY,
          scopeStartOffset  = ScopeColumn.EMPTY,
          scopeEndLine      = ScopeColumn.EMPTY,
          scopeEndOffset    = ScopeColumn.EMPTY,
          reexport          = reexport
        )
        row -> args
      case Suggestion.Method(
            expr,
            module,
            name,
            args,
            selfType,
            returnType,
            doc,
            docHtml,
            reexport
          ) =>
        val row = SuggestionRow(
          id                = None,
          externalIdLeast   = expr.map(_.getLeastSignificantBits),
          externalIdMost    = expr.map(_.getMostSignificantBits),
          kind              = SuggestionKind.METHOD,
          module            = module,
          name              = name,
          selfType          = selfType,
          returnType        = returnType,
          documentation     = doc,
          documentationHtml = docHtml,
          scopeStartLine    = ScopeColumn.EMPTY,
          scopeStartOffset  = ScopeColumn.EMPTY,
          scopeEndLine      = ScopeColumn.EMPTY,
          scopeEndOffset    = ScopeColumn.EMPTY,
          reexport          = reexport
        )
        row -> args
      case Suggestion.Function(expr, module, name, args, returnType, scope) =>
        val row = SuggestionRow(
          id                = None,
          externalIdLeast   = expr.map(_.getLeastSignificantBits),
          externalIdMost    = expr.map(_.getMostSignificantBits),
          kind              = SuggestionKind.FUNCTION,
          module            = module,
          name              = name,
          selfType          = SelfTypeColumn.EMPTY,
          returnType        = returnType,
          documentation     = None,
          documentationHtml = None,
          scopeStartLine    = scope.start.line,
          scopeStartOffset  = scope.start.character,
          scopeEndLine      = scope.end.line,
          scopeEndOffset    = scope.end.character,
          reexport          = None
        )
        row -> args
      case Suggestion.Local(expr, module, name, returnType, scope) =>
        val row = SuggestionRow(
          id                = None,
          externalIdLeast   = expr.map(_.getLeastSignificantBits),
          externalIdMost    = expr.map(_.getMostSignificantBits),
          kind              = SuggestionKind.LOCAL,
          module            = module,
          name              = name,
          selfType          = SelfTypeColumn.EMPTY,
          returnType        = returnType,
          documentation     = None,
          documentationHtml = None,
          scopeStartLine    = scope.start.line,
          scopeStartOffset  = scope.start.character,
          scopeEndLine      = scope.end.line,
          scopeEndOffset    = scope.end.character,
          reexport          = None
        )
        row -> Seq()
    }

  /** Convert the argument to a row in the arguments table. */
  private def toArgumentRow(
    suggestionId: Long,
    index: Int,
    argument: Suggestion.Argument
  ): ArgumentRow =
    ArgumentRow(
      id           = None,
      suggestionId = suggestionId,
      index        = index,
      name         = argument.name,
      tpe          = argument.reprType,
      isSuspended  = argument.isSuspended,
      hasDefault   = argument.hasDefault,
      defaultValue = argument.defaultValue
    )

  /** Convert the database rows to a suggestion entry. */
  private def toSuggestionEntry(
    suggestion: SuggestionRow,
    arguments: Seq[ArgumentRow]
  ): SuggestionEntry =
    SuggestionEntry(suggestion.id.get, toSuggestion(suggestion, arguments))

  /** Convert the databaes rows to a suggestion. */
  private def toSuggestion(
    suggestion: SuggestionRow,
    arguments: Seq[ArgumentRow]
  ): Suggestion =
    suggestion.kind match {
      case SuggestionKind.MODULE =>
        Suggestion.Module(
          module            = suggestion.module,
          documentation     = suggestion.documentation,
          documentationHtml = suggestion.documentationHtml,
          reexport          = suggestion.reexport
        )
      case SuggestionKind.ATOM =>
        Suggestion.Atom(
          externalId =
            toUUID(suggestion.externalIdLeast, suggestion.externalIdMost),
          module            = suggestion.module,
          name              = suggestion.name,
          arguments         = arguments.sortBy(_.index).map(toArgument),
          returnType        = suggestion.returnType,
          documentation     = suggestion.documentation,
          documentationHtml = suggestion.documentationHtml,
          reexport          = suggestion.reexport
        )
      case SuggestionKind.METHOD =>
        Suggestion.Method(
          externalId =
            toUUID(suggestion.externalIdLeast, suggestion.externalIdMost),
          module            = suggestion.module,
          name              = suggestion.name,
          arguments         = arguments.sortBy(_.index).map(toArgument),
          selfType          = suggestion.selfType,
          returnType        = suggestion.returnType,
          documentation     = suggestion.documentation,
          documentationHtml = suggestion.documentationHtml,
          reexport          = suggestion.reexport
        )
      case SuggestionKind.FUNCTION =>
        Suggestion.Function(
          externalId =
            toUUID(suggestion.externalIdLeast, suggestion.externalIdMost),
          module     = suggestion.module,
          name       = suggestion.name,
          arguments  = arguments.sortBy(_.index).map(toArgument),
          returnType = suggestion.returnType,
          scope = Suggestion.Scope(
            Suggestion.Position(
              suggestion.scopeStartLine,
              suggestion.scopeStartOffset
            ),
            Suggestion.Position(
              suggestion.scopeEndLine,
              suggestion.scopeEndOffset
            )
          )
        )
      case SuggestionKind.LOCAL =>
        Suggestion.Local(
          externalId =
            toUUID(suggestion.externalIdLeast, suggestion.externalIdMost),
          module     = suggestion.module,
          name       = suggestion.name,
          returnType = suggestion.returnType,
          scope = Suggestion.Scope(
            Suggestion.Position(
              suggestion.scopeStartLine,
              suggestion.scopeStartOffset
            ),
            Suggestion.Position(
              suggestion.scopeEndLine,
              suggestion.scopeEndOffset
            )
          )
        )
      case k =>
        throw new NoSuchElementException(s"Unknown suggestion kind: $k")
    }

  /** Convert the database row to the suggestion argument. */
  private def toArgument(row: ArgumentRow): Suggestion.Argument =
    Suggestion.Argument(
      name         = row.name,
      reprType     = row.tpe,
      isSuspended  = row.isSuspended,
      hasDefault   = row.hasDefault,
      defaultValue = row.defaultValue
    )

  /** Convert bits to the UUID.
    *
    * @param least the least significant bits of the UUID
    * @param most the most significant bits of the UUID
    * @return the new UUID
    */
  private def toUUID(least: Option[Long], most: Option[Long]): Option[UUID] =
    for {
      l <- least
      m <- most
    } yield new UUID(m, l)

}
