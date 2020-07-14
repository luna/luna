package org.enso.languageserver.runtime

import java.nio.file.Files
import java.util.UUID

import akka.actor.{ActorRef, ActorSystem}
import akka.testkit.{ImplicitSender, TestKit, TestProbe}
import org.enso.jsonrpc.test.RetrySpec
import org.enso.languageserver.capability.CapabilityProtocol.{
  AcquireCapability,
  CapabilityAcquired
}
import org.enso.languageserver.data.{
  CapabilityRegistration,
  Config,
  ExecutionContextConfig,
  FileManagerConfig,
  PathWatcherConfig,
  ReceivesSuggestionsDatabaseUpdates
}
import org.enso.languageserver.filemanager.Path
import org.enso.languageserver.refactoring.ProjectNameChangedEvent
import org.enso.languageserver.session.JsonSession
import org.enso.languageserver.session.SessionRouter.DeliverToJsonController
import org.enso.polyglot.runtime.Runtime.Api
import org.enso.searcher.SuggestionsRepo
import org.enso.searcher.sql.SqlSuggestionsRepo
import org.enso.text.editing.model.Position
import org.scalatest.BeforeAndAfterAll
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpecLike

import scala.concurrent.duration._
import scala.concurrent.{Await, Future}

class SuggestionsHandlerSpec
    extends TestKit(ActorSystem("TestSystem"))
    with ImplicitSender
    with AnyWordSpecLike
    with Matchers
    with BeforeAndAfterAll
    with RetrySpec {

  import system.dispatcher

  val Timeout: FiniteDuration = 10.seconds

  override def afterAll(): Unit = {
    TestKit.shutdownActorSystem(system)
  }

  "SuggestionsHandler" should {

    "subscribe to notification updates" taggedAs Retry() in withDb {
      (_, _, _, handler) =>
        val clientId = UUID.randomUUID()

        handler ! AcquireCapability(
          newJsonSession(clientId),
          CapabilityRegistration(ReceivesSuggestionsDatabaseUpdates())
        )
        expectMsg(CapabilityAcquired)
    }

    "receive runtime updates" taggedAs Retry() in withDb {
      (_, repo, router, handler) =>
        val clientId = UUID.randomUUID()

        // acquire capability
        handler ! AcquireCapability(
          newJsonSession(clientId),
          CapabilityRegistration(ReceivesSuggestionsDatabaseUpdates())
        )
        expectMsg(CapabilityAcquired)

        // receive updates
        handler ! Api.SuggestionsDatabaseUpdateNotification(
          Suggestions.all.map(Api.SuggestionsDatabaseUpdate.Add)
        )

        val updates = Suggestions.all.zipWithIndex.map {
          case (suggestion, ix) =>
            SearchProtocol.SuggestionsDatabaseUpdate.Add(ix + 1L, suggestion)
        }
        router.expectMsg(
          DeliverToJsonController(
            clientId,
            SearchProtocol.SuggestionsDatabaseUpdateNotification(updates, 4L)
          )
        )

        // check database entries exist
        val (_, records) = Await.result(repo.getAll, Timeout)
        records.map(
          _.suggestion
        ) should contain theSameElementsAs Suggestions.all
    }

    "apply runtime updates in correct order" taggedAs Retry() in withDb {
      (_, repo, router, handler) =>
        val clientId = UUID.randomUUID()

        // acquire capability
        handler ! AcquireCapability(
          newJsonSession(clientId),
          CapabilityRegistration(ReceivesSuggestionsDatabaseUpdates())
        )
        expectMsg(CapabilityAcquired)

        // receive updates
        handler ! Api.SuggestionsDatabaseUpdateNotification(
          Suggestions.all.map(Api.SuggestionsDatabaseUpdate.Add) ++
          Suggestions.all.map(Api.SuggestionsDatabaseUpdate.Remove)
        )

        val updates = Suggestions.all.zipWithIndex.map {
          case (suggestion, ix) =>
            SearchProtocol.SuggestionsDatabaseUpdate.Add(ix + 1L, suggestion)
        }
        router.expectMsg(
          DeliverToJsonController(
            clientId,
            SearchProtocol.SuggestionsDatabaseUpdateNotification(updates, 4L)
          )
        )

        // check that database entries removed
        val (_, all) = Await.result(repo.getAll, Timeout)
        all.map(_.suggestion) should contain theSameElementsAs Suggestions.all
    }

    "get initial suggestions database version" taggedAs Retry() in withDb {
      (_, _, _, handler) =>
        handler ! SearchProtocol.GetSuggestionsDatabaseVersion

        expectMsg(SearchProtocol.GetSuggestionsDatabaseVersionResult(0))
    }

    "get suggestions database version" taggedAs Retry() in withDb {
      (_, repo, _, handler) =>
        Await.ready(repo.insert(Suggestions.atom), Timeout)
        handler ! SearchProtocol.GetSuggestionsDatabaseVersion

        expectMsg(SearchProtocol.GetSuggestionsDatabaseVersionResult(1))
    }

    "get initial suggestions database" taggedAs Retry() in withDb {
      (_, _, _, handler) =>
        handler ! SearchProtocol.GetSuggestionsDatabase

        expectMsg(SearchProtocol.GetSuggestionsDatabaseResult(Seq(), 0))
    }

    "get suggestions database" taggedAs Retry() in withDb {
      (_, repo, _, handler) =>
        Await.ready(repo.insert(Suggestions.atom), Timeout)
        handler ! SearchProtocol.GetSuggestionsDatabase

        expectMsg(
          SearchProtocol.GetSuggestionsDatabaseResult(
            Seq(
              SearchProtocol.SuggestionsDatabaseUpdate.Add(1L, Suggestions.atom)
            ),
            1
          )
        )
    }

    "search entries by empty search query" taggedAs Retry() in withDb {
      (config, repo, _, handler) =>
        Await.ready(repo.insertAll(Suggestions.all), Timeout)
        handler ! SearchProtocol.Completion(
          file       = mkModulePath(config, "Foo", "Main.enso"),
          position   = Position(0, 0),
          selfType   = None,
          returnType = None,
          tags       = None
        )

        expectMsg(SearchProtocol.CompletionResult(4L, Seq()))
    }

    "search entries by self type" taggedAs Retry() in withDb {
      (config, repo, _, handler) =>
        val (_, Seq(_, methodId, _, _)) =
          Await.result(repo.insertAll(Suggestions.all), Timeout)
        handler ! SearchProtocol.Completion(
          file       = mkModulePath(config, "Main.enso"),
          position   = Position(0, 0),
          selfType   = Some("MyType"),
          returnType = None,
          tags       = None
        )

        expectMsg(SearchProtocol.CompletionResult(4L, Seq(methodId).flatten))
    }

    "search entries by return type" taggedAs Retry() in withDb {
      (config, repo, _, handler) =>
        val (_, Seq(_, _, functionId, _)) =
          Await.result(repo.insertAll(Suggestions.all), Timeout)
        handler ! SearchProtocol.Completion(
          file       = mkModulePath(config, "Main.enso"),
          position   = Position(1, 10),
          selfType   = None,
          returnType = Some("IO"),
          tags       = None
        )

        expectMsg(SearchProtocol.CompletionResult(4L, Seq(functionId).flatten))
    }

    "search entries by tags" taggedAs Retry() in withDb {
      (config, repo, _, handler) =>
        val (_, Seq(_, _, _, localId)) =
          Await.result(repo.insertAll(Suggestions.all), Timeout)
        handler ! SearchProtocol.Completion(
          file       = mkModulePath(config, "Main.enso"),
          position   = Position(42, 0),
          selfType   = None,
          returnType = None,
          tags       = Some(Seq(SearchProtocol.SuggestionKind.Local))
        )

        expectMsg(SearchProtocol.CompletionResult(4L, Seq(localId).flatten))
    }
  }

  def newSuggestionsHandler(
    sessionRouter: TestProbe,
    repo: SuggestionsRepo[Future]
  ): (ActorRef, Config) = {
    val testContentRoot = Files.createTempDirectory(null).toRealPath()
    testContentRoot.toFile.deleteOnExit()
    val testContentRootId = UUID.randomUUID()
    val config = Config(
      Map(testContentRootId -> testContentRoot.toFile),
      FileManagerConfig(timeout = 3.seconds),
      PathWatcherConfig(),
      ExecutionContextConfig(requestTimeout = 3.seconds)
    )
    val handler =
      system.actorOf(SuggestionsHandler.props(config, repo, sessionRouter.ref))
    handler ! ProjectNameChangedEvent("Test")
    (handler, config)
  }

  def mkModulePath(config: Config, segments: String*): Path = {
    val (rootId, _) = config.contentRoots.head
    Path(rootId, "src" +: segments.toVector)
  }

  def newJsonSession(clientId: UUID): JsonSession =
    JsonSession(clientId, TestProbe().ref)

  def withDb(
    test: (Config, SuggestionsRepo[Future], TestProbe, ActorRef) => Any
  ): Unit = {
    val dbPath = Files.createTempFile("suggestions", ".db")
    system.registerOnTermination(Files.deleteIfExists(dbPath))
    val router            = TestProbe("session-router")
    val repo              = SqlSuggestionsRepo()
    val (handler, config) = newSuggestionsHandler(router, repo)
    Await.ready(repo.init, Timeout)

    try test(config, repo, router, handler)
    finally {
      system.stop(handler)
      repo.close()
    }
  }

}
