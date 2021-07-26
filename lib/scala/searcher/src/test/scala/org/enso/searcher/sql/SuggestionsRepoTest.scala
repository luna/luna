package org.enso.searcher.sql

import java.nio.file.{Files, Path}
import java.util.UUID

import org.enso.polyglot.{ExportedSymbol, ModuleExports, Suggestion}
import org.enso.polyglot.runtime.Runtime.Api
import org.enso.searcher.data.QueryResult
import org.enso.testkit.RetrySpec
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

import scala.concurrent.Await
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._

class SuggestionsRepoTest extends AnyWordSpec with Matchers with RetrySpec {

  val Timeout: FiniteDuration = 20.seconds

  val tmpdir: Path = {
    val tmp = Files.createTempDirectory("suggestions-repo-test")
    sys.addShutdownHook {
      Files.list(tmp).forEach { path =>
        path.toFile.delete()
      }
      tmp.toFile.delete()
    }
    tmp
  }

  def withRepo(test: SqlSuggestionsRepo => Any): Any = {
    val tmpdb = Files.createTempFile(tmpdir, "suggestions-repo", ".db")
    val repo  = new SqlSuggestionsRepo(SqlDatabase(tmpdb.toFile))
    Await.ready(repo.init, Timeout)
    try test(repo)
    finally {
      Await.ready(repo.clean, Timeout)
      repo.close()
    }
  }

