package org.enso.languageserver.search

import java.nio.file.Files
import java.util.UUID

import akka.actor.{ActorRef, ActorSystem}
import akka.testkit.{ImplicitSender, TestKit, TestProbe}
import org.apache.commons.io.FileUtils
import org.enso.languageserver.capability.CapabilityProtocol.{
  AcquireCapability,
  CapabilityAcquired
}
import org.enso.languageserver.data._
import org.enso.languageserver.event.InitializedEvent
import org.enso.languageserver.filemanager._
import org.enso.languageserver.refactoring.ProjectNameChangedEvent
import org.enso.languageserver.search.SearchProtocol.SuggestionDatabaseEntry
import org.enso.languageserver.session.JsonSession
import org.enso.languageserver.session.SessionRouter.DeliverToJsonController
import org.enso.polyglot.{ExportedSymbol, ModuleExports, Suggestion}
import org.enso.polyglot.data.{Tree, TypeGraph}
import org.enso.polyglot.runtime.Runtime.Api
import org.enso.searcher.sql.{SqlDatabase, SqlSuggestionsRepo, SqlVersionsRepo}
import org.enso.searcher.{SuggestionsRepo, VersionsRepo}
import org.enso.testkit.RetrySpec
import org.enso.text.editing.model.Position
import org.enso.text.{ContentVersion, Sha3_224VersionCalculator}
import org.scalatest.BeforeAndAfterAll
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpecLike

import scala.collection.immutable.ListSet
import scala.concurrent.duration._
import scala.concurrent.{Await, Future}
import scala.util.{Failure, Success}

