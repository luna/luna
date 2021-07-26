package org.enso.compiler.test.pass.resolve

import org.enso.compiler.Passes
import org.enso.compiler.context.{InlineContext, ModuleContext}
import org.enso.compiler.core.IR
import org.enso.compiler.pass.resolve.GenerateDocumentation
import org.enso.compiler.pass.{PassConfiguration, PassGroup, PassManager}
import org.enso.compiler.test.CompilerTest
import org.scalatest.Inside
import pprint.pprintln

class GenerateDocumentationTest extends CompilerTest with Inside {

  // === Test Setup ===========================================================

  val passes = new Passes(defaultConfig)

  val precursorPasses: PassGroup =
    passes.getPrecursors(GenerateDocumentation).get

  val passConfiguration: PassConfiguration = PassConfiguration();

  implicit val passManager: PassManager =
    new PassManager(List(precursorPasses), passConfiguration)

  /** Resolves documentation comments in a module.
    *
    * @param ir the module
    */
  implicit class ResolveModule(ir: IR.Module) {

    /** Resolves documentation comments for [[ir]].
      *
      * @param moduleContext the context in which to resolve
      * @return [[ir]], with documentation resolved
      */
    def resolve(implicit moduleContext: ModuleContext): IR.Module = {
      GenerateDocumentation.runModule(ir, moduleContext)
    }
  }

  /** Resolves documentation comments in an expression.
    *
    * @param ir the expression
    */
  implicit class ResolveExpression(ir: IR.Expression) {

    /** Resolves documentation comments for [[ir]].
      *
      * @param inlineContext the context in which to resolve
      * @return [[ir]], with documentation resolved
      */
    def resolve(implicit inlineContext: InlineContext): IR.Expression = {
      GenerateDocumentation.runExpression(ir, inlineContext)
    }
  }

  /** Creates a defaulted module context.
    *
    * @return a defaulted module context
    */
  def mkModuleContext: ModuleContext = {
    buildModuleContext(isGeneratingDocs = true)
  }

  def mkModuleContext2: ModuleContext = {
    buildModuleContext(isGeneratingDocs = false)
  }

  /** Gets documentation metadata from a node.
    * Throws an exception if missing.
    *
    * @param ir the ir to get the doc from.
    * @return the doc assigned to `ir`.
    */
  def getDoc(ir: IR): String = {
    val meta = ir.getMetadata(GenerateDocumentation)
    meta shouldBe defined
    meta.get.documentation
  }

  /** Creates full html doc generator output from the `inner` string, the one
    * that will only change during the test.
    */
  def unfoldedDocumentationForAssertion(inner: String): String =
    s"""<html>
       | <body>
       |   <div class="doc" style="font-size: 13px;">
       |     <div>
       |       <div class="">
       |         $inner
       |       </div>
       |     </div>
       |   </div>
       | </body>
       |</html>""".stripMargin
      .replaceAll(System.lineSeparator(), "")
      .replaceAll(">[ ]+<", "><")

  // === The Tests ============================================================

  "Documentation comments in the top scope" should {
    "be associated with atoms and methods" in {
      implicit val moduleContext: ModuleContext = mkModuleContext
      val ir =
        """## Module Docs
          |
          |## This is doc for My_Atom
          |type My_Atom a b c
          |
          |## This is doc for my_method
          |MyAtom.my_method x = x + this
          |
          |""".stripMargin.preprocessModule.resolve

      ir.bindings.length shouldEqual 2
      ir.bindings(0) shouldBe an[IR.Module.Scope.Definition.Atom]
      ir.bindings(1) shouldBe an[IR.Module.Scope.Definition.Method]

      getDoc(ir.bindings(0)) shouldEqual unfoldedDocumentationForAssertion(
        "<p>This is doc for My<div class=\"Unclosed\"><i>Atom</i></div></p>"
      )
      getDoc(ir.bindings(1)) shouldEqual unfoldedDocumentationForAssertion(
        "<p>This is doc for my<div class=\"Unclosed\"><i>method</i></div></p>"
      )
    }
  }

  "Documentation comments in blocks" should {
    pending
    "be associated with the documented expression in module flow" in {
      implicit val moduleContext: ModuleContext = mkModuleContext
      val ir =
        """
          |method x =
          |    ## Do thing
          |    x + y
          |    ## Do another thing
          |    z = x * y
          |""".stripMargin.preprocessModule.resolve
      val body = ir
        .bindings(0)
        .asInstanceOf[IR.Module.Scope.Definition.Method.Explicit]
        .body
        .asInstanceOf[IR.Function.Lambda]
        .body
        .asInstanceOf[IR.Expression.Block]

      pprintln(body)
      body.expressions.length shouldEqual 1
      getDoc(body.expressions(0)) shouldEqual unfoldedDocumentationForAssertion(
        "<p>Do thing</p>"
      )
      getDoc(body.returnValue) shouldEqual unfoldedDocumentationForAssertion(
        "<p>Do another thing</p>"
      )
    }

    "be associated with the type ascriptions" in {
      implicit val moduleContext: ModuleContext = mkModuleContext
      val ir =
        """
          |method x =
          |    ## Id
          |    f : Any -> Any
          |    f x = x
          |
          |    ## Return thing
          |    f 1
          |""".stripMargin.preprocessModule.resolve
      val body = ir
        .bindings(0)
        .asInstanceOf[IR.Module.Scope.Definition.Method.Explicit]
        .body
        .asInstanceOf[IR.Function.Lambda]
        .body
        .asInstanceOf[IR.Expression.Block]

      body.expressions.length shouldEqual 2
      body.expressions(0) shouldBe an[IR.Application.Operator.Binary]
      getDoc(body.expressions(0)) shouldEqual unfoldedDocumentationForAssertion(
        "<p>Id</p>"
      )
      getDoc(body.returnValue) shouldEqual unfoldedDocumentationForAssertion(
        "<p>Return thing</p>"
      )
    }
  }

  "Documentation in complex type definitions" should {
    pending
    implicit val moduleContext: ModuleContext = mkModuleContext2
    "assign docs to all entities" in {
      val ir =
        """
          |## the type Foo
          |type Foo
          |    ## the constructor Bar
          |    type Bar
          |
          |    ## the included Unit
          |    Unit
          |
          |    ## a method
          |    foo x =
          |        ## a statement
          |        IO.println "foo"
          |        ## the return
          |        0
          |""".stripMargin.preprocessModule.resolve
//      pprintln(ir)
      val tp = ir.bindings(0).asInstanceOf[IR.Module.Scope.Definition.Type]
      getDoc(tp) shouldEqual unfoldedDocumentationForAssertion(
        "<p>the type Foo</p>"
      )
      val t1 = tp.body(0)
      getDoc(t1) shouldEqual unfoldedDocumentationForAssertion(
        "<p>the constructor Bar</p>"
      )
      val t2 = tp.body(1)
      getDoc(t2) shouldEqual unfoldedDocumentationForAssertion(
        "<p>the included Unit</p>"
      )
      val method = tp.body(2).asInstanceOf[IR.Function.Binding]
      getDoc(method) shouldEqual unfoldedDocumentationForAssertion(
        "<p>a method</p>"
      )
      val block = method.body.asInstanceOf[IR.Expression.Block]
      getDoc(
        block.expressions(0)
      ) shouldEqual unfoldedDocumentationForAssertion(
        "<p>a statement</p>"
      )
      getDoc(block.returnValue) shouldEqual unfoldedDocumentationForAssertion(
        "<p>the return</p>"
      )
    }
  }
}