  "SuggestionsRepo" should {

    "init idempotent" taggedAs Retry in withRepo { repo =>
      Await.result(repo.init, Timeout)
    }

    "check the schema version when init" taggedAs Retry in withRepo { repo =>
      val wrongSchemaVersion = Long.MinValue
      val action =
        for {
          version <- repo.setSchemaVersion(wrongSchemaVersion)
          _       <- repo.init
        } yield version

      val thrown =
        the[InvalidSchemaVersion] thrownBy Await.result(action, Timeout)
      thrown.version shouldEqual wrongSchemaVersion
    }

    "get all suggestions" taggedAs Retry in withRepo { repo =>
      val action =
        for {
          _ <- repo.insertAll(
            Seq(
              suggestion.module,
              suggestion.atom,
              suggestion.method,
              suggestion.function,
              suggestion.local
            )
          )
          all <- repo.getAll
        } yield all._2

      val suggestions = Await.result(action, Timeout).map(_.suggestion)
      suggestions should contain theSameElementsAs Seq(
        suggestion.module,
        suggestion.atom,
        suggestion.method,
        suggestion.function,
        suggestion.local
      )
    }

    "get suggestions by method call info" taggedAs Retry in withRepo { repo =>
      val action = for {
        (_, ids) <- repo.insertAll(
          Seq(
            suggestion.module,
            suggestion.atom,
            suggestion.method,
            suggestion.function,
            suggestion.local
          )
        )
        results <- repo.getAllMethods(
          Seq(
            ("Test.Main", "Test.Main", "main"),
            ("Test.Main", "Test.Main", "foo")
          )
        )
      } yield (ids, results)

      val (ids, results) = Await.result(action, Timeout)
      results should contain theSameElementsInOrderAs Seq(ids(2), None)
    }

    "get suggestions by empty method call info" taggedAs Retry in withRepo {
      repo =>
        val action = for {
          _ <- repo.insertAll(
            Seq(
              suggestion.module,
              suggestion.atom,
              suggestion.method,
              suggestion.function,
              suggestion.local
            )
          )
          results <- repo.getAllMethods(Seq())
        } yield results

        val results = Await.result(action, Timeout)
        results.isEmpty shouldEqual true
    }

    "get all module names" taggedAs Retry in withRepo { repo =>
      val action = for {
        _ <- repo.insertAll(
          Seq(
            suggestion.module,
            suggestion.atom,
            suggestion.method,
            suggestion.function,
            suggestion.local
          )
        )
        results <- repo.getAllModules
      } yield results

      val results = Await.result(action, Timeout)
      results shouldEqual Seq(suggestion.atom.module)
    }

    "fail to insert duplicate suggestion" taggedAs Retry in withRepo { repo =>
      val action =
        for {
          (_, ids) <- repo.insertAll(
            Seq(
              suggestion.module,
              suggestion.module,
              suggestion.atom,
              suggestion.atom,
              suggestion.method,
              suggestion.method,
              suggestion.function,
              suggestion.function,
              suggestion.local,
              suggestion.local
            )
          )
          all <- repo.getAll
        } yield (ids, all._2)

      val (ids, all) = Await.result(action, Timeout)
      ids(0) shouldBe a[Some[_]]
      ids(1) shouldBe a[None.type]
      ids(2) shouldBe a[Some[_]]
      ids(3) shouldBe a[None.type]
      all.map(_.suggestion) should contain theSameElementsAs Seq(
        suggestion.module,
        suggestion.atom,
        suggestion.method,
        suggestion.function,
        suggestion.local
      )
    }

    "fail to insertAll duplicate suggestion" taggedAs Retry in withRepo {
      repo =>
        val action =
          for {
            (v1, ids) <- repo.insertAll(Seq(suggestion.local, suggestion.local))
            (v2, all) <- repo.getAll
          } yield (v1, v2, ids, all)

        val (v1, v2, ids, all) = Await.result(action, Timeout)
        v1 shouldEqual v2
        ids.flatten.length shouldEqual 1
        all.map(_.suggestion) should contain theSameElementsAs Seq(
          suggestion.local
        )
    }

    "select suggestion by id" taggedAs Retry in withRepo { repo =>
      val action =
        for {
          Some(id) <- repo.insert(suggestion.atom)
          res      <- repo.select(id)
        } yield res

      Await.result(action, Timeout) shouldEqual Some(suggestion.atom)
    }

    "remove suggestion" taggedAs Retry in withRepo { repo =>
      val action =
        for {
          id1 <- repo.insert(suggestion.atom)
          id2 <- repo.remove(suggestion.atom)
        } yield (id1, id2)

      val (id1, id2) = Await.result(action, Timeout)
      id1 shouldEqual id2
    }

    "remove suggestions by module names" taggedAs Retry in withRepo { repo =>
      val action = for {
        (_, idsIns) <- repo.insertAll(
          Seq(
            suggestion.module,
            suggestion.atom,
            suggestion.method,
            suggestion.function,
            suggestion.local
          )
        )
        (_, idsRem) <- repo.removeModules(Seq(suggestion.atom.module))
      } yield (idsIns.flatten, idsRem)

      val (inserted, removed) = Await.result(action, Timeout)
      inserted should contain theSameElementsAs removed
    }

    "remove suggestions by empty module names" taggedAs Retry in withRepo {
      repo =>
        val action = for {
          (v1, _) <- repo.insertAll(
            Seq(
              suggestion.module,
              suggestion.atom,
              suggestion.method,
              suggestion.function,
              suggestion.local
            )
          )
          (v2, removed) <- repo.removeModules(Seq())
        } yield (v1, v2, removed)

        val (v1, v2, removed) = Await.result(action, Timeout)
        v1 shouldEqual v2
        removed shouldEqual Seq()
    }

    "remove all suggestions" taggedAs Retry in withRepo { repo =>
      val action = for {
        (_, Seq(_, id1, _, _, id4)) <- repo.insertAll(
          Seq(
            suggestion.module,
            suggestion.atom,
            suggestion.method,
            suggestion.function,
            suggestion.local
          )
        )
        (_, ids) <- repo.removeAll(Seq(suggestion.atom, suggestion.local))
      } yield (Seq(id1, id4), ids)

      val (inserted, removed) = Await.result(action, Timeout)
      inserted should contain theSameElementsAs removed
    }

    "get version" taggedAs Retry in withRepo { repo =>
      val action = repo.currentVersion

      Await.result(action, Timeout) shouldEqual 0L
    }

    "change version after insert" taggedAs Retry in withRepo { repo =>
      val action = for {
        v1 <- repo.currentVersion
        _  <- repo.insert(suggestion.atom)
        v2 <- repo.currentVersion
      } yield (v1, v2)

      val (v1, v2) = Await.result(action, Timeout)
      v1 should not equal v2
    }

    "not change version after failed insert" taggedAs Retry in withRepo {
      repo =>
        val action = for {
          v1 <- repo.currentVersion
          _  <- repo.insert(suggestion.atom)
          v2 <- repo.currentVersion
          _  <- repo.insert(suggestion.atom)
          v3 <- repo.currentVersion
        } yield (v1, v2, v3)

        val (v1, v2, v3) = Await.result(action, Timeout)
        v1 should not equal v2
        v2 shouldEqual v3
    }

    "change version after remove" taggedAs Retry in withRepo { repo =>
      val action = for {
        v1 <- repo.currentVersion
        _  <- repo.insert(suggestion.local)
        v2 <- repo.currentVersion
        _  <- repo.remove(suggestion.local)
        v3 <- repo.currentVersion
      } yield (v1, v2, v3)

      val (v1, v2, v3) = Await.result(action, Timeout)
      v1 should not equal v2
      v2 should not equal v3
    }

    "not change version after failed remove" taggedAs Retry in withRepo {
      repo =>
        val action = for {
          v1 <- repo.currentVersion
          _  <- repo.insert(suggestion.local)
          v2 <- repo.currentVersion
          _  <- repo.remove(suggestion.local)
          v3 <- repo.currentVersion
          _  <- repo.remove(suggestion.local)
          v4 <- repo.currentVersion
        } yield (v1, v2, v3, v4)

        val (v1, v2, v3, v4) = Await.result(action, Timeout)
        v1 should not equal v2
        v2 should not equal v3
        v3 shouldEqual v4
    }

    "change version after remove by module name" taggedAs Retry in withRepo {
      repo =>
        val action = for {
          v1      <- repo.currentVersion
          _       <- repo.insert(suggestion.local)
          v2      <- repo.currentVersion
          (v3, _) <- repo.removeModules(Seq(suggestion.local.module))
        } yield (v1, v2, v3)

        val (v1, v2, v3) = Await.result(action, Timeout)
        v1 should not equal v2
        v2 should not equal v3
    }

    "not change version after failed remove by module name" taggedAs Retry in withRepo {
      repo =>
        val action = for {
          v1      <- repo.currentVersion
          _       <- repo.insert(suggestion.local)
          v2      <- repo.currentVersion
          _       <- repo.removeModules(Seq(suggestion.local.module))
          v3      <- repo.currentVersion
          (v4, _) <- repo.removeModules(Seq(suggestion.local.module))
        } yield (v1, v2, v3, v4)

        val (v1, v2, v3, v4) = Await.result(action, Timeout)
        v1 should not equal v2
        v2 should not equal v3
        v3 shouldEqual v4
    }

    "change version after remove all suggestions" taggedAs Retry in withRepo {
      repo =>
        val action = for {
          v1      <- repo.currentVersion
          _       <- repo.insert(suggestion.local)
          v2      <- repo.currentVersion
          (v3, _) <- repo.removeAll(Seq(suggestion.local))
        } yield (v1, v2, v3)

        val (v1, v2, v3) = Await.result(action, Timeout)
        v1 should not equal v2
        v2 should not equal v3
    }

    "not change version after failed remove all suggestions" taggedAs Retry in withRepo {
      repo =>
        val action = for {
          v1      <- repo.currentVersion
          _       <- repo.insert(suggestion.local)
          v2      <- repo.currentVersion
          (v3, _) <- repo.removeAll(Seq(suggestion.local))
          (v4, _) <- repo.removeAll(Seq(suggestion.local))
        } yield (v1, v2, v3, v4)

        val (v1, v2, v3, v4) = Await.result(action, Timeout)
        v1 should not equal v2
        v2 should not equal v3
        v3 shouldEqual v4
    }

    "update suggestion by external id" taggedAs Retry in withRepo { repo =>
      val newReturnType = "Quux"
      val action = for {
        _         <- repo.insert(suggestion.module)
        _         <- repo.insert(suggestion.atom)
        _         <- repo.insert(suggestion.method)
        _         <- repo.insert(suggestion.function)
        Some(id4) <- repo.insert(suggestion.local)
        res <-
          repo.updateAll(Seq(suggestion.local.externalId.get -> newReturnType))
        Some(val4) <- repo.select(id4)
      } yield (id4, res._2, val4)

      val (suggestionId, updatedIds, result) = Await.result(action, Timeout)
      updatedIds.flatten shouldEqual Seq(suggestionId)
      result shouldEqual suggestion.local.copy(returnType = newReturnType)
    }

    "update suggestion external id" taggedAs Retry in withRepo { repo =>
      val newUuid = UUID.randomUUID()
      val action = for {
        (v1, Seq(_, _, id1, _, _)) <- repo.insertAll(
          Seq(
            suggestion.module,
            suggestion.atom,
            suggestion.method,
            suggestion.function,
            suggestion.local
          )
        )
        (v2, id2) <- repo.update(
          suggestion.method,
          Some(Some(newUuid)),
          None,
          None,
          None,
          None,
          None,
          None
        )
        s <- repo.select(id1.get)
      } yield (v1, id1, v2, id2, s)
      val (v1, id1, v2, id2, s) = Await.result(action, Timeout)
      v1 should not equal v2
      id1 shouldEqual id2
      s shouldEqual Some(suggestion.method.copy(externalId = Some(newUuid)))
    }

    "update suggestion removing external id" taggedAs Retry in withRepo {
      repo =>
        val action = for {
          (v1, Seq(_, _, _, id1, _)) <- repo.insertAll(
            Seq(
              suggestion.module,
              suggestion.atom,
              suggestion.method,
              suggestion.function,
              suggestion.local
            )
          )
          (v2, id2) <- repo.update(
            suggestion.function,
            Some(None),
            None,
            None,
            None,
            None,
            None,
            None
          )
          s <- repo.select(id1.get)
        } yield (v1, id1, v2, id2, s)
        val (v1, id1, v2, id2, s) = Await.result(action, Timeout)
        v1 should not equal v2
        id1 shouldEqual id2
        s shouldEqual Some(suggestion.function.copy(externalId = None))
    }

    "update suggestion return type" taggedAs Retry in withRepo { repo =>
      val newReturnType = "NewType"
      val action = for {
        (v1, Seq(_, _, _, id1, _)) <- repo.insertAll(
          Seq(
            suggestion.module,
            suggestion.atom,
            suggestion.method,
            suggestion.function,
            suggestion.local
          )
        )
        (v2, id2) <- repo.update(
          suggestion.function,
          None,
          None,
          Some(newReturnType),
          None,
          None,
          None,
          None
        )
        s <- repo.select(id1.get)
      } yield (v1, id1, v2, id2, s)
      val (v1, id1, v2, id2, s) = Await.result(action, Timeout)
      v1 should not equal v2
      id1 shouldEqual id2
      s shouldEqual Some(suggestion.function.copy(returnType = newReturnType))
    }

    "update suggestion atom documentation" taggedAs Retry in withRepo { repo =>
      val newDoc = "My Doc"
      val action = for {
        (v1, Seq(_, id1, _, _, _)) <- repo.insertAll(
          Seq(
            suggestion.module,
            suggestion.atom,
            suggestion.method,
            suggestion.function,
            suggestion.local
          )
        )
        (v2, id2) <- repo.update(
          suggestion.atom,
          None,
          None,
          None,
          Some(Some(newDoc)),
          None,
          None,
          None
        )
        s <- repo.select(id1.get)
      } yield (v1, id1, v2, id2, s)
      val (v1, id1, v2, id2, s) = Await.result(action, Timeout)
      v1 should not equal v2
      id1 shouldEqual id2
      s shouldEqual Some(suggestion.atom.copy(documentation = Some(newDoc)))
    }

    "update suggestion atom HTML documentation" taggedAs Retry in withRepo {
      repo =>
        val newDoc = "My Doc"
        val action = for {
          (v1, Seq(_, id1, _, _, _)) <- repo.insertAll(
            Seq(
              suggestion.module,
              suggestion.atom,
              suggestion.method,
              suggestion.function,
              suggestion.local
            )
          )
          (v2, id2) <- repo.update(
            suggestion.atom,
            None,
            None,
            None,
            None,
            Some(Some(newDoc)),
            None,
            None
          )
          s <- repo.select(id1.get)
        } yield (v1, id1, v2, id2, s)
        val (v1, id1, v2, id2, s) = Await.result(action, Timeout)
        v1 should not equal v2
        id1 shouldEqual id2
        s shouldEqual Some(
          suggestion.atom.copy(documentationHtml = Some(newDoc))
        )
    }

    "update suggestion module documentation" taggedAs Retry in withRepo {
      repo =>
        val newDoc = "My Doc"
        val action = for {
          (v1, Seq(id1, _, _, _, _)) <- repo.insertAll(
            Seq(
              suggestion.module,
              suggestion.atom,
              suggestion.method,
              suggestion.function,
              suggestion.local
            )
          )
          (v2, id2) <- repo.update(
            suggestion.module,
            None,
            None,
            None,
            Some(Some(newDoc)),
            None,
            None,
            None
          )
          s <- repo.select(id1.get)
        } yield (v1, id1, v2, id2, s)
        val (v1, id1, v2, id2, s) = Await.result(action, Timeout)
        v1 should not equal v2
        id1 shouldEqual id2
        s shouldEqual Some(suggestion.module.copy(documentation = Some(newDoc)))
    }

    "update suggestion module HTML documentation" taggedAs Retry in withRepo {
      repo =>
        val newDoc = "My Doc"
        val action = for {
          (v1, Seq(id1, _, _, _, _)) <- repo.insertAll(
            Seq(
              suggestion.module,
              suggestion.atom,
              suggestion.method,
              suggestion.function,
              suggestion.local
            )
          )
          (v2, id2) <- repo.update(
            suggestion.module,
            None,
            None,
            None,
            None,
            Some(Some(newDoc)),
            None,
            None
          )
          s <- repo.select(id1.get)
        } yield (v1, id1, v2, id2, s)
        val (v1, id1, v2, id2, s) = Await.result(action, Timeout)
        v1 should not equal v2
        id1 shouldEqual id2
        s shouldEqual Some(
          suggestion.module.copy(documentationHtml = Some(newDoc))
        )
    }

    "update suggestion removing documentation" taggedAs Retry in withRepo {
      repo =>
        val action = for {
          (v1, Seq(_, id1, _, _, _)) <- repo.insertAll(
            Seq(
              suggestion.module,
              suggestion.atom,
              suggestion.method,
              suggestion.function,
              suggestion.local
            )
          )
          (v2, id2) <- repo.update(
            suggestion.atom,
            None,
            None,
            None,
            Some(None),
            None,
            None,
            None
          )
          s <- repo.select(id1.get)
        } yield (v1, id1, v2, id2, s)
        val (v1, id1, v2, id2, s) = Await.result(action, Timeout)
        v1 should not equal v2
        id1 shouldEqual id2
        s shouldEqual Some(suggestion.atom.copy(documentation = None))
    }

    "update suggestion removing HTML documentation" taggedAs Retry in withRepo {
      repo =>
        val action = for {
          (v1, Seq(_, id1, _, _, _)) <- repo.insertAll(
            Seq(
              suggestion.module,
              suggestion.atom,
              suggestion.method,
              suggestion.function,
              suggestion.local
            )
          )
          (v2, id2) <- repo.update(
            suggestion.atom,
            None,
            None,
            None,
            None,
            Some(None),
            None,
            None
          )
          s <- repo.select(id1.get)
        } yield (v1, id1, v2, id2, s)
        val (v1, id1, v2, id2, s) = Await.result(action, Timeout)
        v1 should not equal v2
        id1 shouldEqual id2
        s shouldEqual Some(suggestion.atom.copy(documentationHtml = None))
    }

    "update suggestion scope" taggedAs Retry in withRepo { repo =>
      val newScope = Suggestion.Scope(
        Suggestion.Position(14, 15),
        Suggestion.Position(42, 43)
      )
      val action = for {
        (v1, Seq(_, _, _, _, id1)) <- repo.insertAll(
          Seq(
            suggestion.module,
            suggestion.atom,
            suggestion.method,
            suggestion.function,
            suggestion.local
          )
        )
        (v2, id2) <- repo.update(
          suggestion.local,
          None,
          None,
          None,
          None,
          None,
          Some(newScope),
          None
        )
        s <- repo.select(id1.get)
      } yield (v1, id1, v2, id2, s)
      val (v1, id1, v2, id2, s) = Await.result(action, Timeout)
      v1 should not equal v2
      id1 shouldEqual id2
      s shouldEqual Some(suggestion.local.copy(scope = newScope))
    }

    "remove suggestion arguments" taggedAs Retry in withRepo { repo =>
      val newArgs = Seq(
        Api.SuggestionArgumentAction.Remove(1)
      )
      val action = for {
        (v1, Seq(_, id1, _, _, _)) <- repo.insertAll(
          Seq(
            suggestion.module,
            suggestion.atom,
            suggestion.method,
            suggestion.function,
            suggestion.local
          )
        )
        (v2, id2) <- repo.update(
          suggestion.atom,
          None,
          Some(newArgs),
          None,
          None,
          None,
          None,
          None
        )
        s <- repo.select(id1.get)
      } yield (v1, id1, v2, id2, s)
      val (v1, id1, v2, id2, s) = Await.result(action, Timeout)
      v1 should not equal v2
      id1 shouldEqual id2
      s shouldEqual Some(
        suggestion.atom.copy(arguments = suggestion.atom.arguments.init)
      )
    }

    "add suggestion arguments" taggedAs Retry in withRepo { repo =>
      val newArgs = Seq(
        Api.SuggestionArgumentAction.Add(2, suggestion.atom.arguments(0)),
        Api.SuggestionArgumentAction.Add(3, suggestion.atom.arguments(1))
      )
      val action = for {
        (v1, Seq(_, id1, _, _, _)) <- repo.insertAll(
          Seq(
            suggestion.module,
            suggestion.atom,
            suggestion.method,
            suggestion.function,
            suggestion.local
          )
        )
        (v2, id2) <- repo.update(
          suggestion.atom,
          None,
          Some(newArgs),
          None,
          None,
          None,
          None,
          None
        )
        s <- repo.select(id1.get)
      } yield (v1, id1, v2, id2, s)
      val (v1, id1, v2, id2, s) = Await.result(action, Timeout)
      v1 should not equal v2
      id1 shouldEqual id2
      s shouldEqual Some(
        suggestion.atom.copy(arguments =
          suggestion.atom.arguments ++ suggestion.atom.arguments
        )
      )
    }

    "update suggestion arguments" taggedAs Retry in withRepo { repo =>
      val newArgs = Seq(
        Api.SuggestionArgumentAction.Modify(
          1,
          Some("c"),
          Some("C"),
          Some(true),
          Some(true),
          Some(Some("C"))
        )
      )
      val action = for {
        (v1, Seq(_, id1, _, _, _)) <- repo.insertAll(
          Seq(
            suggestion.module,
            suggestion.atom,
            suggestion.method,
            suggestion.function,
            suggestion.local
          )
        )
        (v2, id2) <- repo.update(
          suggestion.atom,
          None,
          Some(newArgs),
          None,
          None,
          None,
          None,
          None
        )
        s <- repo.select(id1.get)
      } yield (v1, id1, v2, id2, s)
      val (v1, id1, v2, id2, s) = Await.result(action, Timeout)
      v1 should not equal v2
      id1 shouldEqual id2
      s shouldEqual Some(
        suggestion.atom.copy(arguments =
          suggestion.atom.arguments.init :+
          Suggestion.Argument("c", "C", true, true, Some("C"))
        )
      )
    }

    "update suggestion empty request" taggedAs Retry in withRepo { repo =>
      val action = for {
        (v1, _) <- repo.insertAll(
          Seq(
            suggestion.module,
            suggestion.atom,
            suggestion.method,
            suggestion.function,
            suggestion.local
          )
        )
        (v2, id2) <- repo.update(
          suggestion.method,
          None,
          None,
          None,
          None,
          None,
          None,
          None
        )
      } yield (v1, v2, id2)
      val (v1, v2, id2) = Await.result(action, Timeout)
      v1 shouldEqual v2
      id2 shouldEqual None
    }

    "change version after updateAll" taggedAs Retry in withRepo { repo =>
      val newReturnType = "Quux"
      val action = for {
        _   <- repo.insert(suggestion.module)
        _   <- repo.insert(suggestion.atom)
        _   <- repo.insert(suggestion.method)
        _   <- repo.insert(suggestion.function)
        id4 <- repo.insert(suggestion.local)
        v1  <- repo.currentVersion
        res <-
          repo.updateAll(Seq(suggestion.local.externalId.get -> newReturnType))
      } yield (id4, res._2, v1, res._1)

      val (suggestionId, updatedIds, v1, v2) = Await.result(action, Timeout)
      updatedIds shouldEqual Seq(suggestionId)
      v1 should not equal v2
    }

    "not change version after failed updateAll" taggedAs Retry in withRepo {
      repo =>
        val newReturnType = "Quux"
        val action = for {
          _   <- repo.insert(suggestion.module)
          _   <- repo.insert(suggestion.atom)
          _   <- repo.insert(suggestion.method)
          _   <- repo.insert(suggestion.function)
          _   <- repo.insert(suggestion.local)
          v1  <- repo.currentVersion
          res <- repo.updateAll(Seq(UUID.randomUUID() -> newReturnType))
        } yield (res._2, v1, res._1)

        val (updatedIds, v1, v2) = Await.result(action, Timeout)
        updatedIds shouldEqual Seq(None)
        v1 shouldEqual v2
    }

    "rename the project name" taggedAs Retry in withRepo { repo =>
      val newModuleName = "Best.Main"
      val newSelfType   = "Best.Main"
      val newReturnType = "Best.Main.MyType"
      val action = for {
        (_, ids) <- repo.insertAll(
          Seq(
            suggestion.module,
            suggestion.atom,
            suggestion.method,
            suggestion.function,
            suggestion.local
          )
        )
        (_, xs1, xs2, xs3, xs4) <- repo.renameProject("Test", "Best")
        (_, res)                <- repo.getAll
      } yield (ids, xs1, xs2, xs3, xs4, res)

      val (ids, xs1, xs2, xs3, xs4, res) = Await.result(action, Timeout)

      xs1 should contain theSameElementsAs ids.flatten.map((_, newModuleName))
      xs2 should contain theSameElementsAs Seq(ids(2)).flatten
        .map((_, newSelfType))
      xs3 should contain theSameElementsAs Seq(ids(3), ids(4)).flatten
        .map((_, newReturnType))
      xs4 should contain theSameElementsAs Seq()
      res.map(_.suggestion) should contain theSameElementsAs Seq(
        suggestion.module.copy(module = newModuleName),
        suggestion.atom.copy(module   = newModuleName),
        suggestion.method
          .copy(module = newModuleName, selfType = newSelfType),
        suggestion.function
          .copy(module = newModuleName, returnType = newReturnType),
        suggestion.local
          .copy(module = newModuleName, returnType = newReturnType)
      )
    }

    "rename the module containing project name" taggedAs Retry in withRepo {
      repo =>
        val newModuleName = "Best.Main"
        val newSelfType   = "Best.Main"
        val newReturnType = "Best.Main.MyType"

        val atom = suggestion.atom.copy(module = "Test.Main.Test.Main")
        val all =
          Seq(
            suggestion.module,
            atom,
            suggestion.method,
            suggestion.function,
            suggestion.local
          )
        val action = for {
          (_, ids)                <- repo.insertAll(all)
          (_, xs1, xs2, xs3, xs4) <- repo.renameProject("Test", "Best")
          (_, res)                <- repo.getAll
        } yield (ids, xs1, xs2, xs3, xs4, res)

        val (ids, xs1, xs2, xs3, xs4, res) = Await.result(action, Timeout)

        xs1 should contain theSameElementsAs ids.zip(all).flatMap {
          case (idOpt, _: Suggestion.Atom) =>
            idOpt.map((_, "Best.Main.Test.Main"))
          case (idOpt, _) =>
            idOpt.map((_, newModuleName))
        }
        xs2 should contain theSameElementsAs Seq(ids(2)).flatten
          .map((_, newSelfType))
        xs3 should contain theSameElementsAs Seq(ids(3), ids(4)).flatten
          .map((_, newReturnType))
        xs4 should contain theSameElementsAs Seq()
        res.map(_.suggestion) should contain theSameElementsAs Seq(
          suggestion.module
            .copy(module   = newModuleName),
          atom.copy(module = "Best.Main.Test.Main"),
          suggestion.method
            .copy(module = newModuleName, selfType = newSelfType),
          suggestion.function
            .copy(module = newModuleName, returnType = newReturnType),
          suggestion.local
            .copy(module = newModuleName, returnType = newReturnType)
        )
    }

    "rename multiple modules containing project name" taggedAs Retry in withRepo {
      repo =>
        val newMainModuleName = "Best.Main"
        val newFooModuleName  = "Best.Foo"
        val newReturnTypeName = "Best.Main.MyType"

        val module   = suggestion.module.copy(module = "Test.Main")
        val atom     = suggestion.atom.copy(module = "Test.Main")
        val method   = suggestion.method.copy(module = "Test.Foo")
        val function = suggestion.function.copy(module = "Bar.Main")
        val local    = suggestion.local.copy(module = "Bar.Main")
        val all      = Seq(module, atom, method, function, local)
        val action = for {
          (_, ids)                <- repo.insertAll(all)
          (_, xs1, xs2, xs3, xs4) <- repo.renameProject("Test", "Best")
          (_, res)                <- repo.getAll
        } yield (ids, xs1, xs2, xs3, xs4, res)

        val (ids, xs1, xs2, xs3, xs4, res) = Await.result(action, Timeout)

        xs1 should contain theSameElementsAs ids
          .zip(Seq(module, atom, method))
          .flatMap {
            case (idOpt, _: Suggestion.Module) =>
              idOpt.map((_, newMainModuleName))
            case (idOpt, _: Suggestion.Atom) =>
              idOpt.map((_, newMainModuleName))
            case (idOpt, _) =>
              idOpt.map((_, newFooModuleName))
          }
        xs2 should contain theSameElementsAs Seq(ids(2)).flatten
          .map((_, newMainModuleName))
        xs3 should contain theSameElementsAs Seq(ids(3), ids(4)).flatten
          .map((_, newReturnTypeName))
        xs4 should contain theSameElementsAs Seq()
        res.map(_.suggestion) should contain theSameElementsAs Seq(
          module.copy(module       = newMainModuleName),
          atom.copy(module         = newMainModuleName),
          method.copy(module       = newFooModuleName, selfType = newMainModuleName),
          function.copy(returnType = newReturnTypeName),
          local.copy(returnType    = newReturnTypeName)
        )
    }

    "rename arguments containing project name" taggedAs Retry in withRepo {
      repo =>
        val newModuleName   = "Best.Main"
        val newSelfType     = "Best.Main"
        val newReturnType   = "Best.Main.MyType"
        val newArgumentType = "Best.Main.Test.MyType"

        val method = suggestion.method.copy(arguments =
          Seq(
            Suggestion.Argument("x", "Number", false, true, Some("0")),
            Suggestion.Argument(
              "y",
              "Test.Main.Test.MyType",
              false,
              false,
              None
            )
          )
        )
        val all =
          Seq(
            suggestion.module,
            suggestion.atom,
            method,
            suggestion.function,
            suggestion.local
          )
        val action = for {
          (_, ids)                <- repo.insertAll(all)
          (_, xs1, xs2, xs3, xs4) <- repo.renameProject("Test", "Best")
          (_, res)                <- repo.getAll
        } yield (ids, xs1, xs2, xs3, xs4, res)

        val (ids, xs1, xs2, xs3, xs4, res) = Await.result(action, Timeout)

        xs1 should contain theSameElementsAs ids.flatten.map((_, newModuleName))
        xs2 should contain theSameElementsAs Seq(ids(2)).flatten
          .map((_, newSelfType))
        xs3 should contain theSameElementsAs Seq(ids(3), ids(4)).flatten
          .map((_, newReturnType))
        xs4 should contain theSameElementsAs Seq(ids(2)).flatMap {
          _.map((_, 1, newArgumentType))
        }
        res.map(_.suggestion) should contain theSameElementsAs Seq(
          suggestion.module.copy(module = newModuleName),
          suggestion.atom.copy(module   = newModuleName),
          method
            .copy(
              module   = newModuleName,
              selfType = newSelfType,
              arguments = method.arguments.map { argument =>
                argument.copy(reprType =
                  if (argument.reprType.startsWith("Test.")) newArgumentType
                  else argument.reprType
                )
              }
            ),
          suggestion.function
            .copy(module = newModuleName, returnType = newReturnType),
          suggestion.local
            .copy(module = newModuleName, returnType = newReturnType)
        )
    }

    "change version after renaming the module" taggedAs Retry in withRepo {
      repo =>
        val action = for {
          v1               <- repo.insert(suggestion.atom)
          (v2, _, _, _, _) <- repo.renameProject("Test", "Zest")
        } yield (v1, v2)

        val (v1, v2) = Await.result(action, Timeout)
        v1 should not equal Some(v2)
    }

    "not change version when not renamed the module" taggedAs Retry in withRepo {
      repo =>
        val action = for {
          v1               <- repo.insert(suggestion.atom)
          (v2, _, _, _, _) <- repo.renameProject("Zest", "Best")
        } yield (v1, v2)

        val (v1, v2) = Await.result(action, Timeout)
        v1 shouldEqual Some(v2)
    }

    "apply export updates" taggedAs Retry in withRepo { repo =>
      val reexport = "Foo.Bar"
      val method   = suggestion.method.copy(reexport = Some(reexport))
      val updates = Seq(
        Api.ExportsUpdate(
          ModuleExports(
            reexport,
            Set(ExportedSymbol.Module(suggestion.module.module))
          ),
          Api.ExportsAction.Add()
        ),
        Api.ExportsUpdate(
          ModuleExports(
            reexport,
            Set(ExportedSymbol.Method(method.module, method.name))
          ),
          Api.ExportsAction.Remove()
        )
      )
      val action = for {
        (_, ids) <- repo.insertAll(
          Seq(
            suggestion.module,
            suggestion.atom,
            method,
            suggestion.function,
            suggestion.local
          )
        )
        results <- repo.applyExports(updates)
      } yield (ids, results)

      val (ids, results) = Await.result(action, Timeout)
      results should contain theSameElementsAs Seq(
        QueryResult(ids(0).toSeq, updates(0)),
        QueryResult(ids(2).toSeq, updates(1))
      )
    }

    "not apply exports with bigger module name" taggedAs Retry in withRepo {
      repo =>
        val reexport = "Foo.Bar.Baz"
        val method   = suggestion.method.copy(reexport = Some("Foo.Bar"))
        val updates = Seq(
          Api.ExportsUpdate(
            ModuleExports(
              reexport,
              Set(ExportedSymbol.Module(suggestion.module.module))
            ),
            Api.ExportsAction.Add()
          ),
          Api.ExportsUpdate(
            ModuleExports(
              reexport,
              Set(ExportedSymbol.Method(method.module, method.name))
            ),
            Api.ExportsAction.Remove()
          )
        )
        val action = for {
          (_, ids) <- repo.insertAll(
            Seq(
              suggestion.module,
              suggestion.atom,
              method,
              suggestion.function,
              suggestion.local
            )
          )
          results <- repo.applyExports(updates)
        } yield (ids, results)

        val (ids, results) = Await.result(action, Timeout)
        results should contain theSameElementsAs Seq(
          QueryResult(ids(0).toSeq, updates(0)),
          QueryResult(Seq(), updates(1))
        )
    }

    "change version after applying exports" taggedAs Retry in withRepo { repo =>
      val reexport = "Foo.Bar"
      val method   = suggestion.method.copy(reexport = Some(reexport))
      val updates = Seq(
        Api.ExportsUpdate(
          ModuleExports(
            reexport,
            Set(ExportedSymbol.Module(suggestion.module.module))
          ),
          Api.ExportsAction.Add()
        ),
        Api.ExportsUpdate(
          ModuleExports(
            reexport,
            Set(ExportedSymbol.Method(method.module, method.name))
          ),
          Api.ExportsAction.Remove()
        )
      )
      val action = for {
        _ <- repo.insertAll(
          Seq(
            suggestion.module,
            suggestion.atom,
            method,
            suggestion.function,
            suggestion.local
          )
        )
        v1      <- repo.currentVersion
        results <- repo.applyExports(updates)
        v2      <- repo.currentVersion
      } yield (results, v1, v2)

      val (results, v1, v2) = Await.result(action, Timeout)
      results.flatMap(_.ids).isEmpty shouldBe false
      v1 should not equal v2
    }

    "not change version when exports not applied" taggedAs Retry in withRepo {
      repo =>
        val reexport = "Foo.Bar"
        val updates = Seq(
          Api.ExportsUpdate(
            ModuleExports(
              reexport,
              Set(
                ExportedSymbol
                  .Method(suggestion.method.module, suggestion.method.name)
              )
            ),
            Api.ExportsAction.Remove()
          )
        )
        val action = for {
          _ <- repo.insertAll(
            Seq(
              suggestion.module,
              suggestion.atom,
              suggestion.method,
              suggestion.function,
              suggestion.local
            )
          )
          v1      <- repo.currentVersion
          results <- repo.applyExports(updates)
          v2      <- repo.currentVersion
        } yield (results, v1, v2)

        val (results, v1, v2) = Await.result(action, Timeout)
        results.flatMap(_.ids).isEmpty shouldBe true
        v1 shouldEqual v2
    }

    "search suggestion by empty query" taggedAs Retry in withRepo { repo =>
      val action = for {
        _   <- repo.insert(suggestion.module)
        _   <- repo.insert(suggestion.atom)
        _   <- repo.insert(suggestion.method)
        _   <- repo.insert(suggestion.function)
        _   <- repo.insert(suggestion.local)
        res <- repo.search(None, Seq(), None, None, None)
      } yield res._2

      val res = Await.result(action, Timeout)
      res.isEmpty shouldEqual true
    }

    "search suggestion by module" taggedAs Retry in withRepo { repo =>
      val action = for {
        id0 <- repo.insert(suggestion.module)
        id1 <- repo.insert(suggestion.atom)
        id2 <- repo.insert(suggestion.method)
        id3 <- repo.insert(suggestion.function)
        id4 <- repo.insert(suggestion.local)
        res <- repo.search(Some("Test.Main"), Seq(), None, None, None)
      } yield (id0, id1, id2, id3, id4, res._2)

      val (id0, id1, id2, id3, id4, res) = Await.result(action, Timeout)
      res should contain theSameElementsAs Seq(id0, id1, id2, id3, id4).flatten
    }

    "search suggestion by empty module" taggedAs Retry in withRepo { repo =>
      val action = for {
        id0 <- repo.insert(suggestion.module)
        id1 <- repo.insert(suggestion.atom)
        id2 <- repo.insert(suggestion.method)
        _   <- repo.insert(suggestion.function)
        _   <- repo.insert(suggestion.local)
        res <- repo.search(Some(""), Seq(), None, None, None)
      } yield (res._2, Seq(id0, id1, id2))

      val (res, globals) = Await.result(action, Timeout)
      res should contain theSameElementsAs globals.flatten
    }

    "search suggestion by self type" taggedAs Retry in withRepo { repo =>
      val action = for {
        _   <- repo.insert(suggestion.module)
        _   <- repo.insert(suggestion.atom)
        id2 <- repo.insert(suggestion.method)
        _   <- repo.insert(suggestion.function)
        _   <- repo.insert(suggestion.local)
        res <- repo.search(None, Seq("Test.Main"), None, None, None)
      } yield (id2, res._2)

      val (id, res) = Await.result(action, Timeout)
      res should contain theSameElementsAs Seq(id).flatten
    }

    "search suggestion by return type" taggedAs Retry in withRepo { repo =>
      val action = for {
        _   <- repo.insert(suggestion.module)
        _   <- repo.insert(suggestion.atom)
        _   <- repo.insert(suggestion.method)
        id3 <- repo.insert(suggestion.function)
        id4 <- repo.insert(suggestion.local)
        res <- repo.search(None, Seq(), Some("Test.Main.MyType"), None, None)
      } yield (id3, id4, res._2)

      val (id1, id2, res) = Await.result(action, Timeout)
      res should contain theSameElementsAs Seq(id1, id2).flatten
    }

    "search suggestion by kind" taggedAs Retry in withRepo { repo =>
      val kinds = Seq(Suggestion.Kind.Atom, Suggestion.Kind.Local)
      val action = for {
        _   <- repo.insert(suggestion.module)
        id1 <- repo.insert(suggestion.atom)
        _   <- repo.insert(suggestion.method)
        _   <- repo.insert(suggestion.function)
        id4 <- repo.insert(suggestion.local)
        res <- repo.search(None, Seq(), None, Some(kinds), None)
      } yield (id1, id4, res._2)

      val (id1, id2, res) = Await.result(action, Timeout)
      res should contain theSameElementsAs Seq(id1, id2).flatten
    }

    "search suggestion by empty kinds" taggedAs Retry in withRepo { repo =>
      val action = for {
        _   <- repo.insert(suggestion.module)
        _   <- repo.insert(suggestion.atom)
        _   <- repo.insert(suggestion.method)
        _   <- repo.insert(suggestion.function)
        _   <- repo.insert(suggestion.local)
        res <- repo.search(None, Seq(), None, Some(Seq()), None)
      } yield res._2

      val res = Await.result(action, Timeout)
      res.isEmpty shouldEqual true
    }

    "search suggestion global by scope" taggedAs Retry in withRepo { repo =>
      val action = for {
        id0 <- repo.insert(suggestion.module)
        id1 <- repo.insert(suggestion.atom)
        id2 <- repo.insert(suggestion.method)
        _   <- repo.insert(suggestion.function)
        _   <- repo.insert(suggestion.local)
        res <-
          repo.search(
            None,
            Seq(),
            None,
            None,
            Some(Suggestion.Position(99, 42))
          )
      } yield (id0, id1, id2, res._2)

      val (id0, id1, id2, res) = Await.result(action, Timeout)
      res should contain theSameElementsAs Seq(id0, id1, id2).flatten
    }

    "search suggestion local by scope" taggedAs Retry in withRepo { repo =>
      val action = for {
        id0 <- repo.insert(suggestion.module)
        id1 <- repo.insert(suggestion.atom)
        id2 <- repo.insert(suggestion.method)
        id3 <- repo.insert(suggestion.function)
        _   <- repo.insert(suggestion.local)
        res <-
          repo.search(None, Seq(), None, None, Some(Suggestion.Position(1, 5)))
      } yield (id0, id1, id2, id3, res._2)

      val (id0, id1, id2, id3, res) = Await.result(action, Timeout)
      res should contain theSameElementsAs Seq(id0, id1, id2, id3).flatten
    }

    "search suggestion by module and self type" taggedAs Retry in withRepo {
      repo =>
        val action = for {
          _   <- repo.insert(suggestion.module)
          _   <- repo.insert(suggestion.atom)
          id2 <- repo.insert(suggestion.method)
          _   <- repo.insert(suggestion.function)
          _   <- repo.insert(suggestion.local)
          res <- repo.search(
            Some("Test.Main"),
            Seq("Test.Main"),
            None,
            None,
            None
          )
        } yield (id2, res._2)

        val (id, res) = Await.result(action, Timeout)
        res should contain theSameElementsAs Seq(id).flatten
    }

    "search suggestion by return type and kind" taggedAs Retry in withRepo {
      repo =>
        val kinds = Seq(Suggestion.Kind.Atom, Suggestion.Kind.Local)
        val action = for {
          _   <- repo.insert(suggestion.module)
          _   <- repo.insert(suggestion.atom)
          _   <- repo.insert(suggestion.method)
          _   <- repo.insert(suggestion.function)
          id4 <- repo.insert(suggestion.local)
          res <- repo.search(
            None,
            Seq(),
            Some("Test.Main.MyType"),
            Some(kinds),
            None
          )
        } yield (id4, res._2)

        val (id, res) = Await.result(action, Timeout)
        res should contain theSameElementsAs Seq(id).flatten
    }

    "search suggestion by return type and scope" taggedAs Retry in withRepo {
      repo =>
        val action = for {
          _   <- repo.insert(suggestion.module)
          _   <- repo.insert(suggestion.atom)
          _   <- repo.insert(suggestion.method)
          _   <- repo.insert(suggestion.function)
          id4 <- repo.insert(suggestion.local)
          res <- repo.search(
            None,
            Seq(),
            Some("Test.Main.MyType"),
            None,
            Some(Suggestion.Position(42, 0))
          )
        } yield (id4, res._2)

        val (id, res) = Await.result(action, Timeout)
        res should contain theSameElementsAs Seq(id).flatten
    }

    "search suggestion by kind and scope" taggedAs Retry in withRepo { repo =>
      val kinds = Seq(Suggestion.Kind.Atom, Suggestion.Kind.Local)
      val action = for {
        _   <- repo.insert(suggestion.module)
        id1 <- repo.insert(suggestion.atom)
        _   <- repo.insert(suggestion.method)
        _   <- repo.insert(suggestion.function)
        _   <- repo.insert(suggestion.local)
        res <- repo.search(
          None,
          Seq(),
          None,
          Some(kinds),
          Some(Suggestion.Position(99, 1))
        )
      } yield (id1, res._2)

      val (id, res) = Await.result(action, Timeout)
      res should contain theSameElementsAs Seq(id).flatten
    }

    "search suggestion by self and return types" taggedAs Retry in withRepo {
      repo =>
        val action = for {
          _ <- repo.insert(suggestion.module)
          _ <- repo.insert(suggestion.atom)
          _ <- repo.insert(suggestion.method)
          _ <- repo.insert(suggestion.function)
          _ <- repo.insert(suggestion.local)
          res <- repo.search(
            None,
            Seq("Test.Main"),
            Some("Test.Main.MyType"),
            None,
            None
          )
        } yield res._2

        val res = Await.result(action, Timeout)
        res.isEmpty shouldEqual true
    }

    "search suggestion by module, return type and kind" taggedAs Retry in withRepo {
      repo =>
        val kinds = Seq(Suggestion.Kind.Atom, Suggestion.Kind.Local)
        val action = for {
          _   <- repo.insert(suggestion.module)
          _   <- repo.insert(suggestion.atom)
          _   <- repo.insert(suggestion.method)
          _   <- repo.insert(suggestion.function)
          id4 <- repo.insert(suggestion.local)
          res <- repo.search(
            Some("Test.Main"),
            Seq(),
            Some("Test.Main.MyType"),
            Some(kinds),
            None
          )
        } yield (id4, res._2)

        val (id, res) = Await.result(action, Timeout)
        res should contain theSameElementsAs Seq(id).flatten
    }

    "search suggestion by return type, kind and scope" taggedAs Retry in withRepo {
      repo =>
        val kinds = Seq(Suggestion.Kind.Atom, Suggestion.Kind.Local)
        val action = for {
          _   <- repo.insert(suggestion.module)
          _   <- repo.insert(suggestion.atom)
          _   <- repo.insert(suggestion.method)
          _   <- repo.insert(suggestion.function)
          id4 <- repo.insert(suggestion.local)
          res <- repo.search(
            None,
            Seq(),
            Some("Test.Main.MyType"),
            Some(kinds),
            Some(Suggestion.Position(42, 0))
          )
        } yield (id4, res._2)

        val (id, res) = Await.result(action, Timeout)
        res should contain theSameElementsAs Seq(id).flatten
    }

    "search suggestion by all parameters" taggedAs Retry in withRepo { repo =>
      val kinds = Seq(
        Suggestion.Kind.Atom,
        Suggestion.Kind.Method,
        Suggestion.Kind.Function
      )
      val action = for {
        _ <- repo.insert(suggestion.module)
        _ <- repo.insert(suggestion.atom)
        _ <- repo.insert(suggestion.method)
        _ <- repo.insert(suggestion.function)
        _ <- repo.insert(suggestion.local)
        res <- repo.search(
          Some("Test.Main"),
          Seq("Test.Main"),
          Some("Test.Main.MyType"),
          Some(kinds),
          Some(Suggestion.Position(42, 0))
        )
      } yield res._2

      val res = Await.result(action, Timeout)
      res.isEmpty shouldEqual true
    }
  }

