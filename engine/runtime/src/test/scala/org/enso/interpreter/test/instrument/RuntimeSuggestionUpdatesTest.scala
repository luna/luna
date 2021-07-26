package org.enso.interpreter.test.instrument

import java.io.{ByteArrayOutputStream, File}
import java.nio.ByteBuffer
import java.nio.file.{Files, Paths}
import java.util.UUID
import java.util.concurrent.{LinkedBlockingQueue, TimeUnit}

import org.enso.interpreter.runtime.`type`.Constants
import org.enso.pkg.{Package, PackageManager}
import org.enso.polyglot._
import org.enso.polyglot.data.Tree
import org.enso.polyglot.runtime.Runtime.Api
import org.enso.text.{ContentVersion, Sha3_224VersionCalculator}
import org.enso.text.editing.model
import org.enso.text.editing.model.TextEdit
import org.graalvm.polyglot.Context
import org.graalvm.polyglot.io.MessageEndpoint
import org.scalatest.BeforeAndAfterEach
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

@scala.annotation.nowarn("msg=multiarg infix syntax")
class RuntimeSuggestionUpdatesTest
    extends AnyFlatSpec
    with Matchers
    with BeforeAndAfterEach {

  var context: TestContext = _

  class TestContext(packageName: String) {
    var endPoint: MessageEndpoint = _
    val messageQueue: LinkedBlockingQueue[Api.Response] =
      new LinkedBlockingQueue()

    val tmpDir: File = Files.createTempDirectory("enso-test-packages").toFile

    val pkg: Package[File] =
      PackageManager.Default.create(tmpDir, packageName, "Enso_Test")
    val out: ByteArrayOutputStream = new ByteArrayOutputStream()
    val executionContext = new PolyglotContext(
      Context
        .newBuilder(LanguageInfo.ID)
        .allowExperimentalOptions(true)
        .allowAllAccess(true)
        .option(RuntimeOptions.PROJECT_ROOT, pkg.root.getAbsolutePath)
        .option(RuntimeOptions.LOG_LEVEL, "WARNING")
        .option(RuntimeOptions.INTERPRETER_SEQUENTIAL_COMMAND_EXECUTION, "true")
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

  it should "send suggestion updates after file modification" in {
    val contextId  = UUID.randomUUID()
    val requestId  = UUID.randomUUID()
    val moduleName = "Enso_Test.Test.Main"

    val code =
      """from Standard.Builtins import all
        |
        |main = IO.println "Hello World!"
        |""".stripMargin.linesIterator.mkString("\n")
    val version  = contentsVersion(code)
    val mainFile = context.writeMain(code)

    // create context
    context.send(Api.Request(requestId, Api.CreateContextRequest(contextId)))
    context.receive shouldEqual Some(
      Api.Response(requestId, Api.CreateContextResponse(contextId))
    )

    // open file
    context.send(
      Api.Request(Api.OpenFileNotification(mainFile, code))
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
    context.receive(3) should contain theSameElementsAs Seq(
      Api.Response(requestId, Api.PushContextResponse(contextId)),
      Api.Response(
        Api.SuggestionsDatabaseModuleUpdateNotification(
          module  = moduleName,
          version = version,
          actions = Vector(Api.SuggestionsDatabaseAction.Clean(moduleName)),
          exports = Vector(),
          updates = Tree.Root(
            Vector(
              Tree.Node(
                Api.SuggestionUpdate(
                  Suggestion.Module(
                    moduleName,
                    None,
                    None
                  ),
                  Api.SuggestionAction.Add()
                ),
                Vector()
              ),
              Tree.Node(
                Api.SuggestionUpdate(
                  Suggestion.Method(
                    None,
                    moduleName,
                    "main",
                    List(
                      Suggestion
                        .Argument(
                          "this",
                          "Enso_Test.Test.Main",
                          false,
                          false,
                          None
                        )
                    ),
                    "Enso_Test.Test.Main",
                    Constants.ANY,
                    None,
                    None
                  ),
                  Api.SuggestionAction.Add()
                ),
                Vector()
              )
            )
          )
        )
      ),
      context.executionComplete(contextId)
    )
    context.consumeOut shouldEqual List("Hello World!")

    // Modify the file
    context.send(
      Api.Request(
        Api.EditFileNotification(
          mainFile,
          Seq(
            TextEdit(
              model.Range(model.Position(2, 6), model.Position(2, 32)),
              "\n    x = 42\n    IO.println x"
            )
          )
        )
      )
    )
    context.receive(2) should contain theSameElementsAs Seq(
      Api.Response(
        Api.SuggestionsDatabaseModuleUpdateNotification(
          module = moduleName,
          version = contentsVersion(
            """from Standard.Builtins import all
              |
              |main =
              |    x = 42
              |    IO.println x
              |""".stripMargin.linesIterator.mkString("\n")
          ),
          actions = Vector(),
          exports = Vector(),
          updates = Tree.Root(
            Vector(
              Tree.Node(
                Api.SuggestionUpdate(
                  Suggestion.Method(
                    None,
                    moduleName,
                    "main",
                    List(
                      Suggestion
                        .Argument(
                          "this",
                          "Enso_Test.Test.Main",
                          false,
                          false,
                          None
                        )
                    ),
                    "Enso_Test.Test.Main",
                    Constants.ANY,
                    None,
                    None
                  ),
                  Api.SuggestionAction.Modify()
                ),
                Vector(
                  Tree.Node(
                    Api.SuggestionUpdate(
                      Suggestion.Local(
                        None,
                        moduleName,
                        "x",
                        Constants.ANY,
                        Suggestion.Scope(
                          Suggestion.Position(2, 6),
                          Suggestion.Position(4, 16)
                        )
                      ),
                      Api.SuggestionAction.Add()
                    ),
                    Vector()
                  )
                )
              )
            )
          )
        )
      ),
      context.executionComplete(contextId)
    )
    context.consumeOut shouldEqual List("42")

    // Modify the file
    context.send(
      Api.Request(
        Api.EditFileNotification(
          mainFile,
          Seq(
            TextEdit(
              model.Range(model.Position(3, 10), model.Position(3, 10)),
              "\n    y = 9"
            ),
            TextEdit(
              model.Range(model.Position(5, 15), model.Position(5, 16)),
              "x+y"
            )
          )
        )
      )
    )
    context.receive(2) should contain theSameElementsAs Seq(
      Api.Response(
        Api.SuggestionsDatabaseModuleUpdateNotification(
          module = moduleName,
          version = contentsVersion(
            """from Standard.Builtins import all
              |
              |main =
              |    x = 42
              |    y = 9
              |    IO.println x+y
              |""".stripMargin.linesIterator.mkString("\n")
          ),
          actions = Vector(),
          exports = Vector(),
          updates = Tree.Root(
            Vector(
              Tree.Node(
                Api.SuggestionUpdate(
                  Suggestion.Method(
                    None,
                    moduleName,
                    "main",
                    List(
                      Suggestion
                        .Argument(
                          "this",
                          "Enso_Test.Test.Main",
                          false,
                          false,
                          None
                        )
                    ),
                    "Enso_Test.Test.Main",
                    Constants.ANY,
                    None,
                    None
                  ),
                  Api.SuggestionAction.Modify()
                ),
                Vector(
                  Tree.Node(
                    Api.SuggestionUpdate(
                      Suggestion.Local(
                        None,
                        moduleName,
                        "x",
                        Constants.ANY,
                        Suggestion.Scope(
                          Suggestion.Position(2, 6),
                          Suggestion.Position(4, 16)
                        )
                      ),
                      Api.SuggestionAction.Modify(scope =
                        Some(
                          Suggestion.Scope(
                            Suggestion.Position(2, 6),
                            Suggestion.Position(5, 18)
                          )
                        )
                      )
                    ),
                    Vector()
                  ),
                  Tree.Node(
                    Api.SuggestionUpdate(
                      Suggestion.Local(
                        None,
                        moduleName,
                        "y",
                        Constants.ANY,
                        Suggestion.Scope(
                          Suggestion.Position(2, 6),
                          Suggestion.Position(5, 18)
                        )
                      ),
                      Api.SuggestionAction.Add()
                    ),
                    Vector()
                  )
                )
              )
            )
          )
        )
      ),
      context.executionComplete(contextId)
    )
    context.consumeOut shouldEqual List("51")

    // Modify the file
    context.send(
      Api.Request(
        Api.EditFileNotification(
          mainFile,
          Seq(
            TextEdit(
              model.Range(model.Position(3, 10), model.Position(3, 10)),
              "\n    y : Number"
            )
          )
        )
      )
    )
    context.receive(2) should contain theSameElementsAs Seq(
      Api.Response(
        Api.SuggestionsDatabaseModuleUpdateNotification(
          module = moduleName,
          version = contentsVersion(
            """from Standard.Builtins import all
              |
              |main =
              |    x = 42
              |    y : Number
              |    y = 9
              |    IO.println x+y
              |""".stripMargin.linesIterator.mkString("\n")
          ),
          actions = Vector(),
          exports = Vector(),
          updates = Tree.Root(
            Vector(
              Tree.Node(
                Api.SuggestionUpdate(
                  Suggestion.Method(
                    None,
                    moduleName,
                    "main",
                    List(
                      Suggestion
                        .Argument(
                          "this",
                          "Enso_Test.Test.Main",
                          false,
                          false,
                          None
                        )
                    ),
                    "Enso_Test.Test.Main",
                    Constants.ANY,
                    None,
                    None
                  ),
                  Api.SuggestionAction.Modify()
                ),
                Vector(
                  Tree.Node(
                    Api.SuggestionUpdate(
                      Suggestion.Local(
                        None,
                        moduleName,
                        "x",
                        Constants.ANY,
                        Suggestion.Scope(
                          Suggestion.Position(2, 6),
                          Suggestion.Position(5, 18)
                        )
                      ),
                      Api.SuggestionAction.Modify(scope =
                        Some(
                          Suggestion.Scope(
                            Suggestion.Position(2, 6),
                            Suggestion.Position(6, 18)
                          )
                        )
                      )
                    ),
                    Vector()
                  ),
                  Tree.Node(
                    Api.SuggestionUpdate(
                      Suggestion.Local(
                        None,
                        moduleName,
                        "y",
                        Constants.ANY,
                        Suggestion.Scope(
                          Suggestion.Position(2, 6),
                          Suggestion.Position(5, 18)
                        )
                      ),
                      Api.SuggestionAction.Modify(
                        returnType = Some(Constants.NUMBER),
                        scope = Some(
                          Suggestion.Scope(
                            Suggestion.Position(2, 6),
                            Suggestion.Position(6, 18)
                          )
                        )
                      )
                    ),
                    Vector()
                  )
                )
              )
            )
          )
        )
      ),
      context.executionComplete(contextId)
    )
    context.consumeOut shouldEqual List("51")

    // Modify the file
    context.send(
      Api.Request(
        Api.EditFileNotification(
          mainFile,
          Seq(
            TextEdit(
              model.Range(model.Position(1, 0), model.Position(1, 0)),
              "\nfoo x = x * 10\n"
            )
          )
        )
      )
    )
    context.receive(2) should contain theSameElementsAs Seq(
      Api.Response(
        Api.SuggestionsDatabaseModuleUpdateNotification(
          module = moduleName,
          version = contentsVersion(
            """from Standard.Builtins import all
              |
              |foo x = x * 10
              |
              |main =
              |    x = 42
              |    y : Number
              |    y = 9
              |    IO.println x+y
              |""".stripMargin.linesIterator.mkString("\n")
          ),
          actions = Vector(),
          exports = Vector(),
          updates = Tree.Root(
            Vector(
              Tree.Node(
                Api.SuggestionUpdate(
                  Suggestion.Method(
                    None,
                    moduleName,
                    "main",
                    List(
                      Suggestion
                        .Argument(
                          "this",
                          "Enso_Test.Test.Main",
                          false,
                          false,
                          None
                        )
                    ),
                    "Enso_Test.Test.Main",
                    Constants.ANY,
                    None,
                    None
                  ),
                  Api.SuggestionAction.Modify()
                ),
                Vector(
                  Tree.Node(
                    Api.SuggestionUpdate(
                      Suggestion.Local(
                        None,
                        moduleName,
                        "x",
                        Constants.ANY,
                        Suggestion.Scope(
                          Suggestion.Position(2, 6),
                          Suggestion.Position(6, 18)
                        )
                      ),
                      Api.SuggestionAction.Modify(scope =
                        Some(
                          Suggestion.Scope(
                            Suggestion.Position(4, 6),
                            Suggestion.Position(8, 18)
                          )
                        )
                      )
                    ),
                    Vector()
                  ),
                  Tree.Node(
                    Api.SuggestionUpdate(
                      Suggestion.Local(
                        None,
                        moduleName,
                        "y",
                        Constants.NUMBER,
                        Suggestion.Scope(
                          Suggestion.Position(2, 6),
                          Suggestion.Position(6, 18)
                        )
                      ),
                      Api.SuggestionAction.Modify(scope =
                        Some(
                          Suggestion.Scope(
                            Suggestion.Position(4, 6),
                            Suggestion.Position(8, 18)
                          )
                        )
                      )
                    ),
                    Vector()
                  )
                )
              ),
              Tree.Node(
                Api.SuggestionUpdate(
                  Suggestion.Method(
                    None,
                    moduleName,
                    "foo",
                    List(
                      Suggestion
                        .Argument(
                          "this",
                          "Enso_Test.Test.Main",
                          false,
                          false,
                          None
                        ),
                      Suggestion
                        .Argument("x", Constants.ANY, false, false, None)
                    ),
                    "Enso_Test.Test.Main",
                    Constants.ANY,
                    None,
                    None
                  ),
                  Api.SuggestionAction.Add()
                ),
                Vector()
              )
            )
          )
        )
      ),
      context.executionComplete(contextId)
    )
    context.consumeOut shouldEqual List("51")

    // Modify the file
    context.send(
      Api.Request(
        Api.EditFileNotification(
          mainFile,
          Seq(
            TextEdit(
              model.Range(model.Position(2, 4), model.Position(2, 14)),
              "a b = a * b"
            )
          )
        )
      )
    )
    context.receive(2) should contain theSameElementsAs Seq(
      Api.Response(
        Api.SuggestionsDatabaseModuleUpdateNotification(
          module = moduleName,
          version = contentsVersion(
            """from Standard.Builtins import all
              |
              |foo a b = a * b
              |
              |main =
              |    x = 42
              |    y : Number
              |    y = 9
              |    IO.println x+y
              |""".stripMargin.linesIterator.mkString("\n")
          ),
          actions = Vector(),
          exports = Vector(),
          updates = Tree.Root(
            Vector(
              Tree.Node(
                Api.SuggestionUpdate(
                  Suggestion.Method(
                    None,
                    moduleName,
                    "foo",
                    List(
                      Suggestion
                        .Argument(
                          "this",
                          "Enso_Test.Test.Main",
                          false,
                          false,
                          None
                        ),
                      Suggestion
                        .Argument("x", Constants.ANY, false, false, None)
                    ),
                    "Enso_Test.Test.Main",
                    Constants.ANY,
                    None,
                    None
                  ),
                  Api.SuggestionAction.Modify(
                    None,
                    Some(
                      List(
                        Api.SuggestionArgumentAction
                          .Modify(1, Some("a"), None, None, None, None),
                        Api.SuggestionArgumentAction.Add(
                          2,
                          Suggestion
                            .Argument("b", Constants.ANY, false, false, None)
                        )
                      )
                    ),
                    None,
                    None,
                    None
                  )
                ),
                Vector()
              )
            )
          )
        )
      ),
      context.executionComplete(contextId)
    )
    context.consumeOut shouldEqual List("51")
  }

  it should "index overloaded functions" in {
    val contextId  = UUID.randomUUID()
    val requestId  = UUID.randomUUID()
    val moduleName = "Enso_Test.Test.Main"

    val contents =
      """from Standard.Builtins import all
        |
        |main =
        |    x = 15.overloaded 1
        |    "foo".overloaded 2
        |    10.overloaded x
        |    Nothing
        |
        |Text.overloaded arg = arg + 1
        |Number.overloaded arg = arg + 2
        |""".stripMargin.linesIterator.mkString("\n")
    val version  = contentsVersion(contents)
    val mainFile = context.writeMain(contents)

    // create context
    context.send(Api.Request(requestId, Api.CreateContextRequest(contextId)))
    context.receive shouldEqual Some(
      Api.Response(requestId, Api.CreateContextResponse(contextId))
    )

    // open file
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
    context.receive(3) should contain theSameElementsAs Seq(
      Api.Response(requestId, Api.PushContextResponse(contextId)),
      Api.Response(
        Api.SuggestionsDatabaseModuleUpdateNotification(
          module  = moduleName,
          version = version,
          actions = Vector(Api.SuggestionsDatabaseAction.Clean(moduleName)),
          exports = Vector(),
          updates = Tree.Root(
            Vector(
              Tree.Node(
                Api.SuggestionUpdate(
                  Suggestion.Module(
                    moduleName,
                    None,
                    None
                  ),
                  Api.SuggestionAction.Add()
                ),
                Vector()
              ),
              Tree.Node(
                Api.SuggestionUpdate(
                  Suggestion.Method(
                    None,
                    moduleName,
                    "main",
                    Seq(
                      Suggestion
                        .Argument(
                          "this",
                          "Enso_Test.Test.Main",
                          false,
                          false,
                          None
                        )
                    ),
                    "Enso_Test.Test.Main",
                    Constants.ANY,
                    None,
                    None
                  ),
                  Api.SuggestionAction.Add()
                ),
                Vector(
                  Tree.Node(
                    Api.SuggestionUpdate(
                      Suggestion.Local(
                        None,
                        moduleName,
                        "x",
                        Constants.ANY,
                        Suggestion.Scope(
                          Suggestion.Position(2, 6),
                          Suggestion.Position(7, 0)
                        )
                      ),
                      Api.SuggestionAction.Add()
                    ),
                    Vector()
                  )
                )
              ),
              Tree.Node(
                Api.SuggestionUpdate(
                  Suggestion.Method(
                    None,
                    moduleName,
                    "overloaded",
                    Seq(
                      Suggestion.Argument(
                        "this",
                        Constants.TEXT,
                        false,
                        false,
                        None
                      ),
                      Suggestion
                        .Argument("arg", Constants.ANY, false, false, None)
                    ),
                    Constants.TEXT,
                    Constants.ANY,
                    None,
                    None
                  ),
                  Api.SuggestionAction.Add()
                ),
                Vector()
              ),
              Tree.Node(
                Api.SuggestionUpdate(
                  Suggestion.Method(
                    None,
                    moduleName,
                    "overloaded",
                    Seq(
                      Suggestion.Argument(
                        "this",
                        Constants.NUMBER,
                        false,
                        false,
                        None
                      ),
                      Suggestion
                        .Argument("arg", Constants.ANY, false, false, None)
                    ),
                    Constants.NUMBER,
                    Constants.ANY,
                    None,
                    None
                  ),
                  Api.SuggestionAction.Add()
                ),
                Vector()
              )
            )
          )
        )
      ),
      context.executionComplete(contextId)
    )
  }

  it should "index exports" in {
    val contextId  = UUID.randomUUID()
    val requestId  = UUID.randomUUID()
    val moduleName = "Enso_Test.Test.Main"

    val mainCode =
      """from Standard.Builtins import all
        |
        |import Enso_Test.Test.A
        |from Enso_Test.Test.A export all
        |
        |main = IO.println "Hello World!"
        |""".stripMargin.linesIterator.mkString("\n")
    val aCode =
      """from Standard.Builtins import all
        |
        |type MyType
        |    type MkA a
        |
        |Integer.fortytwo = 42
        |
        |hello = "Hello World!"
        |""".stripMargin.linesIterator.mkString("\n")

    val mainVersion = contentsVersion(mainCode)
    val mainFile    = context.writeMain(mainCode)
    val aVersion    = contentsVersion(aCode)
    val aFile       = context.writeInSrcDir("A", aCode)

    // create context
    context.send(Api.Request(requestId, Api.CreateContextRequest(contextId)))
    context.receive shouldEqual Some(
      Api.Response(requestId, Api.CreateContextResponse(contextId))
    )

    // open files
    context.send(
      Api.Request(Api.OpenFileNotification(mainFile, mainCode))
    )
    context.receiveNone shouldEqual None
    context.send(
      Api.Request(Api.OpenFileNotification(aFile, aCode))
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
        Api.SuggestionsDatabaseModuleUpdateNotification(
          module  = "Enso_Test.Test.A",
          version = aVersion,
          actions =
            Vector(Api.SuggestionsDatabaseAction.Clean("Enso_Test.Test.A")),
          exports = Vector(),
          updates = Tree.Root(
            Vector(
              Tree.Node(
                Api.SuggestionUpdate(
                  Suggestion.Module("Enso_Test.Test.A", None, None),
                  Api.SuggestionAction.Add()
                ),
                Vector()
              ),
              Tree.Node(
                Api.SuggestionUpdate(
                  Suggestion.Atom(
                    None,
                    "Enso_Test.Test.A",
                    "MkA",
                    List(
                      Suggestion
                        .Argument("a", Constants.ANY, false, false, None)
                    ),
                    "Enso_Test.Test.A.MkA",
                    None,
                    None
                  ),
                  Api.SuggestionAction.Add()
                ),
                Vector()
              ),
              Tree.Node(
                Api.SuggestionUpdate(
                  Suggestion.Method(
                    None,
                    "Enso_Test.Test.A",
                    "a",
                    List(
                      Suggestion
                        .Argument(
                          "this",
                          "Enso_Test.Test.A.MkA",
                          false,
                          false,
                          None
                        )
                    ),
                    "Enso_Test.Test.A.MkA",
                    Constants.ANY,
                    None,
                    None
                  ),
                  Api.SuggestionAction.Add()
                ),
                Vector()
              ),
              Tree.Node(
                Api.SuggestionUpdate(
                  Suggestion.Method(
                    None,
                    "Enso_Test.Test.A",
                    "fortytwo",
                    List(
                      Suggestion.Argument(
                        "this",
                        Constants.INTEGER,
                        false,
                        false,
                        None
                      )
                    ),
                    Constants.INTEGER,
                    Constants.ANY,
                    None,
                    None
                  ),
                  Api.SuggestionAction.Add()
                ),
                Vector()
              ),
              Tree.Node(
                Api.SuggestionUpdate(
                  Suggestion.Method(
                    None,
                    "Enso_Test.Test.A",
                    "hello",
                    List(
                      Suggestion.Argument(
                        "this",
                        "Enso_Test.Test.A",
                        false,
                        false,
                        None
                      )
                    ),
                    "Enso_Test.Test.A",
                    Constants.ANY,
                    None,
                    None
                  ),
                  Api.SuggestionAction.Add()
                ),
                Vector()
              )
            )
          )
        )
      ),
      Api.Response(
        Api.SuggestionsDatabaseModuleUpdateNotification(
          module  = moduleName,
          version = mainVersion,
          actions = Vector(Api.SuggestionsDatabaseAction.Clean(moduleName)),
          exports = Vector(
            Api.ExportsUpdate(
              ModuleExports(
                "Enso_Test.Test.Main",
                Set(
                  ExportedSymbol.Atom("Enso_Test.Test.A", "MkA"),
                  ExportedSymbol.Method("Enso_Test.Test.A", "hello")
                )
              ),
              Api.ExportsAction.Add()
            )
          ),
          updates = Tree.Root(
            Vector(
              Tree.Node(
                Api.SuggestionUpdate(
                  Suggestion.Module(
                    moduleName,
                    None,
                    None
                  ),
                  Api.SuggestionAction.Add()
                ),
                Vector()
              ),
              Tree.Node(
                Api.SuggestionUpdate(
                  Suggestion.Method(
                    None,
                    moduleName,
                    "main",
                    List(
                      Suggestion
                        .Argument(
                          "this",
                          "Enso_Test.Test.Main",
                          false,
                          false,
                          None
                        )
                    ),
                    "Enso_Test.Test.Main",
                    Constants.ANY,
                    None,
                    None
                  ),
                  Api.SuggestionAction.Add()
                ),
                Vector()
              )
            )
          )
        )
      ),
      context.executionComplete(contextId)
    )
    context.consumeOut shouldEqual List("Hello World!")

    // Modify the file
    context.send(
      Api.Request(
        Api.EditFileNotification(
          mainFile,
          Seq(
            TextEdit(
              model.Range(model.Position(3, 32), model.Position(3, 32)),
              " hiding hello"
            )
          )
        )
      )
    )
    context.receive(2) should contain theSameElementsAs Seq(
      Api.Response(
        Api.SuggestionsDatabaseModuleUpdateNotification(
          module = moduleName,
          version = contentsVersion(
            """from Standard.Builtins import all
              |
              |import Enso_Test.Test.A
              |from Enso_Test.Test.A export all hiding hello
              |
              |main = IO.println "Hello World!"
              |""".stripMargin.linesIterator.mkString("\n")
          ),
          actions = Vector(),
          exports = Vector(
            Api.ExportsUpdate(
              ModuleExports(
                "Enso_Test.Test.Main",
                Set(ExportedSymbol.Method("Enso_Test.Test.A", "hello"))
              ),
              Api.ExportsAction.Remove()
            )
          ),
          updates = Tree.Root(Vector())
        )
      ),
      context.executionComplete(contextId)
    )
    context.consumeOut shouldEqual List("Hello World!")

    // Modify the file
    context.send(
      Api.Request(
        Api.EditFileNotification(
          mainFile,
          Seq(
            TextEdit(
              model.Range(model.Position(2, 0), model.Position(5, 0)),
              ""
            )
          )
        )
      )
    )
    context.receive(2) should contain theSameElementsAs Seq(
      Api.Response(
        Api.SuggestionsDatabaseModuleUpdateNotification(
          module = moduleName,
          version = contentsVersion(
            """from Standard.Builtins import all
              |
              |main = IO.println "Hello World!"
              |""".stripMargin.linesIterator.mkString("\n")
          ),
          actions = Vector(),
          exports = Vector(
            Api.ExportsUpdate(
              ModuleExports(
                "Enso_Test.Test.Main",
                Set(ExportedSymbol.Atom("Enso_Test.Test.A", "MkA"))
              ),
              Api.ExportsAction.Remove()
            )
          ),
          updates = Tree.Root(Vector())
        )
      ),
      context.executionComplete(contextId)
    )
    context.consumeOut shouldEqual List("Hello World!")
  }

}
