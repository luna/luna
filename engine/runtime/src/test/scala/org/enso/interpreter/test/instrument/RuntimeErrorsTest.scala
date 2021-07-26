package org.enso.interpreter.test.instrument

import java.io.{ByteArrayOutputStream, File}
import java.nio.ByteBuffer
import java.nio.file.{Files, Paths}
import java.util.UUID
import java.util.concurrent.{LinkedBlockingQueue, TimeUnit}
import org.enso.interpreter.instrument.execution.Timer
import org.enso.interpreter.runtime.`type`.Constants
import org.enso.interpreter.test.Metadata
import org.enso.pkg.{Package, PackageManager}
import org.enso.polyglot._
import org.enso.polyglot.runtime.Runtime.Api
import org.enso.text.editing.model
import org.enso.text.editing.model.TextEdit
import org.enso.text.{ContentVersion, Sha3_224VersionCalculator}
import org.graalvm.polyglot.Context
import org.graalvm.polyglot.io.MessageEndpoint
import org.scalatest.BeforeAndAfterEach
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

@scala.annotation.nowarn("msg=multiarg infix syntax")
class RuntimeErrorsTest
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
      PackageManager.Default.create(
        tmpDir,
        packageName,
        namespace = "Enso_Test"
      )
    val out: ByteArrayOutputStream = new ByteArrayOutputStream()
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
      .asHostObject[org.enso.interpreter.runtime.Context]
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

    def receive: Option[Api.Response] = {
      Option(messageQueue.poll(10, TimeUnit.SECONDS))
    }

    def receive(n: Int): List[Api.Response] = {
      Iterator.continually(receive).take(n).flatten.toList
    }

    def consumeOut: List[String] = {
      val result = out.toString
      out.reset()
      result.linesIterator.toList
    }

    def executionComplete(contextId: UUID): Api.Response =
      Api.Response(Api.ExecutionComplete(contextId))
  }

  def contentsVersion(content: String): ContentVersion =
    Sha3_224VersionCalculator.evalVersion(content)

  override protected def beforeEach(): Unit = {
    context = new TestContext("Test")
    val Some(Api.Response(_, Api.InitializedNotification())) = context.receive
  }

  it should "return panic sentinels in method body" in {
    val contextId  = UUID.randomUUID()
    val requestId  = UUID.randomUUID()
    val moduleName = "Enso_Test.Test.Main"
    val metadata   = new Metadata
    // foo body id
    metadata.addItem(21, 5)
    val xId       = metadata.addItem(35, 9)
    val yId       = metadata.addItem(53, 8)
    val mainResId = metadata.addItem(66, 7)

    val code =
      """main =
        |    foo a b = a + b
        |    x = undefined
        |    y = foo x 42
        |    foo y 1
        |""".stripMargin.linesIterator.mkString("\n")
    val contents = metadata.appendToCode(code)
    val mainFile = context.writeMain(contents)

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
    context.send(
      Api.Request(
        requestId,
        Api.PushContextRequest(
          contextId,
          Api.StackItem.ExplicitCall(
            Api.MethodPointer(moduleName, "Enso_Test.Test.Main", "main"),
            None,
            Vector()
          )
        )
      )
    )
    context.receive(6) should contain theSameElementsAs Seq(
      Api.Response(requestId, Api.PushContextResponse(contextId)),
      Api.Response(
        Api.ExecutionUpdate(
          contextId,
          Seq(
            Api.ExecutionResult.Diagnostic.error(
              "Variable `undefined` is not defined.",
              Some(mainFile),
              Some(model.Range(model.Position(2, 8), model.Position(2, 17))),
              Some(xId)
            )
          )
        )
      ),
      TestMessages.panic(
        contextId,
        xId,
        Api.ExpressionUpdate.Payload.Panic(
          "Compile error: Variable `undefined` is not defined.",
          Seq(xId)
        )
      ),
      TestMessages.panic(
        contextId,
        yId,
        Api.ExpressionUpdate.Payload.Panic(
          "Compile error: Variable `undefined` is not defined.",
          Seq(xId)
        )
      ),
      TestMessages.panic(
        contextId,
        mainResId,
        Api.ExpressionUpdate.Payload.Panic(
          "Compile error: Variable `undefined` is not defined.",
          Seq(xId)
        )
      ),
      context.executionComplete(contextId)
    )
  }

  it should "return panic sentinels in method calls" in {
    val contextId  = UUID.randomUUID()
    val requestId  = UUID.randomUUID()
    val moduleName = "Enso_Test.Test.Main"
    val metadata   = new Metadata
    val mainBodyId = metadata.addItem(28, 12)

    val code =
      """foo a b = a + b + x
        |
        |main = here.foo 1 2
        |""".stripMargin.linesIterator.mkString("\n")
    val contents = metadata.appendToCode(code)
    val mainFile = context.writeMain(contents)

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
    context.send(
      Api.Request(
        requestId,
        Api.PushContextRequest(
          contextId,
          Api.StackItem.ExplicitCall(
            Api.MethodPointer(moduleName, "Enso_Test.Test.Main", "main"),
            None,
            Vector()
          )
        )
      )
    )
    context.receive(4) should contain theSameElementsAs Seq(
      Api.Response(requestId, Api.PushContextResponse(contextId)),
      Api.Response(
        Api.ExecutionUpdate(
          contextId,
          Seq(
            Api.ExecutionResult.Diagnostic.error(
              "Variable `x` is not defined.",
              Some(mainFile),
              Some(model.Range(model.Position(0, 18), model.Position(0, 19))),
              None
            )
          )
        )
      ),
      TestMessages.panic(
        contextId,
        mainBodyId,
        Api.MethodPointer("Enso_Test.Test.Main", "Enso_Test.Test.Main", "foo"),
        Api.ExpressionUpdate.Payload.Panic(
          "Compile error: Variable `x` is not defined.",
          Seq(mainBodyId)
        )
      ),
      context.executionComplete(contextId)
    )
  }

  it should "return dataflow errors in method body" in {
    val contextId  = UUID.randomUUID()
    val requestId  = UUID.randomUUID()
    val moduleName = "Enso_Test.Test.Main"
    val metadata   = new Metadata
    // foo body id
    metadata.addItem(70, 5)
    val xId       = metadata.addItem(84, 19)
    val yId       = metadata.addItem(112, 8)
    val mainResId = metadata.addItem(125, 7)

    val code =
      """from Standard.Builtins import all
        |
        |type MyError
        |
        |main =
        |    foo a b = a + b
        |    x = Error.throw MyError
        |    y = foo x 42
        |    foo y 1
        |""".stripMargin.linesIterator.mkString("\n")
    val contents = metadata.appendToCode(code)
    val mainFile = context.writeMain(contents)

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
    context.send(
      Api.Request(
        requestId,
        Api.PushContextRequest(
          contextId,
          Api.StackItem.ExplicitCall(
            Api.MethodPointer(moduleName, "Enso_Test.Test.Main", "main"),
            None,
            Vector()
          )
        )
      )
    )
    context.receive(5) should contain theSameElementsAs Seq(
      Api.Response(requestId, Api.PushContextResponse(contextId)),
      TestMessages.error(
        contextId,
        xId,
        Api.ExpressionUpdate.Payload.DataflowError(Seq())
      ),
      TestMessages.error(
        contextId,
        yId,
        Api.ExpressionUpdate.Payload.DataflowError(Seq())
      ),
      TestMessages.error(
        contextId,
        mainResId,
        Api.ExpressionUpdate.Payload.DataflowError(Seq())
      ),
      context.executionComplete(contextId)
    )
  }

  it should "return panic sentinels continuing execution" in {
    val contextId  = UUID.randomUUID()
    val requestId  = UUID.randomUUID()
    val moduleName = "Enso_Test.Test.Main"
    val metadata   = new Metadata
    val xId        = metadata.addItem(50, 9)
    val yId        = metadata.addItem(68, 2)
    val mainResId  = metadata.addItem(75, 12)

    val code =
      """from Standard.Builtins import all
        |
        |main =
        |    x = undefined
        |    y = 42
        |    IO.println y
        |""".stripMargin.linesIterator.mkString("\n")
    val contents = metadata.appendToCode(code)
    val mainFile = context.writeMain(contents)

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
    context.send(
      Api.Request(
        requestId,
        Api.PushContextRequest(
          contextId,
          Api.StackItem.ExplicitCall(
            Api.MethodPointer(moduleName, "Enso_Test.Test.Main", "main"),
            None,
            Vector()
          )
        )
      )
    )
    context.receive(6) should contain theSameElementsAs Seq(
      Api.Response(requestId, Api.PushContextResponse(contextId)),
      Api.Response(
        Api.ExecutionUpdate(
          contextId,
          Seq(
            Api.ExecutionResult.Diagnostic.warning(
              "Unused variable x.",
              Some(mainFile),
              Some(model.Range(model.Position(3, 4), model.Position(3, 5)))
            ),
            Api.ExecutionResult.Diagnostic.error(
              "Variable `undefined` is not defined.",
              Some(mainFile),
              Some(model.Range(model.Position(3, 8), model.Position(3, 17))),
              Some(xId)
            )
          )
        )
      ),
      TestMessages.panic(
        contextId,
        xId,
        Api.ExpressionUpdate.Payload.Panic(
          "Compile error: Variable `undefined` is not defined.",
          Seq(xId)
        )
      ),
      TestMessages.update(contextId, yId, Constants.INTEGER),
      TestMessages.update(contextId, mainResId, Constants.NOTHING),
      context.executionComplete(contextId)
    )
    context.consumeOut shouldEqual Seq("42")
  }

  it should "return dataflow errors continuing execution" in {
    val contextId  = UUID.randomUUID()
    val requestId  = UUID.randomUUID()
    val moduleName = "Enso_Test.Test.Main"
    val metadata   = new Metadata
    val xId        = metadata.addItem(64, 19)
    val yId        = metadata.addItem(92, 2)
    val mainResId  = metadata.addItem(99, 12)

    val code =
      """from Standard.Builtins import all
        |
        |type MyError
        |
        |main =
        |    x = Error.throw MyError
        |    y = 42
        |    IO.println y
        |""".stripMargin.linesIterator.mkString("\n")
    val contents = metadata.appendToCode(code)
    val mainFile = context.writeMain(contents)

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
    context.send(
      Api.Request(
        requestId,
        Api.PushContextRequest(
          contextId,
          Api.StackItem.ExplicitCall(
            Api.MethodPointer(moduleName, "Enso_Test.Test.Main", "main"),
            None,
            Vector()
          )
        )
      )
    )
    context.receive(6) should contain theSameElementsAs Seq(
      Api.Response(requestId, Api.PushContextResponse(contextId)),
      Api.Response(
        Api.ExecutionUpdate(
          contextId,
          Seq(
            Api.ExecutionResult.Diagnostic.warning(
              "Unused variable x.",
              Some(mainFile),
              Some(model.Range(model.Position(5, 4), model.Position(5, 5)))
            )
          )
        )
      ),
      TestMessages.error(
        contextId,
        xId,
        Api.ExpressionUpdate.Payload.DataflowError(Seq())
      ),
      TestMessages.update(contextId, yId, Constants.INTEGER),
      TestMessages.update(contextId, mainResId, Constants.NOTHING),
      context.executionComplete(contextId)
    )
    context.consumeOut shouldEqual Seq("42")
  }

  it should "continue execution after dataflow errors" in {
    val contextId  = UUID.randomUUID()
    val requestId  = UUID.randomUUID()
    val moduleName = "Enso_Test.Test.Main"
    val metadata   = new Metadata
    val xId        = metadata.addItem(64, 19)
    val yId        = metadata.addItem(92, 5)
    val mainResId  = metadata.addItem(102, 12)

    val code =
      """from Standard.Builtins import all
        |
        |type MyError
        |
        |main =
        |    x = Error.throw MyError
        |    y = x - 1
        |    IO.println y
        |""".stripMargin.linesIterator.mkString("\n")
    val contents = metadata.appendToCode(code)
    val mainFile = context.writeMain(contents)

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
    context.send(
      Api.Request(
        requestId,
        Api.PushContextRequest(
          contextId,
          Api.StackItem.ExplicitCall(
            Api.MethodPointer(moduleName, "Enso_Test.Test.Main", "main"),
            None,
            Vector()
          )
        )
      )
    )
    context.receive(5) should contain theSameElementsAs Seq(
      Api.Response(requestId, Api.PushContextResponse(contextId)),
      TestMessages.error(
        contextId,
        xId,
        Api.ExpressionUpdate.Payload.DataflowError(Seq())
      ),
      TestMessages.error(
        contextId,
        yId,
        Api.ExpressionUpdate.Payload.DataflowError(Seq())
      ),
      TestMessages.update(contextId, mainResId, Constants.NOTHING),
      context.executionComplete(contextId)
    )
    context.consumeOut shouldEqual Seq("(Error: MyError)")

    // Modify the file
    context.send(
      Api.Request(
        Api.EditFileNotification(
          mainFile,
          Seq(
            TextEdit(
              model.Range(model.Position(5, 8), model.Position(5, 27)),
              "1234567890123456789"
            )
          )
        )
      )
    )
    context.receive(3) should contain theSameElementsAs Seq(
      TestMessages.update(contextId, xId, Constants.INTEGER),
      TestMessages.update(contextId, yId, Constants.INTEGER),
      context.executionComplete(contextId)
    )
    context.consumeOut shouldEqual List("1234567890123456788")

    // Modify the file
    context.send(
      Api.Request(
        Api.EditFileNotification(
          mainFile,
          Seq(
            TextEdit(
              model.Range(model.Position(5, 8), model.Position(5, 27)),
              "1000000000000.div 0"
            )
          )
        )
      )
    )
    context.receive(3) should contain theSameElementsAs Seq(
      TestMessages.error(
        contextId,
        xId,
        Api.ExpressionUpdate.Payload.DataflowError(Seq())
      ),
      TestMessages.error(
        contextId,
        yId,
        Api.ExpressionUpdate.Payload.DataflowError(Seq())
      ),
      context.executionComplete(contextId)
    )
    context.consumeOut shouldEqual List(
      "(Error: (Arithmetic_Error 'Cannot divide by zero.'))"
    )

    // Modify the file
    context.send(
      Api.Request(
        Api.EditFileNotification(
          mainFile,
          Seq(
            TextEdit(
              model.Range(model.Position(5, 8), model.Position(5, 27)),
              "1000000000000.div 2"
            )
          )
        )
      )
    )
    context.receive(3) should contain theSameElementsAs Seq(
      TestMessages.update(contextId, xId, Constants.INTEGER),
      TestMessages.update(contextId, yId, Constants.INTEGER),
      context.executionComplete(contextId)
    )
    context.consumeOut shouldEqual List("499999999999")
  }

  it should "not send updates when dataflow error changes in expression" in {
    val contextId  = UUID.randomUUID()
    val requestId  = UUID.randomUUID()
    val moduleName = "Enso_Test.Test.Main"
    val metadata   = new Metadata
    val xId        = metadata.addItem(79, 20)
    val yId        = metadata.addItem(108, 5)
    val mainResId  = metadata.addItem(118, 12)

    val code =
      """from Standard.Builtins import all
        |
        |type MyError1
        |type MyError2
        |
        |main =
        |    x = Error.throw MyError1
        |    y = x - 1
        |    IO.println y
        |""".stripMargin.linesIterator.mkString("\n")
    val contents = metadata.appendToCode(code)
    val mainFile = context.writeMain(contents)

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
    context.send(
      Api.Request(
        requestId,
        Api.PushContextRequest(
          contextId,
          Api.StackItem.ExplicitCall(
            Api.MethodPointer(moduleName, "Enso_Test.Test.Main", "main"),
            None,
            Vector()
          )
        )
      )
    )
    context.receive(5) should contain theSameElementsAs Seq(
      Api.Response(requestId, Api.PushContextResponse(contextId)),
      TestMessages.error(
        contextId,
        xId,
        Api.ExpressionUpdate.Payload.DataflowError(Seq())
      ),
      TestMessages.error(
        contextId,
        yId,
        Api.ExpressionUpdate.Payload.DataflowError(Seq())
      ),
      TestMessages.update(contextId, mainResId, Constants.NOTHING),
      context.executionComplete(contextId)
    )
    context.consumeOut shouldEqual Seq("(Error: MyError1)")

    // Modify the file
    context.send(
      Api.Request(
        Api.EditFileNotification(
          mainFile,
          Seq(
            TextEdit(
              model.Range(model.Position(6, 20), model.Position(6, 28)),
              "MyError2"
            )
          )
        )
      )
    )
    context.receive(1) should contain theSameElementsAs Seq(
      context.executionComplete(contextId)
    )
    context.consumeOut shouldEqual List("(Error: MyError2)")
  }

  it should "not send updates when dataflow error changes in method" in {
    val contextId  = UUID.randomUUID()
    val requestId  = UUID.randomUUID()
    val moduleName = "Enso_Test.Test.Main"
    val metadata   = new Metadata
    val xId        = metadata.addItem(111, 8)
    val yId        = metadata.addItem(128, 5)
    val mainResId  = metadata.addItem(138, 12)

    val code =
      """from Standard.Builtins import all
        |
        |type MyError1
        |type MyError2
        |
        |foo =
        |    Error.throw MyError1
        |
        |main =
        |    x = this.foo
        |    y = x - 1
        |    IO.println y
        |""".stripMargin.linesIterator.mkString("\n")
    val contents = metadata.appendToCode(code)
    val mainFile = context.writeMain(contents)

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
    context.send(
      Api.Request(
        requestId,
        Api.PushContextRequest(
          contextId,
          Api.StackItem.ExplicitCall(
            Api.MethodPointer(moduleName, "Enso_Test.Test.Main", "main"),
            None,
            Vector()
          )
        )
      )
    )
    context.receive(5) should contain theSameElementsAs Seq(
      Api.Response(requestId, Api.PushContextResponse(contextId)),
      TestMessages.error(
        contextId,
        xId,
        Api.MethodPointer(moduleName, moduleName, "foo"),
        Api.ExpressionUpdate.Payload.DataflowError(Seq())
      ),
      TestMessages.error(
        contextId,
        yId,
        Api.ExpressionUpdate.Payload.DataflowError(Seq())
      ),
      TestMessages.update(contextId, mainResId, Constants.NOTHING),
      context.executionComplete(contextId)
    )
    context.consumeOut shouldEqual Seq("(Error: MyError1)")

    // Modify the file
    context.send(
      Api.Request(
        Api.EditFileNotification(
          mainFile,
          Seq(
            TextEdit(
              model.Range(model.Position(6, 16), model.Position(6, 24)),
              "MyError2"
            )
          )
        )
      )
    )
    context.receive(1) should contain theSameElementsAs Seq(
      context.executionComplete(contextId)
    )
    context.consumeOut shouldEqual List("(Error: MyError2)")
  }

  it should "continue execution after thrown panics" in {
    val contextId  = UUID.randomUUID()
    val requestId  = UUID.randomUUID()
    val moduleName = "Enso_Test.Test.Main"
    val metadata   = new Metadata
    val xId        = metadata.addItem(64, 19)
    val yId        = metadata.addItem(92, 5)
    val mainResId  = metadata.addItem(102, 12)

    val code =
      """from Standard.Builtins import all
        |
        |type MyError
        |
        |main =
        |    x = Panic.throw MyError
        |    y = x - 1
        |    IO.println y
        |""".stripMargin.linesIterator.mkString("\n")
    val contents = metadata.appendToCode(code)
    val mainFile = context.writeMain(contents)

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
    context.send(
      Api.Request(
        requestId,
        Api.PushContextRequest(
          contextId,
          Api.StackItem.ExplicitCall(
            Api.MethodPointer(moduleName, "Enso_Test.Test.Main", "main"),
            None,
            Vector()
          )
        )
      )
    )
    context.receive(5) should contain theSameElementsAs Seq(
      Api.Response(requestId, Api.PushContextResponse(contextId)),
      TestMessages.panic(
        contextId,
        xId,
        Api.ExpressionUpdate.Payload.Panic(
          "MyError",
          Seq(xId)
        )
      ),
      TestMessages.panic(
        contextId,
        yId,
        Api.ExpressionUpdate.Payload.Panic(
          "MyError",
          Seq(xId)
        )
      ),
      TestMessages.panic(
        contextId,
        mainResId,
        Api.ExpressionUpdate.Payload.Panic(
          "MyError",
          Seq(xId)
        )
      ),
      context.executionComplete(contextId)
    )
    context.consumeOut shouldEqual Seq()

    // Modify the file
    context.send(
      Api.Request(
        Api.EditFileNotification(
          mainFile,
          Seq(
            TextEdit(
              model.Range(model.Position(5, 8), model.Position(5, 27)),
              "1234567890123456789"
            )
          )
        )
      )
    )
    context.receive(4) should contain theSameElementsAs Seq(
      TestMessages.update(contextId, xId, Constants.INTEGER),
      TestMessages.update(contextId, yId, Constants.INTEGER),
      TestMessages.update(contextId, mainResId, Constants.NOTHING),
      context.executionComplete(contextId)
    )
    context.consumeOut shouldEqual List("1234567890123456788")
  }

  it should "continue execution after panics in expressions" in {
    val contextId  = UUID.randomUUID()
    val requestId  = UUID.randomUUID()
    val moduleName = "Enso_Test.Test.Main"
    val metadata   = new Metadata
    val xId        = metadata.addItem(50, 7)
    val yId        = metadata.addItem(66, 5)
    val mainResId  = metadata.addItem(76, 12)

    val code =
      """from Standard.Builtins import all
        |
        |main =
        |    x = 1 + foo
        |    y = x - 1
        |    IO.println y
        |""".stripMargin.linesIterator.mkString("\n")
    val contents = metadata.appendToCode(code)
    val mainFile = context.writeMain(contents)

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
    context.send(
      Api.Request(
        requestId,
        Api.PushContextRequest(
          contextId,
          Api.StackItem.ExplicitCall(
            Api.MethodPointer(moduleName, "Enso_Test.Test.Main", "main"),
            None,
            Vector()
          )
        )
      )
    )
    context.receive(6) should contain theSameElementsAs Seq(
      Api.Response(requestId, Api.PushContextResponse(contextId)),
      Api.Response(
        Api.ExecutionUpdate(
          contextId,
          Seq(
            Api.ExecutionResult.Diagnostic.error(
              "Variable `foo` is not defined.",
              Some(mainFile),
              Some(model.Range(model.Position(3, 12), model.Position(3, 15))),
              None
            )
          )
        )
      ),
      TestMessages.panic(
        contextId,
        xId,
        Api.ExpressionUpdate.Payload.Panic(
          "Compile error: Variable `foo` is not defined.",
          Seq(xId)
        )
      ),
      TestMessages.panic(
        contextId,
        yId,
        Api.ExpressionUpdate.Payload.Panic(
          "Compile error: Variable `foo` is not defined.",
          Seq(xId)
        )
      ),
      TestMessages.panic(
        contextId,
        mainResId,
        Api.ExpressionUpdate.Payload.Panic(
          "Compile error: Variable `foo` is not defined.",
          Seq(xId)
        )
      ),
      context.executionComplete(contextId)
    )
    context.consumeOut shouldEqual Seq()

    // Modify the file
    context.send(
      Api.Request(
        Api.EditFileNotification(
          mainFile,
          Seq(
            TextEdit(
              model.Range(model.Position(3, 12), model.Position(3, 15)),
              "101"
            )
          )
        )
      )
    )
    context.receive(4) should contain theSameElementsAs Seq(
      TestMessages.update(contextId, xId, Constants.INTEGER),
      TestMessages.update(contextId, yId, Constants.INTEGER),
      TestMessages.update(contextId, mainResId, Constants.NOTHING),
      context.executionComplete(contextId)
    )
    context.consumeOut shouldEqual List("101")

  }

  it should "send updates when panic changes in expression" in {
    val contextId  = UUID.randomUUID()
    val requestId  = UUID.randomUUID()
    val moduleName = "Enso_Test.Test.Main"
    val metadata   = new Metadata
    val xId        = metadata.addItem(79, 20)
    val yId        = metadata.addItem(108, 5)
    val mainResId  = metadata.addItem(118, 12)

    val code =
      """from Standard.Builtins import all
        |
        |type MyError1
        |type MyError2
        |
        |main =
        |    x = Panic.throw MyError1
        |    y = x - 1
        |    IO.println y
        |""".stripMargin.linesIterator.mkString("\n")
    val contents = metadata.appendToCode(code)
    val mainFile = context.writeMain(contents)

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
    context.send(
      Api.Request(
        requestId,
        Api.PushContextRequest(
          contextId,
          Api.StackItem.ExplicitCall(
            Api.MethodPointer(moduleName, "Enso_Test.Test.Main", "main"),
            None,
            Vector()
          )
        )
      )
    )
    context.receive(5) should contain theSameElementsAs Seq(
      Api.Response(requestId, Api.PushContextResponse(contextId)),
      TestMessages.panic(
        contextId,
        xId,
        Api.ExpressionUpdate.Payload.Panic(
          "MyError1",
          Seq(xId)
        )
      ),
      TestMessages.panic(
        contextId,
        yId,
        Api.ExpressionUpdate.Payload.Panic(
          "MyError1",
          Seq(xId)
        )
      ),
      TestMessages.panic(
        contextId,
        mainResId,
        Api.ExpressionUpdate.Payload.Panic(
          "MyError1",
          Seq(xId)
        )
      ),
      context.executionComplete(contextId)
    )
    context.consumeOut shouldEqual Seq()

    // Modify the file
    context.send(
      Api.Request(
        Api.EditFileNotification(
          mainFile,
          Seq(
            TextEdit(
              model.Range(model.Position(6, 20), model.Position(6, 28)),
              "MyError2"
            )
          )
        )
      )
    )
    context.receive(4) should contain theSameElementsAs Seq(
      TestMessages.panic(
        contextId,
        xId,
        Api.ExpressionUpdate.Payload.Panic(
          "MyError2",
          Seq(xId)
        )
      ),
      TestMessages.panic(
        contextId,
        yId,
        Api.ExpressionUpdate.Payload.Panic(
          "MyError2",
          Seq(xId)
        )
      ),
      TestMessages.panic(
        contextId,
        mainResId,
        Api.ExpressionUpdate.Payload.Panic(
          "MyError2",
          Seq(xId)
        )
      ),
      context.executionComplete(contextId)
    )
    context.consumeOut shouldEqual List()
  }

  it should "send updates when panic is resolved in method" in {
    val contextId  = UUID.randomUUID()
    val requestId  = UUID.randomUUID()
    val moduleName = "Enso_Test.Test.Main"
    val metadata   = new Metadata
    val xId        = metadata.addItem(75, 8)
    val yId        = metadata.addItem(92, 5)
    val mainResId  = metadata.addItem(102, 12)

    val code =
      """from Standard.Builtins import all
        |
        |foo =
        |    Panic.throw 9
        |
        |main =
        |    x = this.foo
        |    y = x + 1
        |    IO.println y
        |""".stripMargin.linesIterator.mkString("\n")
    val contents = metadata.appendToCode(code)
    val mainFile = context.writeMain(contents)

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
    context.send(
      Api.Request(
        requestId,
        Api.PushContextRequest(
          contextId,
          Api.StackItem.ExplicitCall(
            Api.MethodPointer(moduleName, "Enso_Test.Test.Main", "main"),
            None,
            Vector()
          )
        )
      )
    )
    context.receive(5) should contain theSameElementsAs Seq(
      Api.Response(requestId, Api.PushContextResponse(contextId)),
      TestMessages.panic(
        contextId,
        xId,
        Api.MethodPointer(moduleName, moduleName, "foo"),
        Api.ExpressionUpdate.Payload.Panic(
          "9 (Integer)",
          Seq(xId)
        )
      ),
      TestMessages.panic(
        contextId,
        yId,
        Api.ExpressionUpdate.Payload.Panic(
          "9 (Integer)",
          Seq(xId)
        )
      ),
      TestMessages.panic(
        contextId,
        mainResId,
        Api.ExpressionUpdate.Payload.Panic(
          "9 (Integer)",
          Seq(xId)
        )
      ),
      context.executionComplete(contextId)
    )
    context.consumeOut shouldEqual Seq()

    // Modify the file
    context.send(
      Api.Request(
        Api.EditFileNotification(
          mainFile,
          Seq(
            TextEdit(
              model.Range(model.Position(3, 4), model.Position(3, 17)),
              "10002 - 10000"
            )
          )
        )
      )
    )
    context.receive(4) should contain theSameElementsAs Seq(
      TestMessages.update(
        contextId,
        xId,
        Constants.INTEGER,
        Api.MethodPointer(moduleName, moduleName, "foo")
      ),
      TestMessages.update(contextId, yId, Constants.INTEGER),
      TestMessages.update(contextId, mainResId, Constants.NOTHING),
      context.executionComplete(contextId)
    )
    context.consumeOut shouldEqual List("3")
  }

  it should "send updates when dataflow error is resolved in method" in {
    val contextId  = UUID.randomUUID()
    val requestId  = UUID.randomUUID()
    val moduleName = "Enso_Test.Test.Main"
    val metadata   = new Metadata
    val xId        = metadata.addItem(75, 8)
    val yId        = metadata.addItem(92, 5)
    val mainResId  = metadata.addItem(102, 12)

    val code =
      """from Standard.Builtins import all
        |
        |foo =
        |    Error.throw 9
        |
        |main =
        |    x = this.foo
        |    y = x + 1
        |    IO.println y
        |""".stripMargin.linesIterator.mkString("\n")
    val contents = metadata.appendToCode(code)
    val mainFile = context.writeMain(contents)

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
    context.send(
      Api.Request(
        requestId,
        Api.PushContextRequest(
          contextId,
          Api.StackItem.ExplicitCall(
            Api.MethodPointer(moduleName, "Enso_Test.Test.Main", "main"),
            None,
            Vector()
          )
        )
      )
    )
    context.receive(5) should contain theSameElementsAs Seq(
      Api.Response(requestId, Api.PushContextResponse(contextId)),
      TestMessages.error(
        contextId,
        xId,
        Api.MethodPointer(moduleName, moduleName, "foo"),
        Api.ExpressionUpdate.Payload.DataflowError(Seq())
      ),
      TestMessages.error(
        contextId,
        yId,
        Api.ExpressionUpdate.Payload.DataflowError(Seq())
      ),
      TestMessages.update(
        contextId,
        mainResId,
        Constants.NOTHING
      ),
      context.executionComplete(contextId)
    )
    context.consumeOut shouldEqual Seq("(Error: 9)")

    // Modify the file
    context.send(
      Api.Request(
        Api.EditFileNotification(
          mainFile,
          Seq(
            TextEdit(
              model.Range(model.Position(3, 4), model.Position(3, 17)),
              "10002 - 10000"
            )
          )
        )
      )
    )
    context.receive(3) should contain theSameElementsAs Seq(
      TestMessages.update(
        contextId,
        xId,
        Constants.INTEGER,
        Api.MethodPointer(moduleName, moduleName, "foo")
      ),
      TestMessages.update(contextId, yId, Constants.INTEGER),
      context.executionComplete(contextId)
    )
    context.consumeOut shouldEqual List("3")
  }

  it should "not cache panics" in {
    val contextId  = UUID.randomUUID()
    val requestId  = UUID.randomUUID()
    val moduleName = "Enso_Test.Test.Main"
    val newline    = System.lineSeparator()

    val metadata   = new Metadata
    val xId        = metadata.addItem(15, 20)
    val mainResId  = metadata.addItem(40, 1)
    val x1Id       = metadata.addItem(50, 20)
    val mainRes1Id = metadata.addItem(75, 1)

    val code =
      """main =
        |    x = IO.println "MyError"
        |    x
        |""".stripMargin.linesIterator.mkString("\n")
    val contents = metadata.appendToCode(code)
    val mainFile = context.writeMain(contents)

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
    context.send(
      Api.Request(
        requestId,
        Api.PushContextRequest(
          contextId,
          Api.StackItem.ExplicitCall(
            Api.MethodPointer(moduleName, "Enso_Test.Test.Main", "main"),
            None,
            Vector()
          )
        )
      )
    )
    context.receive(5) should contain theSameElementsAs Seq(
      Api.Response(requestId, Api.PushContextResponse(contextId)),
      Api.Response(
        Api.ExecutionUpdate(
          contextId,
          Seq(
            Api.ExecutionResult.Diagnostic.error(
              "The name IO could not be found.",
              Some(mainFile),
              Some(model.Range(model.Position(1, 8), model.Position(1, 10))),
              None
            )
          )
        )
      ),
      TestMessages.panic(
        contextId,
        xId,
        Api.ExpressionUpdate.Payload.Panic(
          "Compile error: The name IO could not be found.",
          Seq(xId)
        )
      ),
      TestMessages.panic(
        contextId,
        mainResId,
        Api.ExpressionUpdate.Payload.Panic(
          "Compile error: The name IO could not be found.",
          Seq(xId)
        )
      ),
      context.executionComplete(contextId)
    )
    context.consumeOut shouldEqual Seq()

    // Modify the file
    context.send(
      Api.Request(
        Api.EditFileNotification(
          mainFile,
          Seq(
            TextEdit(
              model.Range(model.Position(0, 0), model.Position(0, 0)),
              s"from Standard.Builtins import all$newline$newline"
            )
          )
        )
      )
    )
    context.receive(3) should contain theSameElementsAs Seq(
      TestMessages.update(contextId, x1Id, Constants.NOTHING),
      TestMessages.update(contextId, mainRes1Id, Constants.NOTHING),
      context.executionComplete(contextId)
    )
    context.consumeOut shouldEqual List("MyError")
  }

}