  object suggestion {

    val module: Suggestion.Module =
      Suggestion.Module(
        module            = "Test.Main",
        documentation     = Some("This is a main module."),
        documentationHtml = Some("<p>This is a main module.</p>"),
        reexport          = None
      )

    val atom: Suggestion.Atom =
      Suggestion.Atom(
        externalId = None,
        module     = "Test.Main",
        name       = "Pair",
        arguments = Seq(
          Suggestion.Argument("a", "Any", false, false, None),
          Suggestion.Argument("b", "Any", false, false, None)
        ),
        returnType        = "Pair",
        documentation     = Some("Awesome"),
        documentationHtml = Some("<p>Awesome</p>"),
        reexport          = None
      )

    val method: Suggestion.Method =
      Suggestion.Method(
        externalId        = Some(UUID.randomUUID()),
        module            = "Test.Main",
        name              = "main",
        arguments         = Seq(),
        selfType          = "Test.Main",
        returnType        = "IO",
        documentation     = None,
        documentationHtml = None,
        reexport          = None
      )

    val function: Suggestion.Function =
      Suggestion.Function(
        externalId = Some(UUID.randomUUID()),
        module     = "Test.Main",
        name       = "bar",
        arguments = Seq(
          Suggestion.Argument("x", "Number", false, true, Some("0"))
        ),
        returnType = "Test.Main.MyType",
        scope =
          Suggestion.Scope(Suggestion.Position(1, 5), Suggestion.Position(1, 9))
      )

    val local: Suggestion.Local =
      Suggestion.Local(
        externalId = Some(UUID.randomUUID()),
        module     = "Test.Main",
        name       = "bazz",
        returnType = "Test.Main.MyType",
        scope = Suggestion.Scope(
          Suggestion.Position(32, 0),
          Suggestion.Position(84, 0)
        )
      )
  }
}
