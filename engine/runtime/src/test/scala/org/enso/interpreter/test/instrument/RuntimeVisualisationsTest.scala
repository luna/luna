package org.enso.interpreter.test.instrument

import java.io.{ByteArrayOutputStream, File}
import java.nio.ByteBuffer
import java.nio.file.{Files, Paths}
import java.util.UUID
import java.util.concurrent.{LinkedBlockingQueue, TimeUnit}
import org.enso.interpreter.instrument.execution.Timer
import org.enso.interpreter.runtime.`type`.Constants
import org.enso.interpreter.runtime.{Context => EnsoContext}
import org.enso.interpreter.test.Metadata
import org.enso.pkg.{Package, PackageManager}
import org.enso.polyglot._
import org.enso.polyglot.runtime.Runtime.Api
import org.enso.text.editing.model
import org.enso.text.editing.model.TextEdit
import org.graalvm.polyglot.Context
import org.graalvm.polyglot.io.MessageEndpoint
import org.scalatest.BeforeAndAfterEach
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

@scala.annotation.nowarn("msg=multiarg infix syntax")
class RuntimeVisualisationsTest
    extends AnyFlatSpec
    with Matchers
    with BeforeAndAfterEach {

  // === Test Timer ===========================================================

  class TestTimer extends Timer {
    override def getTime(): Long = 0
  }

  // === Test Utilities =======================================================

  var context: TestContext = _

  class TestContext(packageName: String) {
    var endPoint: MessageEndpoint = _
    val messageQueue: LinkedBlockingQueue[Api.Response] =
      new LinkedBlockingQueue()

    val tmpDir: File = Files.createTempDirectory("enso-test-packages").toFile

    val pkg: Package[File] =
      PackageManager.Default.create(tmpDir, packageName, "Enso_Test")
    val out: ByteArrayOutputStream    = new ByteArrayOutputStream()
    val logOut: ByteArrayOutputStream = new ByteArrayOutputStream()
    val executionContext = new PolyglotContext(
      Context
        .newBuilder(LanguageInfo.ID)
        .allowExperimentalOptions(true)
        .allowAllAccess(true)
        .option(RuntimeOptions.PROJECT_ROOT, pkg.root.getAbsolutePath)
        .option(RuntimeOptions.LOG_LEVEL, "WARNING")
        .option(RuntimeOptions.INTERPRETER_SEQUENTIAL_COMMAND_EXECUTION, "true")
        .option(RuntimeOptions.ENABLE_PROJECT_SUGGESTIONS, "false")
        .option(RuntimeOptions.ENABLE_GLOBAL_SUGGESTIONS, "false")
        .option(RuntimeServerInfo.ENABLE_OPTION, "true")
        .option(RuntimeOptions.INTERACTIVE_MODE, "true")
        .option(
          RuntimeOptions.LANGUAGE_HOME_OVERRIDE,
          Paths.get("../../distribution/component").toFile.getAbsolutePath
        )
        .logHandler(logOut)
        .out(out)
        .serverTransport { (uri, peer) =>
          if (uri.toString == RuntimeServerInfo.URI) {
            endPoint = peer
            new MessageEndpoint {
              override def sendText(text: String): Unit = {}

              override def sendBinary(data: ByteBuffer): Unit =
                Api.deserializeResponse(data).foreach(messageQueue.add)

              override def sendPing(data: ByteBuffer): Unit = {}

              override def sendPong(data: ByteBuffer): Unit = {}

              override def sendClose(): Unit = {}
            }
          } else null
        }
        .build()
    )
    executionContext.context.initialize(LanguageInfo.ID)

    val languageContext = executionContext.context
      .getBindings(LanguageInfo.ID)
      .invokeMember(MethodNames.TopScope.LEAK_CONTEXT)
      .asHostObject[EnsoContext]
    languageContext.getLanguage.getIdExecutionInstrument
      .overrideTimer(new TestTimer)

    def writeMain(contents: String): File =
      Files.write(pkg.mainFile.toPath, contents.getBytes).toFile

    def writeFile(file: File, contents: String): File =
      Files.write(file.toPath, contents.getBytes).toFile

    def writeInSrcDir(moduleName: String, contents: String): File = {
      val file = new File(pkg.sourceDir, s"$moduleName.enso")
      Files.write(file.toPath, contents.getBytes).toFile
    }

    def send(msg: Api.Request): Unit = endPoint.sendBinary(Api.serialize(msg))

    def receiveNone: Option[Api.Response] = {
      Option(messageQueue.poll())
    }

    def receive: Option[Api.Response] =
      receiveTimeout(10)

    def receiveTimeout(timeoutSeconds: Int): Option[Api.Response] =
      Option(messageQueue.poll(timeoutSeconds.toLong, TimeUnit.SECONDS))

    def receive(n: Int, timeoutSeconds: Int = 10): List[Api.Response] =
      Iterator
        .continually(receiveTimeout(timeoutSeconds))
        .take(n)
        .flatten
        .toList

    def consumeOut: List[String] = {
      val result = out.toString
      out.reset()
      result.linesIterator.toList
    }

    def executionComplete(contextId: UUID): Api.Response =
      Api.Response(Api.ExecutionComplete(contextId))

    // === The Tests ==========================================================

    object Main {

      val metadata = new Metadata

      val idMainX = metadata.addItem(51, 1)
      val idMainY = metadata.addItem(61, 7)
      val idMainZ = metadata.addItem(77, 5)
      val idFooY  = metadata.addItem(116, 8)
      val idFooZ  = metadata.addItem(133, 5)

      def code =
        metadata.appendToCode(
          """
            |from Standard.Builtins import all
            |
            |main =
            |    x = 6
            |    y = x.foo 5
            |    z = y + 5
            |    z
            |
            |Number.foo = x ->
            |    y = this + 3
            |    z = y * x
            |    z
            |""".stripMargin.linesIterator.mkString("\n")
        )

      object Update {

        def mainX(contextId: UUID, fromCache: Boolean = false): Api.Response =
          Api.Response(
            Api.ExpressionUpdates(
              contextId,
              Set(
                Api.ExpressionUpdate(
                  Main.idMainX,
                  Some(Constants.INTEGER),
                  None,
                  Vector(Api.ProfilingInfo.ExecutionTime(0)),
                  fromCache,
                  Api.ExpressionUpdate.Payload.Value()
                )
              )
            )
          )

        def mainY(contextId: UUID, fromCache: Boolean = false): Api.Response =
          Api.Response(
            Api.ExpressionUpdates(
              contextId,
              Set(
                Api.ExpressionUpdate(
                  Main.idMainY,
                  Some(Constants.INTEGER),
                  Some(
                    Api.MethodPointer(
                      "Enso_Test.Test.Main",
                      Constants.NUMBER,
                      "foo"
                    )
                  ),
                  Vector(Api.ProfilingInfo.ExecutionTime(0)),
                  fromCache,
                  Api.ExpressionUpdate.Payload.Value()
                )
              )
            )
          )

        def mainZ(contextId: UUID, fromCache: Boolean = false): Api.Response =
          Api.Response(
            Api.ExpressionUpdates(
              contextId,
              Set(
                Api.ExpressionUpdate(
                  Main.idMainZ,
                  Some(Constants.INTEGER),
                  None,
                  Vector(Api.ProfilingInfo.ExecutionTime(0)),
                  fromCache,
                  Api.ExpressionUpdate.Payload.Value()
                )
              )
            )
          )

        def fooY(contextId: UUID, fromCache: Boolean = false): Api.Response =
          Api.Response(
            Api.ExpressionUpdates(
              contextId,
              Set(
                Api.ExpressionUpdate(
                  Main.idFooY,
                  Some(Constants.INTEGER),
                  None,
                  Vector(Api.ProfilingInfo.ExecutionTime(0)),
                  fromCache,
                  Api.ExpressionUpdate.Payload.Value()
                )
              )
            )
          )

        def fooZ(contextId: UUID, fromCache: Boolean = false): Api.Response =
          Api.Response(
            Api.ExpressionUpdates(
              contextId,
              Set(
                Api.ExpressionUpdate(
                  Main.idFooZ,
                  Some(Constants.INTEGER),
                  None,
                  Vector(Api.ProfilingInfo.ExecutionTime(0)),
                  fromCache,
                  Api.ExpressionUpdate.Payload.Value()
                )
              )
            )
          )
      }
    }

    object Visualisation {

      val code =
        """
          |encode = x -> x.to_text
          |
          |incAndEncode = x -> here.encode x+1
          |
          |""".stripMargin

    }

  }

  override protected def beforeEach(): Unit = {
    context = new TestContext("Test")
    val Some(Api.Response(_, Api.InitializedNotification())) = context.receive
  }

  it should "emit visualisation update when expression is computed" in {
    val idMain     = context.Main.metadata.addItem(87, 1)
    val contents   = context.Main.code
    val mainFile   = context.writeMain(context.Main.code)
    val moduleName = "Enso_Test.Test.Main"
    val visualisationFile =
      context.writeInSrcDir("Visualisation", context.Visualisation.code)

    context.send(
      Api.Request(
        Api.OpenFileNotification(
          visualisationFile,
          context.Visualisation.code
        )
      )
    )

    val contextId       = UUID.randomUUID()
    val requestId       = UUID.randomUUID()
    val visualisationId = UUID.randomUUID()

    // create context
    context.send(Api.Request(requestId, Api.CreateContextRequest(contextId)))
    context.receive shouldEqual Some(
      Api.Response(requestId, Api.CreateContextResponse(contextId))
    )

    // Open the new file
    context.send(
      Api.Request(Api.OpenFileNotification(mainFile, contents))
    )
    context.receiveNone shouldEqual None

    // push main
    val item1 = Api.StackItem.ExplicitCall(
      Api.MethodPointer(moduleName, "Enso_Test.Test.Main", "main"),
      None,
      Vector()
    )
    context.send(
      Api.Request(requestId, Api.PushContextRequest(contextId, item1))
    )
    context.receive(6) should contain theSameElementsAs Seq(
      Api.Response(requestId, Api.PushContextResponse(contextId)),
      context.Main.Update.mainX(contextId),
      context.Main.Update.mainY(contextId),
      context.Main.Update.mainZ(contextId),
      TestMessages.update(contextId, idMain, Constants.INTEGER),
      context.executionComplete(contextId)
    )

    // attach visualisation
    context.send(
      Api.Request(
        requestId,
        Api.AttachVisualisation(
          visualisationId,
          idMain,
          Api.VisualisationConfiguration(
            contextId,
            "Enso_Test.Test.Visualisation",
            "x -> here.encode x"
          )
        )
      )
    )
    val attachVisualisationResponses = context.receive(3)
    attachVisualisationResponses should contain allOf (
      Api.Response(requestId, Api.VisualisationAttached()),
      context.executionComplete(contextId)
    )
    val Some(data) = attachVisualisationResponses.collectFirst {
      case Api.Response(
            None,
            Api.VisualisationUpdate(
              Api.VisualisationContext(
                `visualisationId`,
                `contextId`,
                `idMain`
              ),
              data
            )
          ) =>
        data
    }
    data.sameElements("50".getBytes) shouldBe true

    // recompute
    context.send(
      Api.Request(requestId, Api.RecomputeContextRequest(contextId, None))
    )

    val recomputeResponses = context.receive(3)
    recomputeResponses should contain allOf (
      Api.Response(requestId, Api.RecomputeContextResponse(contextId)),
      context.executionComplete(contextId)
    )
    val Some(data2) = recomputeResponses.collectFirst {
      case Api.Response(
            None,
            Api.VisualisationUpdate(
              Api.VisualisationContext(
                `visualisationId`,
                `contextId`,
                `idMain`
              ),
              data
            )
          ) =>
        data
    }
    data2.sameElements("50".getBytes) shouldBe true
  }

  it should "emit visualisation update when expression is cached" in {
    val contents   = context.Main.code
    val mainFile   = context.writeMain(context.Main.code)
    val moduleName = "Enso_Test.Test.Main"
    val visualisationFile =
      context.writeInSrcDir("Visualisation", context.Visualisation.code)

    context.send(
      Api.Request(
        Api.OpenFileNotification(
          visualisationFile,
          context.Visualisation.code
        )
      )
    )

    val contextId       = UUID.randomUUID()
    val requestId       = UUID.randomUUID()
    val visualisationId = UUID.randomUUID()

    // create context
    context.send(Api.Request(requestId, Api.CreateContextRequest(contextId)))
    context.receive shouldEqual Some(
      Api.Response(requestId, Api.CreateContextResponse(contextId))
    )

    // Open the new file
    context.send(
      Api.Request(Api.OpenFileNotification(mainFile, contents))
    )
    context.receiveNone shouldEqual None

    // push main
    val item1 = Api.StackItem.ExplicitCall(
      Api.MethodPointer(moduleName, "Main", "main"),
      None,
      Vector()
    )
    context.send(
      Api.Request(requestId, Api.PushContextRequest(contextId, item1))
    )
    context.receive(5) should contain theSameElementsAs Seq(
      Api.Response(requestId, Api.PushContextResponse(contextId)),
      context.Main.Update.mainX(contextId),
      context.Main.Update.mainY(contextId),
      context.Main.Update.mainZ(contextId),
      context.executionComplete(contextId)
    )

    // attach visualisation
    context.send(
      Api.Request(
        requestId,
        Api.AttachVisualisation(
          visualisationId,
          context.Main.idMainX,
          Api.VisualisationConfiguration(
            contextId,
            "Enso_Test.Test.Visualisation",
            "x -> here.encode x"
          )
        )
      )
    )
    val attachVisualisationResponses = context.receive(2)
    attachVisualisationResponses should contain(
      Api.Response(requestId, Api.VisualisationAttached())
    )
    val expectedExpressionId = context.Main.idMainX
    val Some(data) = attachVisualisationResponses.collectFirst {
      case Api.Response(
            None,
            Api.VisualisationUpdate(
              Api.VisualisationContext(
                `visualisationId`,
                `contextId`,
                `expectedExpressionId`
              ),
              data
            )
          ) =>
        data
    }
    data.sameElements("6".getBytes) shouldBe true

    // recompute
    context.send(
      Api.Request(requestId, Api.RecomputeContextRequest(contextId, None))
    )
    context.receive(2) should contain allOf (
      Api.Response(requestId, Api.RecomputeContextResponse(contextId)),
      context.executionComplete(contextId)
    )

    // recompute invalidating x
    context.send(
      Api.Request(
        requestId,
        Api.RecomputeContextRequest(
          contextId,
          Some(
            Api.InvalidatedExpressions.Expressions(Vector(context.Main.idMainX))
          )
        )
      )
    )
    val recomputeResponses2 = context.receive(3)
    recomputeResponses2 should contain allOf (
      Api.Response(requestId, Api.RecomputeContextResponse(contextId)),
      context.executionComplete(contextId)
    )
    val Some(data2) = recomputeResponses2.collectFirst {
      case Api.Response(
            None,
            Api.VisualisationUpdate(
              Api.VisualisationContext(
                `visualisationId`,
                `contextId`,
                `expectedExpressionId`
              ),
              data
            )
          ) =>
        data
    }
    data2.sameElements("6".getBytes) shouldBe true
  }

  it should "emit visualisation update when expression is modified" in {
    val contents   = context.Main.code
    val moduleName = "Enso_Test.Test.Main"
    val mainFile   = context.writeMain(contents)
    val visualisationFile =
      context.writeInSrcDir("Visualisation", context.Visualisation.code)

    val contextId       = UUID.randomUUID()
    val requestId       = UUID.randomUUID()
    val visualisationId = UUID.randomUUID()

    // open files
    context.send(
      Api.Request(
        Api.OpenFileNotification(
          visualisationFile,
          context.Visualisation.code
        )
      )
    )
    context.receiveNone shouldEqual None
    context.send(
      Api.Request(Api.OpenFileNotification(mainFile, contents))
    )
    context.receiveNone shouldEqual None

    // create context
    context.send(Api.Request(requestId, Api.CreateContextRequest(contextId)))
    context.receive shouldEqual Some(
      Api.Response(requestId, Api.CreateContextResponse(contextId))
    )

    // push main
    val item1 = Api.StackItem.ExplicitCall(
      Api.MethodPointer(moduleName, "Enso_Test.Test.Main", "main"),
      None,
      Vector()
    )
    context.send(
      Api.Request(requestId, Api.PushContextRequest(contextId, item1))
    )

    context.receive(5) should contain theSameElementsAs Seq(
      Api.Response(requestId, Api.PushContextResponse(contextId)),
      context.Main.Update.mainX(contextId),
      context.Main.Update.mainY(contextId),
      context.Main.Update.mainZ(contextId),
      context.executionComplete(contextId)
    )

    // attach visualization
    context.send(
      Api.Request(
        requestId,
        Api.AttachVisualisation(
          visualisationId,
          context.Main.idMainX,
          Api.VisualisationConfiguration(
            contextId,
            "Enso_Test.Test.Visualisation",
            "x -> here.encode x"
          )
        )
      )
    )
    val attachVisualisationResponses = context.receive(2)
    attachVisualisationResponses should contain(
      Api.Response(requestId, Api.VisualisationAttached())
    )
    val expectedExpressionId = context.Main.idMainX
    val Some(data) = attachVisualisationResponses.collectFirst {
      case Api.Response(
            None,
            Api.VisualisationUpdate(
              Api.VisualisationContext(
                `visualisationId`,
                `contextId`,
                `expectedExpressionId`
              ),
              data
            )
          ) =>
        data
    }
    data.sameElements("6".getBytes) shouldBe true

    // Modify the file
    context.send(
      Api.Request(
        Api.EditFileNotification(
          mainFile,
          Seq(
            TextEdit(
              model.Range(model.Position(4, 8), model.Position(4, 9)),
              "5"
            )
          )
        )
      )
    )

    val editFileResponse = context.receive(2)
    editFileResponse should contain(
      context.executionComplete(contextId)
    )
    val Some(data1) = editFileResponse.collectFirst {
      case Api.Response(
            None,
            Api.VisualisationUpdate(
              Api.VisualisationContext(
                `visualisationId`,
                `contextId`,
                `expectedExpressionId`
              ),
              data
            )
          ) =>
        data
    }
    data1.sameElements("5".getBytes) shouldBe true
  }

  it should "emit visualisation update when transitive expression is modified" in {
    val contents   = context.Main.code
    val moduleName = "Enso_Test.Test.Main"
    val mainFile   = context.writeMain(contents)
    val visualisationFile =
      context.writeInSrcDir("Visualisation", context.Visualisation.code)

    val contextId       = UUID.randomUUID()
    val requestId       = UUID.randomUUID()
    val visualisationId = UUID.randomUUID()

    // open files
    context.send(
      Api.Request(
        Api.OpenFileNotification(
          visualisationFile,
          context.Visualisation.code
        )
      )
    )
    context.receiveNone shouldEqual None
    context.send(
      Api.Request(Api.OpenFileNotification(mainFile, contents))
    )
    context.receiveNone shouldEqual None

    // create context
    context.send(Api.Request(requestId, Api.CreateContextRequest(contextId)))
    context.receive shouldEqual Some(
      Api.Response(requestId, Api.CreateContextResponse(contextId))
    )

    // push main
    val item1 = Api.StackItem.ExplicitCall(
      Api.MethodPointer(moduleName, "Enso_Test.Test.Main", "main"),
      None,
      Vector()
    )
    context.send(
      Api.Request(requestId, Api.PushContextRequest(contextId, item1))
    )

    context.receive(5) should contain theSameElementsAs Seq(
      Api.Response(requestId, Api.PushContextResponse(contextId)),
      context.Main.Update.mainX(contextId),
      context.Main.Update.mainY(contextId),
      context.Main.Update.mainZ(contextId),
      context.executionComplete(contextId)
    )

    // attach visualization
    context.send(
      Api.Request(
        requestId,
        Api.AttachVisualisation(
          visualisationId,
          context.Main.idMainZ,
          Api.VisualisationConfiguration(
            contextId,
            "Enso_Test.Test.Visualisation",
            "here.encode"
          )
        )
      )
    )
    val attachVisualisationResponses = context.receive(2)
    attachVisualisationResponses should contain(
      Api.Response(requestId, Api.VisualisationAttached())
    )
    val expectedExpressionId = context.Main.idMainZ
    val Some(data) = attachVisualisationResponses.collectFirst {
      case Api.Response(
            None,
            Api.VisualisationUpdate(
              Api.VisualisationContext(
                `visualisationId`,
                `contextId`,
                `expectedExpressionId`
              ),
              data
            )
          ) =>
        data
    }
    data.sameElements("50".getBytes) shouldBe true

    // Modify the file
    context.send(
      Api.Request(
        Api.EditFileNotification(
          mainFile,
          Seq(
            TextEdit(
              model.Range(model.Position(4, 8), model.Position(4, 9)),
              "5"
            )
          )
        )
      )
    )

    val editFileResponse = context.receive(2)
    editFileResponse should contain(
      context.executionComplete(contextId)
    )
    val Some(data1) = editFileResponse.collectFirst {
      case Api.Response(
            None,
            Api.VisualisationUpdate(
              Api.VisualisationContext(
                `visualisationId`,
                `contextId`,
                `expectedExpressionId`
              ),
              data
            )
          ) =>
        data
    }
    data1.sameElements("45".getBytes) shouldBe true
  }

  it should "be able to modify visualisations" in {
    val contents = context.Main.code
    val mainFile = context.writeMain(contents)
    val visualisationFile =
      context.writeInSrcDir("Visualisation", context.Visualisation.code)

    // open files
    context.send(
      Api.Request(Api.OpenFileNotification(mainFile, contents))
    )
    context.receiveNone shouldEqual None
    context.send(
      Api.Request(
        Api.OpenFileNotification(
          visualisationFile,
          context.Visualisation.code
        )
      )
    )
    context.receiveNone shouldEqual None

    val contextId       = UUID.randomUUID()
    val requestId       = UUID.randomUUID()
    val visualisationId = UUID.randomUUID()

    // create context
    context.send(Api.Request(requestId, Api.CreateContextRequest(contextId)))
    context.receive shouldEqual Some(
      Api.Response(requestId, Api.CreateContextResponse(contextId))
    )

    // push main
    val item1 = Api.StackItem.ExplicitCall(
      Api.MethodPointer("Enso_Test.Test.Main", "Enso_Test.Test.Main", "main"),
      None,
      Vector()
    )
    context.send(
      Api.Request(requestId, Api.PushContextRequest(contextId, item1))
    )
    context.receive(5) should contain theSameElementsAs Seq(
      Api.Response(requestId, Api.PushContextResponse(contextId)),
      context.Main.Update.mainX(contextId),
      context.Main.Update.mainY(contextId),
      context.Main.Update.mainZ(contextId),
      context.executionComplete(contextId)
    )

    // attach visualisation
    context.send(
      Api.Request(
        requestId,
        Api.AttachVisualisation(
          visualisationId,
          context.Main.idMainX,
          Api.VisualisationConfiguration(
            contextId,
            "Enso_Test.Test.Visualisation",
            "x -> here.encode x"
          )
        )
      )
    )

    val attachVisualisationResponses = context.receive(2)
    attachVisualisationResponses should contain(
      Api.Response(requestId, Api.VisualisationAttached())
    )
    val expectedExpressionId = context.Main.idMainX
    val Some(data) = attachVisualisationResponses.collectFirst {
      case Api.Response(
            None,
            Api.VisualisationUpdate(
              Api.VisualisationContext(
                `visualisationId`,
                `contextId`,
                `expectedExpressionId`
              ),
              data
            )
          ) =>
        data
    }
    data.sameElements("6".getBytes) shouldBe true

    // modify visualisation
    context.send(
      Api.Request(
        requestId,
        Api.ModifyVisualisation(
          visualisationId,
          Api.VisualisationConfiguration(
            contextId,
            "Enso_Test.Test.Visualisation",
            "x -> here.incAndEncode x"
          )
        )
      )
    )
    val modifyVisualisationResponses = context.receive(2)
    modifyVisualisationResponses should contain(
      Api.Response(requestId, Api.VisualisationModified())
    )
    val Some(dataAfterModification) =
      modifyVisualisationResponses.collectFirst {
        case Api.Response(
              None,
              Api.VisualisationUpdate(
                Api.VisualisationContext(
                  `visualisationId`,
                  `contextId`,
                  `expectedExpressionId`
                ),
                data
              )
            ) =>
          data
      }
    dataAfterModification.sameElements("7".getBytes) shouldBe true
  }

  it should "not emit visualisation update when visualisation is detached" in {
    val contents = context.Main.code
    val mainFile = context.writeMain(contents)
    val visualisationFile =
      context.writeInSrcDir("Visualisation", context.Visualisation.code)

    // open files
    context.send(
      Api.Request(Api.OpenFileNotification(mainFile, contents))
    )
    context.receiveNone shouldEqual None
    context.send(
      Api.Request(
        Api.OpenFileNotification(
          visualisationFile,
          context.Visualisation.code
        )
      )
    )

    val contextId       = UUID.randomUUID()
    val requestId       = UUID.randomUUID()
    val visualisationId = UUID.randomUUID()

    // create context
    context.send(Api.Request(requestId, Api.CreateContextRequest(contextId)))
    context.receive shouldEqual Some(
      Api.Response(requestId, Api.CreateContextResponse(contextId))
    )

    // attach visualisation
    context.send(
      Api.Request(
        requestId,
        Api.AttachVisualisation(
          visualisationId,
          context.Main.idMainX,
          Api.VisualisationConfiguration(
            contextId,
            "Enso_Test.Test.Visualisation",
            "x -> here.encode x"
          )
        )
      )
    )
    context.receive(3) should contain theSameElementsAs Seq(
      Api.Response(requestId, Api.VisualisationAttached()),
      Api.Response(
        Api.ExecutionFailed(
          contextId,
          Api.ExecutionResult.Failure("Execution stack is empty.", None)
        )
      ),
      context.executionComplete(contextId)
    )

    // push main
    val item1 = Api.StackItem.ExplicitCall(
      Api.MethodPointer("Enso_Test.Test.Main", "Enso_Test.Test.Main", "main"),
      None,
      Vector()
    )
    context.send(
      Api.Request(requestId, Api.PushContextRequest(contextId, item1))
    )
    val pushResponses = context.receive(6)
    pushResponses should contain allOf (
      Api.Response(requestId, Api.PushContextResponse(contextId)),
      context.Main.Update.mainX(contextId),
      context.Main.Update.mainY(contextId),
      context.Main.Update.mainZ(contextId),
      context.executionComplete(contextId)
    )
    val expectedExpressionId = context.Main.idMainX
    val Some(data) =
      pushResponses.collectFirst {
        case Api.Response(
              None,
              Api.VisualisationUpdate(
                Api.VisualisationContext(
                  `visualisationId`,
                  `contextId`,
                  `expectedExpressionId`
                ),
                data
              )
            ) =>
          data
      }
    data.sameElements("6".getBytes) shouldBe true

    // detach visualisation
    context.send(
      Api.Request(
        requestId,
        Api.DetachVisualisation(
          contextId,
          visualisationId,
          context.Main.idMainX
        )
      )
    )
    context.receive shouldEqual Some(
      Api.Response(requestId, Api.VisualisationDetached())
    )

    // recompute
    context.send(
      Api.Request(requestId, Api.RecomputeContextRequest(contextId, None))
    )
    context.receive(2) should contain theSameElementsAs Seq(
      Api.Response(requestId, Api.RecomputeContextResponse(contextId)),
      context.executionComplete(contextId)
    )

    // recompute invalidating x
    context.send(
      Api.Request(
        requestId,
        Api.RecomputeContextRequest(
          contextId,
          Some(
            Api.InvalidatedExpressions.Expressions(Vector(context.Main.idMainX))
          )
        )
      )
    )
    context.receive(2) should contain theSameElementsAs Seq(
      Api.Response(requestId, Api.RecomputeContextResponse(contextId)),
      context.executionComplete(contextId)
    )
  }

  it should "not emit visualisation update when expression is not affected by the change" in {
    val contents   = context.Main.code
    val moduleName = "Enso_Test.Test.Main"
    val mainFile   = context.writeMain(contents)
    val visualisationFile =
      context.writeInSrcDir("Visualisation", context.Visualisation.code)

    val contextId       = UUID.randomUUID()
    val requestId       = UUID.randomUUID()
    val visualisationId = UUID.randomUUID()

    // open files
    context.send(
      Api.Request(
        Api.OpenFileNotification(
          visualisationFile,
          context.Visualisation.code
        )
      )
    )
    context.receiveNone shouldEqual None
    context.send(
      Api.Request(Api.OpenFileNotification(mainFile, contents))
    )
    context.receiveNone shouldEqual None

    // create context
    context.send(Api.Request(requestId, Api.CreateContextRequest(contextId)))
    context.receive shouldEqual Some(
      Api.Response(requestId, Api.CreateContextResponse(contextId))
    )

    // push main
    val item1 = Api.StackItem.ExplicitCall(
      Api.MethodPointer(moduleName, "Enso_Test.Test.Main", "main"),
      None,
      Vector()
    )
    context.send(
      Api.Request(requestId, Api.PushContextRequest(contextId, item1))
    )

    context.receive(5) should contain theSameElementsAs Seq(
      Api.Response(requestId, Api.PushContextResponse(contextId)),
      context.Main.Update.mainX(contextId),
      context.Main.Update.mainY(contextId),
      context.Main.Update.mainZ(contextId),
      context.executionComplete(contextId)
    )

    // attach visualization
    context.send(
      Api.Request(
        requestId,
        Api.AttachVisualisation(
          visualisationId,
          context.Main.idMainX,
          Api.VisualisationConfiguration(
            contextId,
            "Enso_Test.Test.Visualisation",
            "here.encode"
          )
        )
      )
    )
    val attachVisualisationResponses = context.receive(2)
    attachVisualisationResponses should contain(
      Api.Response(requestId, Api.VisualisationAttached())
    )
    val expectedExpressionId = context.Main.idMainX
    val Some(data) = attachVisualisationResponses.collectFirst {
      case Api.Response(
            None,
            Api.VisualisationUpdate(
              Api.VisualisationContext(
                `visualisationId`,
                `contextId`,
                `expectedExpressionId`
              ),
              data
            )
          ) =>
        data
    }
    data.sameElements("6".getBytes) shouldBe true

    // Modify the file
    context.send(
      Api.Request(
        Api.EditFileNotification(
          mainFile,
          Seq(
            TextEdit(
              model.Range(model.Position(6, 12), model.Position(6, 13)),
              "6"
            )
          )
        )
      )
    )

    context.receive(1) should contain theSameElementsAs Seq(
      context.executionComplete(contextId)
    )
  }

  it should "not reorder visualization commands" in {
    val contents = context.Main.code
    val mainFile = context.writeMain(contents)
    val visualisationFile =
      context.writeInSrcDir("Visualisation", context.Visualisation.code)

    // open files
    context.send(
      Api.Request(Api.OpenFileNotification(mainFile, contents))
    )
    context.receiveNone shouldEqual None
    context.send(
      Api.Request(
        Api.OpenFileNotification(
          visualisationFile,
          context.Visualisation.code
        )
      )
    )
    context.receiveNone shouldEqual None

    val contextId       = UUID.randomUUID()
    val requestId       = UUID.randomUUID()
    val visualisationId = UUID.randomUUID()

    // create context
    context.send(Api.Request(requestId, Api.CreateContextRequest(contextId)))
    context.receive shouldEqual Some(
      Api.Response(requestId, Api.CreateContextResponse(contextId))
    )

    // push main
    val item1 = Api.StackItem.ExplicitCall(
      Api.MethodPointer("Enso_Test.Test.Main", "Enso_Test.Test.Main", "main"),
      None,
      Vector()
    )
    context.send(
      Api.Request(requestId, Api.PushContextRequest(contextId, item1))
    )
    context.receive(5) should contain theSameElementsAs Seq(
      Api.Response(requestId, Api.PushContextResponse(contextId)),
      context.Main.Update.mainX(contextId),
      context.Main.Update.mainY(contextId),
      context.Main.Update.mainZ(contextId),
      context.executionComplete(contextId)
    )

    // attach visualisation
    context.send(
      Api.Request(
        requestId,
        Api.AttachVisualisation(
          visualisationId,
          context.Main.idMainX,
          Api.VisualisationConfiguration(
            contextId,
            "Enso_Test.Test.Visualisation",
            "x -> here.encode x"
          )
        )
      )
    )

    val attachVisualisationResponses = context.receive(2)
    attachVisualisationResponses should contain(
      Api.Response(requestId, Api.VisualisationAttached())
    )
    val expectedExpressionId = context.Main.idMainX
    val Some(data) = attachVisualisationResponses.collectFirst {
      case Api.Response(
            None,
            Api.VisualisationUpdate(
              Api.VisualisationContext(
                `visualisationId`,
                `contextId`,
                `expectedExpressionId`
              ),
              data
            )
          ) =>
        data
    }
    data.sameElements("6".getBytes) shouldBe true

    // modify visualisation
    context.send(
      Api.Request(
        requestId,
        Api.ModifyVisualisation(
          visualisationId,
          Api.VisualisationConfiguration(
            contextId,
            "Enso_Test.Test.Visualisation",
            "x -> here.incAndEncode x"
          )
        )
      )
    )
    // detach visualisation
    context.send(
      Api.Request(
        requestId,
        Api.DetachVisualisation(
          contextId,
          visualisationId,
          context.Main.idMainX
        )
      )
    )
    val modifyVisualisationResponses = context.receive(3)
    modifyVisualisationResponses should contain allOf (
      Api.Response(requestId, Api.VisualisationModified()),
      Api.Response(requestId, Api.VisualisationDetached())
    )
    val Some(dataAfterModification) =
      modifyVisualisationResponses.collectFirst {
        case Api.Response(
              None,
              Api.VisualisationUpdate(
                Api.VisualisationContext(
                  `visualisationId`,
                  `contextId`,
                  `expectedExpressionId`
                ),
                data
              )
            ) =>
          data
      }
    dataAfterModification.sameElements("7".getBytes) shouldBe true
  }

  it should "return ModuleNotFound error when attaching visualisation" in {
    val idMain     = context.Main.metadata.addItem(87, 1)
    val contents   = context.Main.code
    val mainFile   = context.writeMain(context.Main.code)
    val moduleName = "Enso_Test.Test.Main"

    val contextId       = UUID.randomUUID()
    val requestId       = UUID.randomUUID()
    val visualisationId = UUID.randomUUID()

    // create context
    context.send(Api.Request(requestId, Api.CreateContextRequest(contextId)))
    context.receive shouldEqual Some(
      Api.Response(requestId, Api.CreateContextResponse(contextId))
    )

    // Open the new file
    context.send(
      Api.Request(Api.OpenFileNotification(mainFile, contents))
    )
    context.receiveNone shouldEqual None

    // push main
    val item1 = Api.StackItem.ExplicitCall(
      Api.MethodPointer(moduleName, "Enso_Test.Test.Main", "main"),
      None,
      Vector()
    )
    context.send(
      Api.Request(requestId, Api.PushContextRequest(contextId, item1))
    )
    context.receive(6) should contain theSameElementsAs Seq(
      Api.Response(requestId, Api.PushContextResponse(contextId)),
      context.Main.Update.mainX(contextId),
      context.Main.Update.mainY(contextId),
      context.Main.Update.mainZ(contextId),
      TestMessages.update(contextId, idMain, Constants.INTEGER),
      context.executionComplete(contextId)
    )

    // attach visualisation
    context.send(
      Api.Request(
        requestId,
        Api.AttachVisualisation(
          visualisationId,
          idMain,
          Api.VisualisationConfiguration(
            contextId,
            "Test.Undefined",
            "x -> x"
          )
        )
      )
    )
    context.receive(1) should contain theSameElementsAs Seq(
      Api.Response(requestId, Api.ModuleNotFound("Test.Undefined"))
    )
  }

  it should "be able to use external libraries if they are needed by the visualisation" in {
    val idMain     = context.Main.metadata.addItem(87, 1)
    val contents   = context.Main.code
    val mainFile   = context.writeMain(context.Main.code)
    val moduleName = "Enso_Test.Test.Main"

    val contextId       = UUID.randomUUID()
    val requestId       = UUID.randomUUID()
    val visualisationId = UUID.randomUUID()

    // create context
    context.send(Api.Request(requestId, Api.CreateContextRequest(contextId)))
    context.receive shouldEqual Some(
      Api.Response(requestId, Api.CreateContextResponse(contextId))
    )

    // Open the new file
    context.send(
      Api.Request(Api.OpenFileNotification(mainFile, contents))
    )
    context.receiveNone shouldEqual None

    // push main
    val item1 = Api.StackItem.ExplicitCall(
      Api.MethodPointer(moduleName, "Enso_Test.Test.Main", "main"),
      None,
      Vector()
    )
    context.send(
      Api.Request(requestId, Api.PushContextRequest(contextId, item1))
    )
    context.receive(6) should contain theSameElementsAs Seq(
      Api.Response(requestId, Api.PushContextResponse(contextId)),
      context.Main.Update.mainX(contextId),
      context.Main.Update.mainY(contextId),
      context.Main.Update.mainZ(contextId),
      TestMessages.update(contextId, idMain, Constants.INTEGER),
      context.executionComplete(contextId)
    )

    // attach visualisation
    context.send(
      Api.Request(
        requestId,
        Api.AttachVisualisation(
          visualisationId,
          idMain,
          Api.VisualisationConfiguration(
            contextId,
            "Standard.Visualization.Id",
            "x -> x.default_visualization.to_text"
          )
        )
      )
    )

    val attachVisualisationResponses =
      context.receive(n = 5, timeoutSeconds = 60)
    attachVisualisationResponses should contain allOf (
      Api.Response(requestId, Api.VisualisationAttached()),
      context.executionComplete(contextId)
    )

    val Some(data) = attachVisualisationResponses.collectFirst {
      case Api.Response(
            None,
            Api.VisualisationUpdate(
              Api.VisualisationContext(
                `visualisationId`,
                `contextId`,
                `idMain`
              ),
              data
            )
          ) =>
        data
    }

    data.sameElements("(Builtin 'JSON')".getBytes) shouldBe true

    val loadedLibraries = attachVisualisationResponses.collect {
      case Api.Response(None, Api.LibraryLoaded(namespace, name, _, _)) =>
        (namespace, name)
    }

    loadedLibraries should contain(("Standard", "Visualization"))
  }

  it should "return VisualisationExpressionFailed error when attaching visualisation" in {
    val idMain     = context.Main.metadata.addItem(87, 1)
    val contents   = context.Main.code
    val mainFile   = context.writeMain(context.Main.code)
    val moduleName = "Enso_Test.Test.Main"

    val contextId       = UUID.randomUUID()
    val requestId       = UUID.randomUUID()
    val visualisationId = UUID.randomUUID()

    // create context
    context.send(Api.Request(requestId, Api.CreateContextRequest(contextId)))
    context.receive shouldEqual Some(
      Api.Response(requestId, Api.CreateContextResponse(contextId))
    )

    // Open the new file
    context.send(
      Api.Request(Api.OpenFileNotification(mainFile, contents))
    )
    context.receiveNone shouldEqual None

    // push main
    val item1 = Api.StackItem.ExplicitCall(
      Api.MethodPointer(moduleName, "Enso_Test.Test.Main", "main"),
      None,
      Vector()
    )
    context.send(
      Api.Request(requestId, Api.PushContextRequest(contextId, item1))
    )
    context.receive(6) should contain theSameElementsAs Seq(
      Api.Response(requestId, Api.PushContextResponse(contextId)),
      context.Main.Update.mainX(contextId),
      context.Main.Update.mainY(contextId),
      context.Main.Update.mainZ(contextId),
      TestMessages.update(contextId, idMain, Constants.INTEGER),
      context.executionComplete(contextId)
    )

    // attach visualisation
    context.send(
      Api.Request(
        requestId,
        Api.AttachVisualisation(
          visualisationId,
          idMain,
          Api.VisualisationConfiguration(
            contextId,
            "Enso_Test.Test.Main",
            "here.does_not_exist"
          )
        )
      )
    )
    context.receive(1) should contain theSameElementsAs Seq(
      Api.Response(
        requestId,
        Api.VisualisationExpressionFailed(
          "Method `does_not_exist` of Main could not be found.",
          Some(
            Api.ExecutionResult.Diagnostic.error(
              message = "Method `does_not_exist` of Main could not be found.",
              stack = Vector(
                Api.StackTraceElement("<eval>", None, None, None),
                Api.StackTraceElement("Debug.eval", None, None, None)
              )
            )
          )
        )
      )
    )
  }

  it should "return visualisation evaluation errors with diagnostic info" in {
    val idMain     = context.Main.metadata.addItem(87, 1)
    val contents   = context.Main.code
    val mainFile   = context.writeMain(context.Main.code)
    val moduleName = "Enso_Test.Test.Main"

    val contextId       = UUID.randomUUID()
    val requestId       = UUID.randomUUID()
    val visualisationId = UUID.randomUUID()

    // create context
    context.send(Api.Request(requestId, Api.CreateContextRequest(contextId)))
    context.receive shouldEqual Some(
      Api.Response(requestId, Api.CreateContextResponse(contextId))
    )

    // Open the new file
    context.send(
      Api.Request(Api.OpenFileNotification(mainFile, contents))
    )
    context.receiveNone shouldEqual None

    // push main
    val item1 = Api.StackItem.ExplicitCall(
      Api.MethodPointer(moduleName, "Enso_Test.Test.Main", "main"),
      None,
      Vector()
    )
    context.send(
      Api.Request(requestId, Api.PushContextRequest(contextId, item1))
    )
    context.receive(6) should contain theSameElementsAs Seq(
      Api.Response(requestId, Api.PushContextResponse(contextId)),
      context.Main.Update.mainX(contextId),
      context.Main.Update.mainY(contextId),
      context.Main.Update.mainZ(contextId),
      TestMessages.update(contextId, idMain, Constants.INTEGER),
      context.executionComplete(contextId)
    )

    // attach visualisation
    context.send(
      Api.Request(
        requestId,
        Api.AttachVisualisation(
          visualisationId,
          idMain,
          Api.VisualisationConfiguration(
            contextId,
            moduleName,
            "x -> x.visualise_me"
          )
        )
      )
    )
    context.receive(3) should contain theSameElementsAs Seq(
      Api.Response(requestId, Api.VisualisationAttached()),
      Api.Response(
        Api.VisualisationEvaluationFailed(
          contextId,
          visualisationId,
          idMain,
          "Method `visualise_me` of 50 (Integer) could not be found.",
          Some(
            Api.ExecutionResult.Diagnostic.error(
              "Method `visualise_me` of 50 (Integer) could not be found.",
              None,
              Some(model.Range(model.Position(0, 5), model.Position(0, 19))),
              None,
              Vector(
                Api.StackTraceElement(
                  "<anonymous>",
                  None,
                  Some(
                    model.Range(model.Position(0, 5), model.Position(0, 19))
                  ),
                  None
                )
              )
            )
          )
        )
      ),
      context.executionComplete(contextId)
    )
  }

  it should "return visualisation error with a stack trace" in {
    val idMain     = context.Main.metadata.addItem(87, 1)
    val contents   = context.Main.code
    val mainFile   = context.writeMain(context.Main.code)
    val moduleName = "Enso_Test.Test.Main"
    val visualisationCode =
      """
        |encode x = x.visualise_me
        |
        |inc_and_encode x = here.encode x+1
        |""".stripMargin.linesIterator.mkString("\n")

    val visualisationFile =
      context.writeInSrcDir("Visualisation", visualisationCode)

    context.send(
      Api.Request(
        Api.OpenFileNotification(
          visualisationFile,
          visualisationCode
        )
      )
    )

    val contextId       = UUID.randomUUID()
    val requestId       = UUID.randomUUID()
    val visualisationId = UUID.randomUUID()

    // create context
    context.send(Api.Request(requestId, Api.CreateContextRequest(contextId)))
    context.receive shouldEqual Some(
      Api.Response(requestId, Api.CreateContextResponse(contextId))
    )

    // Open the new file
    context.send(
      Api.Request(Api.OpenFileNotification(mainFile, contents))
    )
    context.receiveNone shouldEqual None

    // push main
    val item1 = Api.StackItem.ExplicitCall(
      Api.MethodPointer(moduleName, "Enso_Test.Test.Main", "main"),
      None,
      Vector()
    )
    context.send(
      Api.Request(requestId, Api.PushContextRequest(contextId, item1))
    )
    context.receive(6) should contain theSameElementsAs Seq(
      Api.Response(requestId, Api.PushContextResponse(contextId)),
      context.Main.Update.mainX(contextId),
      context.Main.Update.mainY(contextId),
      context.Main.Update.mainZ(contextId),
      TestMessages.update(contextId, idMain, Constants.INTEGER),
      context.executionComplete(contextId)
    )

    // attach visualisation
    context.send(
      Api.Request(
        requestId,
        Api.AttachVisualisation(
          visualisationId,
          idMain,
          Api.VisualisationConfiguration(
            contextId,
            "Enso_Test.Test.Visualisation",
            "here.inc_and_encode"
          )
        )
      )
    )
    context.receive(3) should contain theSameElementsAs Seq(
      Api.Response(requestId, Api.VisualisationAttached()),
      Api.Response(
        Api.VisualisationEvaluationFailed(
          contextId,
          visualisationId,
          idMain,
          "Method `visualise_me` of 51 (Integer) could not be found.",
          Some(
            Api.ExecutionResult.Diagnostic.error(
              "Method `visualise_me` of 51 (Integer) could not be found.",
              Some(visualisationFile),
              Some(model.Range(model.Position(1, 11), model.Position(1, 25))),
              None,
              Vector(
                Api.StackTraceElement(
                  "Visualisation.encode",
                  Some(visualisationFile),
                  Some(
                    model.Range(model.Position(1, 11), model.Position(1, 25))
                  ),
                  None
                ),
                Api.StackTraceElement(
                  "Visualisation.inc_and_encode",
                  Some(visualisationFile),
                  Some(
                    model.Range(model.Position(3, 19), model.Position(3, 34))
                  ),
                  None
                )
              )
            )
          )
        )
      ),
      context.executionComplete(contextId)
    )
  }
}