class SuggestionsHandlerSpec
    extends TestKit(ActorSystem("TestSystem"))
    with ImplicitSender
    with AnyWordSpecLike
    with Matchers
    with BeforeAndAfterAll
    with RetrySpec {

  import system.dispatcher

  val Timeout: FiniteDuration = 10.seconds

  def contentsVersion(text: String): ContentVersion =
    Sha3_224VersionCalculator.evalVersion(text)

  override def afterAll(): Unit = {
    TestKit.shutdownActorSystem(system)
  }

  "SuggestionsHandler" should {

    "prune stale modules" in withDbs { (config, suggestions, versions) =>
      // setup database
      val version1 = Array[Byte](1, 2, 3)
      val version2 = Array[Byte](2, 3, 4)
      val setupAction = for {
        _ <- suggestions.insert(TestSuggestion.atom)
        _ <- suggestions.insert(TestSuggestion.method)
        _ <- versions.setVersion(TestSuggestion.atom.module, version1)
        _ <- versions.setVersion(TestSuggestion.method.module, version2)
      } yield ()
      Await.ready(setupAction, Timeout)

      withHandler(config, suggestions, versions) { (_, connector, handler) =>
        // initialize
        handler ! SuggestionsHandler.ProjectNameUpdated("Test")
        handler ! InitializedEvent.TruffleContextInitialized
        handler ! InitializedEvent.SuggestionsRepoInitialized
        handler ! InitializedEvent.FileVersionsRepoInitialized
        connector.receiveN(1)
        handler ! Api.Response(
          UUID.randomUUID(),
          Api.GetTypeGraphResponse(buildTestTypeGraph)
        )
        connector.receiveN(1)
        // prune atom module
        handler ! Api.Response(
          UUID.randomUUID(),
          Api.VerifyModulesIndexResponse(Seq(TestSuggestion.atom.module))
        )
        // wait for initialization
        handler ! AcquireCapability(
          newJsonSession(UUID.randomUUID()),
          CapabilityRegistration(ReceivesSuggestionsDatabaseUpdates())
        )
        expectMsg(CapabilityAcquired)
        // check
        val (_, entries) = Await.result(suggestions.getAll, Timeout)
        entries.map(_.suggestion) should contain theSameElementsAs Seq(
          TestSuggestion.method
        )
        val module1Version = Await.result(
          versions.getVersion(TestSuggestion.atom.module),
          Timeout
        )
        module1Version.isEmpty shouldBe true
        val module2Version = Await.result(
          versions.getVersion(TestSuggestion.method.module),
          Timeout
        )
        module2Version.isEmpty shouldBe false
        module2Version.get should contain theSameElementsInOrderAs version2
      }
    }

    "subscribe to notification updates" taggedAs Retry in withDb {
      (_, _, _, _, handler) =>
        val clientId = UUID.randomUUID()

        handler ! AcquireCapability(
          newJsonSession(clientId),
          CapabilityRegistration(ReceivesSuggestionsDatabaseUpdates())
        )
        expectMsg(CapabilityAcquired)
    }

    "receive runtime updates" taggedAs Retry in withDb {
      (_, repo, router, _, handler) =>
        val clientId = UUID.randomUUID()

        // acquire capability
        handler ! AcquireCapability(
          newJsonSession(clientId),
          CapabilityRegistration(ReceivesSuggestionsDatabaseUpdates())
        )
        expectMsg(CapabilityAcquired)

        // receive updates
        handler ! Api.SuggestionsDatabaseModuleUpdateNotification(
          "Foo.Main",
          contentsVersion(""),
          Vector(),
          Vector(),
          Tree.Root(Suggestions.all.toVector.map { suggestion =>
            Tree.Node(
              Api.SuggestionUpdate(suggestion, Api.SuggestionAction.Add()),
              Vector()
            )
          })
        )

        val updates = Suggestions.all.zipWithIndex.map {
          case (suggestion, ix) =>
            SearchProtocol.SuggestionsDatabaseUpdate.Add(ix + 1L, suggestion)
        }
        router.expectMsg(
          DeliverToJsonController(
            clientId,
            SearchProtocol.SuggestionsDatabaseUpdateNotification(
              Suggestions.all.size.toLong,
              updates
            )
          )
        )

        // check database entries exist
        val (_, records) = Await.result(repo.getAll, Timeout)
        records.map(
          _.suggestion
        ) should contain theSameElementsAs Suggestions.all
    }

    "apply runtime updates in correct order" taggedAs Retry in withDb {
      (_, repo, router, _, handler) =>
        val clientId = UUID.randomUUID()

        // acquire capability
        handler ! AcquireCapability(
          newJsonSession(clientId),
          CapabilityRegistration(ReceivesSuggestionsDatabaseUpdates())
        )
        expectMsg(CapabilityAcquired)

        // receive updates
        handler ! Api.SuggestionsDatabaseModuleUpdateNotification(
          "Foo.Main",
          contentsVersion(""),
          Vector(),
          Vector(),
          Tree.Root(
            Suggestions.all.toVector
              .map { suggestion =>
                Tree.Node(
                  Api
                    .SuggestionUpdate(suggestion, Api.SuggestionAction.Add()),
                  Vector()
                )
              } ++
            Suggestions.all.map { suggestion =>
              Tree.Node(
                Api.SuggestionUpdate(
                  suggestion,
                  Api.SuggestionAction.Remove()
                ),
                Vector()
              )
            }
          )
        )

        val updatesAdd = Suggestions.all.zipWithIndex.map {
          case (suggestion, ix) =>
            SearchProtocol.SuggestionsDatabaseUpdate.Add(ix + 1L, suggestion)
        }
        val updatesRemove = Suggestions.all.zipWithIndex.map { case (_, ix) =>
          SearchProtocol.SuggestionsDatabaseUpdate.Remove(ix + 1L)
        }
        router.expectMsg(
          DeliverToJsonController(
            clientId,
            SearchProtocol.SuggestionsDatabaseUpdateNotification(
              Suggestions.all.size * 2L,
              updatesAdd ++ updatesRemove
            )
          )
        )

        // check that all database entries are removed
        val (_, all) = Await.result(repo.getAll, Timeout)
        all shouldEqual Seq()
    }

    "apply runtime updates tree" taggedAs Retry in withDb {
      (_, _, router, _, handler) =>
        val clientId = UUID.randomUUID()

        // acquire capability
        handler ! AcquireCapability(
          newJsonSession(clientId),
          CapabilityRegistration(ReceivesSuggestionsDatabaseUpdates())
        )
        expectMsg(CapabilityAcquired)

        val tree1 = Tree.Root(
          Vector(
            Tree.Node(
              Api.SuggestionUpdate(
                Suggestions.module,
                Api.SuggestionAction.Add()
              ),
              Vector()
            ),
            Tree.Node(
              Api.SuggestionUpdate(
                Suggestions.atom,
                Api.SuggestionAction.Add()
              ),
              Vector()
            ),
            Tree.Node(
              Api.SuggestionUpdate(
                Suggestions.method,
                Api.SuggestionAction.Add()
              ),
              Vector(
                Tree.Node(
                  Api.SuggestionUpdate(
                    Suggestions.function,
                    Api.SuggestionAction.Add()
                  ),
                  Vector(
                    Tree.Node(
                      Api.SuggestionUpdate(
                        Suggestions.local,
                        Api.SuggestionAction.Add()
                      ),
                      Vector()
                    )
                  )
                )
              )
            )
          )
        )

        // add tree
        handler ! Api.SuggestionsDatabaseModuleUpdateNotification(
          "Foo.Main",
          contentsVersion(""),
          Vector(),
          Vector(),
          tree1
        )

        val updates1 = tree1.toVector.zipWithIndex.map { case (update, ix) =>
          SearchProtocol.SuggestionsDatabaseUpdate.Add(
            ix + 1L,
            update.suggestion
          )
        }
        router.expectMsg(
          DeliverToJsonController(
            clientId,
            SearchProtocol.SuggestionsDatabaseUpdateNotification(
              updates1.size.toLong,
              updates1
            )
          )
        )

        val tree2 = Tree.Root(
          Vector(
            Tree.Node(
              Api.SuggestionUpdate(
                Suggestions.atom,
                Api.SuggestionAction.Modify(
                  arguments = Some(
                    Seq(
                      Api.SuggestionArgumentAction
                        .Modify(0, reprType = Some("A"))
                    )
                  )
                )
              ),
              Vector()
            ),
            Tree.Node(
              Api.SuggestionUpdate(
                Suggestions.method,
                Api.SuggestionAction.Modify()
              ),
              Vector(
                Tree.Node(
                  Api.SuggestionUpdate(
                    Suggestions.function,
                    Api.SuggestionAction.Modify(
                      scope = Some(Suggestions.local.scope)
                    )
                  ),
                  Vector(
                    Tree.Node(
                      Api.SuggestionUpdate(
                        Suggestions.local,
                        Api.SuggestionAction.Remove()
                      ),
                      Vector()
                    ),
                    Tree.Node(
                      Api.SuggestionUpdate(
                        Suggestions.local,
                        Api.SuggestionAction.Add()
                      ),
                      Vector()
                    )
                  )
                )
              )
            )
          )
        )
        // update tree
        handler ! Api.SuggestionsDatabaseModuleUpdateNotification(
          "Foo.Main",
          contentsVersion("1"),
          Vector(),
          Vector(),
          tree2
        )

        val updates2 = Seq(
          SearchProtocol.SuggestionsDatabaseUpdate.Modify(
            2L,
            arguments = Some(
              Seq(
                SearchProtocol.SuggestionArgumentUpdate
                  .Modify(
                    0,
                    reprType = Some(
                      SearchProtocol
                        .FieldUpdate(SearchProtocol.FieldAction.Set, Some("A"))
                    )
                  )
              )
            )
          ),
          SearchProtocol.SuggestionsDatabaseUpdate.Modify(
            4L,
            scope = Some(
              SearchProtocol.FieldUpdate(
                SearchProtocol.FieldAction.Set,
                Some(Suggestions.local.scope)
              )
            )
          ),
          SearchProtocol.SuggestionsDatabaseUpdate.Remove(5L),
          SearchProtocol.SuggestionsDatabaseUpdate.Add(6L, Suggestions.local)
        )
        router.expectMsg(
          DeliverToJsonController(
            clientId,
            SearchProtocol.SuggestionsDatabaseUpdateNotification(
              (updates1.size + updates2.size).toLong,
              updates2
            )
          )
        )
    }

    "apply update with an atom named as module" taggedAs Retry in withDb {
      (_, _, router, _, handler) =>
        val clientId = UUID.randomUUID()

        // acquire capability
        handler ! AcquireCapability(
          newJsonSession(clientId),
          CapabilityRegistration(ReceivesSuggestionsDatabaseUpdates())
        )
        expectMsg(CapabilityAcquired)

        val moduleName = "Test.Foo"
        val fooAtom = Suggestion.Atom(
          externalId        = None,
          module            = moduleName,
          name              = "Foo",
          arguments         = Vector(),
          returnType        = moduleName,
          documentation     = None,
          documentationHtml = None
        )
        val module = Suggestion.Module(
          module            = moduleName,
          documentation     = None,
          documentationHtml = None
        )

        val tree = Tree.Root(
          Vector(
            Tree.Node(
              Api.SuggestionUpdate(
                module,
                Api.SuggestionAction.Add()
              ),
              Vector()
            ),
            Tree.Node(
              Api.SuggestionUpdate(
                fooAtom,
                Api.SuggestionAction.Add()
              ),
              Vector()
            )
          )
        )

        // add tree
        handler ! Api.SuggestionsDatabaseModuleUpdateNotification(
          "Foo.Main",
          contentsVersion(""),
          Vector(),
          Vector(),
          tree
        )

        val updates = tree.toVector.zipWithIndex.map { case (update, ix) =>
          SearchProtocol.SuggestionsDatabaseUpdate.Add(
            ix + 1L,
            update.suggestion
          )
        }
        router.expectMsg(
          DeliverToJsonController(
            clientId,
            SearchProtocol.SuggestionsDatabaseUpdateNotification(
              updates.size.toLong,
              updates
            )
          )
        )
    }

    "apply runtime actions" taggedAs Retry in withDb {
      (_, repo, router, _, handler) =>
        val clientId = UUID.randomUUID()

        // acquire capability
        handler ! AcquireCapability(
          newJsonSession(clientId),
          CapabilityRegistration(ReceivesSuggestionsDatabaseUpdates())
        )
        expectMsg(CapabilityAcquired)

        val suggestions = Seq(
          Suggestions.atom,
          Suggestions.method,
          Suggestions.function,
          Suggestions.local
        )

        val tree1 = Tree.Root(
          Vector(
            Tree.Node(
              Api.SuggestionUpdate(
                Suggestions.atom,
                Api.SuggestionAction.Add()
              ),
              Vector()
            ),
            Tree.Node(
              Api.SuggestionUpdate(
                Suggestions.method,
                Api.SuggestionAction.Add()
              ),
              Vector(
                Tree.Node(
                  Api.SuggestionUpdate(
                    Suggestions.function,
                    Api.SuggestionAction.Add()
                  ),
                  Vector(
                    Tree.Node(
                      Api.SuggestionUpdate(
                        Suggestions.local,
                        Api.SuggestionAction.Add()
                      ),
                      Vector()
                    )
                  )
                )
              )
            )
          )
        )

        // add tree
        handler ! Api.SuggestionsDatabaseModuleUpdateNotification(
          "Foo.Main",
          contentsVersion(""),
          Vector(),
          Vector(),
          tree1
        )

        val updates1 = tree1.toVector.zipWithIndex.map { case (update, ix) =>
          SearchProtocol.SuggestionsDatabaseUpdate.Add(
            ix + 1L,
            update.suggestion
          )
        }
        router.expectMsg(
          DeliverToJsonController(
            clientId,
            SearchProtocol.SuggestionsDatabaseUpdateNotification(
              updates1.size.toLong,
              updates1
            )
          )
        )

        // clean module
        handler ! Api.SuggestionsDatabaseModuleUpdateNotification(
          "Foo.Main",
          contentsVersion("1"),
          Vector(Api.SuggestionsDatabaseAction.Clean(Suggestions.atom.module)),
          Vector(),
          Tree.Root(Vector())
        )

        val updates2 = suggestions.zipWithIndex.map { case (_, ix) =>
          SearchProtocol.SuggestionsDatabaseUpdate.Remove(ix + 1L)
        }
        router.expectMsg(
          DeliverToJsonController(
            clientId,
            SearchProtocol.SuggestionsDatabaseUpdateNotification(
              updates1.size.toLong,
              updates2
            )
          )
        )

        // check that all database entries are removed
        val (_, all) = Await.result(repo.getAll, Timeout)
        all shouldEqual Seq()
    }

    "apply export updates" taggedAs Retry in withDb {
      (_, _, router, _, handler) =>
        val clientId = UUID.randomUUID()

        // acquire capability
        handler ! AcquireCapability(
          newJsonSession(clientId),
          CapabilityRegistration(ReceivesSuggestionsDatabaseUpdates())
        )
        expectMsg(CapabilityAcquired)

        val tree1 = Tree.Root(
          Vector(
            Tree.Node(
              Api.SuggestionUpdate(
                Suggestions.module,
                Api.SuggestionAction.Add()
              ),
              Vector()
            ),
            Tree.Node(
              Api.SuggestionUpdate(
                Suggestions.atom,
                Api.SuggestionAction.Add()
              ),
              Vector()
            ),
            Tree.Node(
              Api.SuggestionUpdate(
                Suggestions.method,
                Api.SuggestionAction.Add()
              ),
              Vector(
                Tree.Node(
                  Api.SuggestionUpdate(
                    Suggestions.function,
                    Api.SuggestionAction.Add()
                  ),
                  Vector(
                    Tree.Node(
                      Api.SuggestionUpdate(
                        Suggestions.local,
                        Api.SuggestionAction.Add()
                      ),
                      Vector()
                    )
                  )
                )
              )
            )
          )
        )

        // add tree
        handler ! Api.SuggestionsDatabaseModuleUpdateNotification(
          "Foo.Main",
          contentsVersion(""),
          Vector(),
          Vector(),
          tree1
        )

        val updates1 = tree1.toVector.zipWithIndex.map { case (update, ix) =>
          SearchProtocol.SuggestionsDatabaseUpdate.Add(
            ix + 1L,
            update.suggestion
          )
        }
        router.expectMsg(
          DeliverToJsonController(
            clientId,
            SearchProtocol.SuggestionsDatabaseUpdateNotification(
              updates1.size.toLong,
              updates1
            )
          )
        )

        val exportUpdateAdd =
          Api.ExportsUpdate(
            ModuleExports(
              "Foo.Bar",
              ListSet(
                ExportedSymbol.Module(
                  Suggestions.module.module
                ),
                ExportedSymbol.Atom(
                  Suggestions.atom.module,
                  Suggestions.atom.name
                ),
                ExportedSymbol.Method(
                  Suggestions.method.module,
                  Suggestions.method.name
                )
              )
            ),
            Api.ExportsAction.Add()
          )
        // apply updates1
        handler ! Api.SuggestionsDatabaseModuleUpdateNotification(
          "Foo.Main",
          contentsVersion("1"),
          Vector(),
          Vector(exportUpdateAdd),
          Tree.Root(Vector())
        )

        val updates2 = Seq(1L, 2L, 3L).map { id =>
          SearchProtocol.SuggestionsDatabaseUpdate.Modify(
            id,
            reexport = Some(fieldUpdate(exportUpdateAdd.exports.module))
          )
        }
        router.expectMsg(
          DeliverToJsonController(
            clientId,
            SearchProtocol.SuggestionsDatabaseUpdateNotification(
              updates1.size.toLong + 1,
              updates2
            )
          )
        )

        val exportUpdateRemove =
          Api.ExportsUpdate(
            ModuleExports(
              "Foo.Bar",
              ListSet(
                ExportedSymbol.Module(
                  Suggestions.module.module
                ),
                ExportedSymbol.Atom(
                  Suggestions.atom.module,
                  Suggestions.atom.name
                ),
                ExportedSymbol.Method(
                  Suggestions.method.module,
                  Suggestions.method.name
                )
              )
            ),
            Api.ExportsAction.Remove()
          )
        // apply updates2
        handler ! Api.SuggestionsDatabaseModuleUpdateNotification(
          "Foo.Main",
          contentsVersion("2"),
          Vector(),
          Vector(exportUpdateRemove),
          Tree.Root(Vector())
        )

        val updates3 = Seq(1L, 2L, 3L).map { id =>
          SearchProtocol.SuggestionsDatabaseUpdate.Modify(
            id,
            reexport = Some(fieldRemove)
          )
        }
        router.expectMsg(
          DeliverToJsonController(
            clientId,
            SearchProtocol.SuggestionsDatabaseUpdateNotification(
              updates1.size.toLong + 2,
              updates3
            )
          )
        )
    }

    "get initial suggestions database version" taggedAs Retry in withDb {
      (_, _, _, _, handler) =>
        handler ! SearchProtocol.GetSuggestionsDatabaseVersion

        expectMsg(SearchProtocol.GetSuggestionsDatabaseVersionResult(0))
    }

    "get suggestions database version" taggedAs Retry in withDb {
      (_, repo, _, _, handler) =>
        Await.ready(repo.insert(Suggestions.atom), Timeout)
        handler ! SearchProtocol.GetSuggestionsDatabaseVersion

        expectMsg(SearchProtocol.GetSuggestionsDatabaseVersionResult(1))
    }

    "get initial suggestions database" taggedAs Retry in withDb {
      (_, _, _, _, handler) =>
        handler ! SearchProtocol.GetSuggestionsDatabase

        expectMsg(SearchProtocol.GetSuggestionsDatabaseResult(0, Seq()))
    }

    "get suggestions database" taggedAs Retry in withDb {
      (_, repo, _, _, handler) =>
        Await.ready(repo.insert(Suggestions.atom), Timeout)
        handler ! SearchProtocol.GetSuggestionsDatabase

        expectMsg(
          SearchProtocol.GetSuggestionsDatabaseResult(
            1,
            Seq(SuggestionDatabaseEntry(1L, Suggestions.atom))
          )
        )
    }

    "invalidate suggestions database" taggedAs Retry in withDb {
      (_, repo, _, connector, handler) =>
        Await.ready(repo.insert(Suggestions.atom), Timeout)
        handler ! SearchProtocol.InvalidateSuggestionsDatabase

        connector.expectMsgClass(classOf[Api.Request]) match {
          case Api.Request(_, Api.InvalidateModulesIndexRequest()) =>
          case Api.Request(_, msg) =>
            fail(s"Runtime connector receive unexpected message: $msg")
        }
        connector.reply(Api.Response(Api.InvalidateModulesIndexResponse()))

        expectMsg(SearchProtocol.InvalidateSuggestionsDatabaseResult)
    }

    "rename module when renaming project" taggedAs Retry in withDb {
      (_, repo, router, _, handler) =>
        Await.ready(repo.insert(Suggestions.atom), Timeout)
        val clientId      = UUID.randomUUID()
        val newModuleName = "Vest"

        // acquire capability
        handler ! AcquireCapability(
          newJsonSession(clientId),
          CapabilityRegistration(ReceivesSuggestionsDatabaseUpdates())
        )
        expectMsg(CapabilityAcquired)

        handler ! ProjectNameChangedEvent("Test", newModuleName)

        router.expectMsg(
          DeliverToJsonController(
            clientId,
            SearchProtocol.SuggestionsDatabaseUpdateNotification(
              2,
              Seq(
                SearchProtocol.SuggestionsDatabaseUpdate.Modify(
                  id     = 1,
                  module = Some(fieldUpdate("Vest.Main"))
                )
              )
            )
          )
        )
    }

    "rename types when renaming project" taggedAs Retry in withDb {
      (_, repo, router, _, handler) =>
        val method = Suggestions.method.copy(
          selfType = "Test.MyType",
          arguments = Suggestions.method.arguments.map(arg =>
            arg.copy(reprType = "Test.MyType")
          )
        )
        Await.ready(repo.insert(method), Timeout)
        val clientId      = UUID.randomUUID()
        val newModuleName = "Vest"

        // acquire capability
        handler ! AcquireCapability(
          newJsonSession(clientId),
          CapabilityRegistration(ReceivesSuggestionsDatabaseUpdates())
        )
        expectMsg(CapabilityAcquired)

        handler ! ProjectNameChangedEvent("Test", newModuleName)

        router.expectMsg(
          DeliverToJsonController(
            clientId,
            SearchProtocol.SuggestionsDatabaseUpdateNotification(
              2,
              Seq(
                SearchProtocol.SuggestionsDatabaseUpdate.Modify(
                  id     = 1,
                  module = Some(fieldUpdate("Vest.Main"))
                ),
                SearchProtocol.SuggestionsDatabaseUpdate.Modify(
                  id       = 1,
                  selfType = Some(fieldUpdate("Vest.MyType"))
                ),
                SearchProtocol.SuggestionsDatabaseUpdate.Modify(
                  id = 1,
                  arguments = Some(
                    method.arguments.zipWithIndex.map { case (_, index) =>
                      SearchProtocol.SuggestionArgumentUpdate.Modify(
                        index    = index,
                        reprType = Some(fieldUpdate("Vest.MyType"))
                      )
                    }
                  )
                )
              )
            )
          )
        )
    }

    "search entries by empty search query" taggedAs Retry in withDb {
      (config, repo, _, _, handler) =>
        val (_, inserted) =
          Await.result(repo.insertAll(Suggestions.all), Timeout)
        handler ! SearchProtocol.Completion(
          file       = mkModulePath(config, "Foo", "Main.enso"),
          position   = Position(0, 0),
          selfType   = None,
          returnType = None,
          tags       = None
        )

        expectMsg(
          SearchProtocol.CompletionResult(
            Suggestions.all.length.toLong,
            Seq(
              inserted(0).get,
              inserted(1).get,
              inserted(7).get,
              inserted(5).get,
              inserted(6).get,
              inserted(2).get
            )
          )
        )
    }

    "search entries by self type" taggedAs Retry in withDb {
      (config, repo, _, _, handler) =>
        val (_, Seq(_, _, methodId, _, _, methodOnAnyId, _, _)) =
          Await.result(repo.insertAll(Suggestions.all), Timeout)
        handler ! SearchProtocol.Completion(
          file       = mkModulePath(config, "Main.enso"),
          position   = Position(0, 0),
          selfType   = Some("MyType"),
          returnType = None,
          tags       = None
        )

        expectMsg(
          SearchProtocol.CompletionResult(
            Suggestions.all.length.toLong,
            Seq(methodId, methodOnAnyId).flatten
          )
        )
    }

    "search entries based on supertypes of self" taggedAs Retry in withDb {
      (config, repo, _, _, handler) =>
        val (
          _,
          Seq(_, _, _, _, _, anyMethodId, numberMethodId, integerMethodId)
        ) =
          Await.result(repo.insertAll(Suggestions.all), Timeout)

        handler ! SearchProtocol.Completion(
          file       = mkModulePath(config, "Main.enso"),
          position   = Position(0, 0),
          selfType   = Some("Integer"),
          returnType = None,
          tags       = None
        )

        expectMsg(
          SearchProtocol.CompletionResult(
            Suggestions.all.length.toLong,
            Seq(integerMethodId, numberMethodId, anyMethodId).flatten
          )
        )
    }

    "search entries for any" taggedAs Retry in withDb {
      (config, repo, _, _, handler) =>
        val (_, Seq(_, _, _, _, _, anyMethodId, _, _)) =
          Await.result(repo.insertAll(Suggestions.all), Timeout)

        handler ! SearchProtocol.Completion(
          file       = mkModulePath(config, "Main.enso"),
          position   = Position(0, 0),
          selfType   = Some("Any"),
          returnType = None,
          tags       = None
        )

        expectMsg(
          SearchProtocol.CompletionResult(
            Suggestions.all.length.toLong,
            Seq(anyMethodId).flatten
          )
        )
    }

    "search entries by return type" taggedAs Retry in withDb {
      (config, repo, _, _, handler) =>
        val (_, Seq(_, _, _, functionId, _, _, _, _)) =
          Await.result(repo.insertAll(Suggestions.all), Timeout)
        handler ! SearchProtocol.Completion(
          file       = mkModulePath(config, "Main.enso"),
          position   = Position(1, 10),
          selfType   = None,
          returnType = Some("IO"),
          tags       = None
        )

        expectMsg(
          SearchProtocol.CompletionResult(
            Suggestions.all.length.toLong,
            Seq(functionId).flatten
          )
        )
    }

    "search entries by tags" taggedAs Retry in withDb {
      (config, repo, _, _, handler) =>
        val (_, Seq(_, _, _, _, localId, _, _, _)) =
          Await.result(repo.insertAll(Suggestions.all), Timeout)
        handler ! SearchProtocol.Completion(
          file       = mkModulePath(config, "Main.enso"),
          position   = Position(42, 0),
          selfType   = None,
          returnType = None,
          tags       = Some(Seq(SearchProtocol.SuggestionKind.Local))
        )

        expectMsg(
          SearchProtocol.CompletionResult(
            Suggestions.all.length.toLong,
            Seq(localId).flatten
          )
        )
    }
  }

  private def fieldUpdate(value: String): SearchProtocol.FieldUpdate[String] =
    SearchProtocol.FieldUpdate(SearchProtocol.FieldAction.Set, Some(value))

  private def fieldRemove[A]: SearchProtocol.FieldUpdate[A] =
    SearchProtocol.FieldUpdate(SearchProtocol.FieldAction.Remove, None)

  def newSuggestionsHandler(
    config: Config,
    sessionRouter: TestProbe,
    runtimeConnector: TestProbe,
    suggestionsRepo: SuggestionsRepo[Future],
    fileVersionsRepo: VersionsRepo[Future]
  ): ActorRef = {
    val contentRootManagerActor =
      system.actorOf(ContentRootManagerActor.props(config))
    val contentRootManagerWrapper: ContentRootManager =
      new ContentRootManagerWrapper(config, contentRootManagerActor)
    system.actorOf(
      SuggestionsHandler.props(
        config,
        contentRootManagerWrapper,
        suggestionsRepo,
        fileVersionsRepo,
        sessionRouter.ref,
        runtimeConnector.ref
      )
    )
  }

  def newInitializedSuggestionsHandler(
    config: Config,
    sessionRouter: TestProbe,
    runtimeConnector: TestProbe,
    suggestionsRepo: SuggestionsRepo[Future],
    fileVersionsRepo: VersionsRepo[Future]
  ): ActorRef = {
    val handler =
      newSuggestionsHandler(
        config,
        sessionRouter,
        runtimeConnector,
        suggestionsRepo,
        fileVersionsRepo
      )
    handler ! SuggestionsHandler.ProjectNameUpdated("Test")
    handler ! InitializedEvent.TruffleContextInitialized
    runtimeConnector.receiveN(1)
    handler ! Api.Response(
      UUID.randomUUID(),
      Api.GetTypeGraphResponse(buildTestTypeGraph)
    )
    runtimeConnector.receiveN(1)
    handler ! Api.Response(
      UUID.randomUUID(),
      Api.VerifyModulesIndexResponse(Seq())
    )
    handler
  }

  def buildTestTypeGraph: TypeGraph = {
    val graph = TypeGraph("Any")
    graph.insert("Number", "Any")
    graph.insert("Integer", "Number")

    graph
  }

  def newConfig(root: ContentRootWithFile): Config = {
    Config(
      root,
      FileManagerConfig(timeout = 3.seconds),
      PathWatcherConfig(),
      ExecutionContextConfig(requestTimeout = 3.seconds),
      ProjectDirectoriesConfig.initialize(root.file)
    )
  }

  def mkModulePath(config: Config, segments: String*): Path = {
    val rootId = config.projectContentRoot.id
    Path(rootId, "src" +: segments.toVector)
  }

  def newJsonSession(clientId: UUID): JsonSession =
    JsonSession(clientId, TestProbe().ref)

  def withDbs(
    test: (Config, SuggestionsRepo[Future], VersionsRepo[Future]) => Any
  ): Unit = {
    val testContentRoot = Files.createTempDirectory(null).toRealPath()
    sys.addShutdownHook(FileUtils.deleteQuietly(testContentRoot.toFile))
    val config = newConfig(
      ContentRootWithFile(
        ContentRoot.Project(UUID.randomUUID()),
        testContentRoot.toFile
      )
    )
    val sqlDatabase     = SqlDatabase(config.directories.suggestionsDatabaseFile)
    val suggestionsRepo = new SqlSuggestionsRepo(sqlDatabase)
    val versionsRepo    = new SqlVersionsRepo(sqlDatabase)

    val suggestionsInit = suggestionsRepo.init
    val versionsInit    = versionsRepo.init
    suggestionsInit.onComplete {
      case Success(()) =>
        system.eventStream.publish(InitializedEvent.SuggestionsRepoInitialized)
      case Failure(ex) =>
        system.log.error(ex, "Failed to initialize Suggestions repo")
    }
    versionsInit.onComplete {
      case Success(()) =>
        system.eventStream.publish(InitializedEvent.FileVersionsRepoInitialized)
      case Failure(ex) =>
        system.log.error(ex, "Failed to initialize FileVersions repo")
    }

    Await.ready(suggestionsInit, Timeout)
    Await.ready(versionsInit, Timeout)

    try test(config, suggestionsRepo, versionsRepo)
    finally {
      versionsRepo.close()
      suggestionsRepo.close()
    }
  }

  def withHandler(
    config: Config,
    suggestionsRepo: SuggestionsRepo[Future],
    versionsRepo: VersionsRepo[Future]
  )(
    test: (TestProbe, TestProbe, ActorRef) => Any
  ): Unit = {
    val router    = TestProbe("session-router")
    val connector = TestProbe("runtime-connector")
    val handler = newSuggestionsHandler(
      config,
      router,
      connector,
      suggestionsRepo,
      versionsRepo
    )

    try test(router, connector, handler)
    finally {
      system.stop(handler)
    }
  }

  def withDb(
    test: (
      Config,
      SuggestionsRepo[Future],
      TestProbe,
      TestProbe,
      ActorRef
    ) => Any
  ): Unit = {
    val testContentRoot = Files.createTempDirectory(null).toRealPath()
    sys.addShutdownHook(FileUtils.deleteQuietly(testContentRoot.toFile))
    val config = newConfig(
      ContentRootWithFile(
        ContentRoot.Project(UUID.randomUUID()),
        testContentRoot.toFile
      )
    )
    val router          = TestProbe("session-router")
    val connector       = TestProbe("runtime-connector")
    val sqlDatabase     = SqlDatabase(config.directories.suggestionsDatabaseFile)
    val suggestionsRepo = new SqlSuggestionsRepo(sqlDatabase)
    val versionsRepo    = new SqlVersionsRepo(sqlDatabase)

    suggestionsRepo.init.onComplete {
      case Success(()) =>
        system.eventStream.publish(InitializedEvent.SuggestionsRepoInitialized)
      case Failure(ex) =>
        system.log.error(ex, "Failed to initialize Suggestions repo")
    }
    versionsRepo.init.onComplete {
      case Success(()) =>
        system.eventStream.publish(InitializedEvent.FileVersionsRepoInitialized)
      case Failure(ex) =>
        system.log.error(ex, "Failed to initialize FileVersions repo")
    }

    val handler = newInitializedSuggestionsHandler(
      config,
      router,
      connector,
      suggestionsRepo,
      versionsRepo
    )

    try test(config, suggestionsRepo, router, connector, handler)
    finally {
      system.stop(handler)
      sqlDatabase.close()
    }
  }

  object TestSuggestion {

    val atom: Suggestion.Atom =
      Suggestion.Atom(
        externalId = None,
        module     = "Test.Pair",
        name       = "Pair",
        arguments = Seq(
          Suggestion.Argument("a", "Any", false, false, None),
          Suggestion.Argument("b", "Any", false, false, None)
        ),
        returnType        = "Pair",
        documentation     = Some("Awesome"),
        documentationHtml = Some("")
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
        documentationHtml = None
      )
  }

}
