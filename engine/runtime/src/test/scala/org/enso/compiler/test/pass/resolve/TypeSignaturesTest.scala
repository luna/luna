package org.enso.compiler.test.pass.resolve

import org.enso.compiler.Passes
import org.enso.compiler.context.{FreshNameSupply, InlineContext, ModuleContext}
import org.enso.compiler.core.IR
import org.enso.compiler.pass.{PassConfiguration, PassGroup, PassManager}
import org.enso.compiler.pass.resolve.{
  DocumentationComments,
  ModuleAnnotations,
  TypeSignatures
}
import org.enso.compiler.test.CompilerTest

class TypeSignaturesTest extends CompilerTest {

  // === Test Setup ===========================================================

  val passes = new Passes(defaultConfig)

  val precursorPasses: PassGroup = passes.getPrecursors(TypeSignatures).get

  val passConfiguration: PassConfiguration = PassConfiguration()

  implicit val passManager: PassManager =
    new PassManager(List(precursorPasses), passConfiguration)

  /** Adds an extension method to a module for performing type signature
    * resolution.
    *
    * @param ir the IR to add the extension method to
    */
  implicit class ResolveModule(ir: IR.Module) {

    /** Resolves type signatures in [[ir]].
      *
      * @param moduleContext the context in which resolution is taking place
      * @return [[ir]], with all type signatures resolved
      */
    def resolve(implicit moduleContext: ModuleContext): IR.Module = {
      TypeSignatures.runModule(ir, moduleContext)
    }
  }

  /** Adds an extension method to an expression for performing type signature
    * resolution.
    *
    * @param ir the expression to add the extension method to
    */
  implicit class ResolveExpression(ir: IR.Expression) {

    /** Resolves type signatures in [[ir]].
      *
      * @param inlineContext the context in which resolution is taking place
      * @return [[ir]], with all type signatures resolved
      */
    def resolve(implicit inlineContext: InlineContext): IR.Expression = {
      TypeSignatures.runExpression(ir, inlineContext)
    }
  }

  /** Creates a defaulted module context.
    *
    * @return a defaulted module context
    */
  def mkModuleContext: ModuleContext = {
    buildModuleContext(freshNameSupply = Some(new FreshNameSupply))
  }

  /** Creates a defaulted inline context.
    *
    * @return a defaulted inline context
    */
  def mkInlineContext: InlineContext = {
    buildInlineContext(freshNameSupply = Some(new FreshNameSupply))
  }

  // === The Tests ============================================================

  "Resolution of type signatures in modules" should {
    implicit val ctx: ModuleContext = mkModuleContext

    "associate signatures with method definitions" in {
      val ir =
        """
          |MyAtom.bar : Number -> Suspended Number -> Number
          |MyAtom.bar a b = a + b
          |""".stripMargin.preprocessModule.resolve

      ir.bindings.length shouldEqual 1
      ir.bindings.head.getMetadata(TypeSignatures) shouldBe defined
    }

    "allow dotted paths in type signatures" in {
      val ir =
        """
          |File.with_output_stream : Vector.Vector -> (Output_Stream -> Any ! File_Error) -> Any ! File_Error
          |File.with_output_stream open_options action = undefined
          |""".stripMargin.preprocessModule.resolve

      ir.bindings.length shouldEqual 1
      ir.bindings.head.getMetadata(TypeSignatures) shouldBe defined
    }

    "raise an error if a signature is divorced from its definition" in {
      val ir =
        """
          |MyAtom.quux : Fizz
          |MyAtom.foo : Number -> Number -> Number
          |
          |MyAtom.bar = 1
          |
          |MyAtom.foo a b = a + b
          |
          |MyAtom.baz : Int
          |""".stripMargin.preprocessModule.resolve

      ir.bindings.length shouldEqual 5
      ir.bindings.head shouldBe an[IR.Error.Unexpected.TypeSignature]
      ir.bindings(1) shouldBe an[IR.Error.Unexpected.TypeSignature]
      ir.bindings(2) shouldBe an[IR.Module.Scope.Definition.Method]
      ir.bindings(3) shouldBe an[IR.Module.Scope.Definition.Method]
      ir.bindings(4) shouldBe an[IR.Error.Unexpected.TypeSignature]
    }

    "reattach documentation to method definitions" in {
      val ir =
        """## Module doc
          |
          |## My bar
          |bar : Number -> Number -> Number
          |bar a b = a + b
          |""".stripMargin.preprocessModule.resolve

      ir.bindings.length shouldEqual 1
      ir.bindings.head.getMetadata(TypeSignatures) shouldBe defined
      ir.bindings.head.getMetadata(DocumentationComments) shouldBe defined
    }

    "reattach annotations to method definitions" in {
      val ir =
        """@Builtin_Type
          |bar : Number -> Number -> Number
          |bar a b = a + b
          |""".stripMargin.preprocessModule.resolve

      ir.bindings.length shouldEqual 1
      ir.bindings.head.getMetadata(TypeSignatures) shouldBe defined
      ir.bindings.head.getMetadata(ModuleAnnotations) shouldBe defined
    }

    "work inside type definition bodies" in {
      val ir =
        """
          |type MyType
          |    type MyAtom
          |
          |    ## is atom
          |    is_atom : this -> Boolean
          |    is_atom = true
          |
          |    error_signature : Int
          |""".stripMargin.preprocessModule.resolve

      ir.bindings.length shouldEqual 3
      ir.bindings.head shouldBe an[IR.Module.Scope.Definition.Atom]
      ir.bindings(1) shouldBe an[IR.Module.Scope.Definition.Method]
      ir.bindings(1).getMetadata(TypeSignatures) shouldBe defined
      ir.bindings(1).getMetadata(DocumentationComments) shouldBe defined
      ir.bindings(2) shouldBe an[IR.Error.Unexpected.TypeSignature]
    }

    "recurse into bodies" in {
      val ir =
        """
          |main =
          |    ## An eff
          |    f : A -> A
          |    f a = a
          |
          |    ## Local
          |    h : A
          |    h = 1
          |
          |    f 1
          |""".stripMargin.preprocessModule.resolve.bindings.head
          .asInstanceOf[IR.Module.Scope.Definition.Method]

      val block = ir.body
        .asInstanceOf[IR.Function.Lambda]
        .body
        .asInstanceOf[IR.Expression.Block]

      block.expressions.length shouldEqual 2
      block.expressions.head.getMetadata(TypeSignatures) shouldBe defined
      block.expressions.head.getMetadata(DocumentationComments) shouldBe defined

      block.expressions(1).getMetadata(TypeSignatures) shouldBe defined
      block.expressions(1).getMetadata(DocumentationComments) shouldBe defined
    }
  }

  "Resolution of type signatures for blocks" should {
    implicit val ctx: InlineContext = mkInlineContext

    val ir =
      """
        |block =
        |    ## Doc f
        |    f : Int
        |    f = 0
        |
        |    g : Int
        |    g =
        |        ## Doc inner f
        |        f : Double
        |        f = 0
        |        f 1
        |
        |    bad_sig : Int
        |""".stripMargin.preprocessExpression.get.resolve
        .asInstanceOf[IR.Expression.Binding]

    val block = ir.expression.asInstanceOf[IR.Expression.Block]

    "associate signatures with bindings" in {
      val head = block.expressions.head
      head shouldBe an[IR.Expression.Binding]
      head.getMetadata(TypeSignatures) shouldBe defined
      head.getMetadata(DocumentationComments) shouldBe defined
    }

    "raise an error if a signature is divorced from its definition" in {
      block.returnValue shouldBe an[IR.Error.Unexpected.TypeSignature]
    }

    "work recursively" in {
      val nested = block
        .expressions(1)
        .asInstanceOf[IR.Expression.Binding]
        .expression
        .asInstanceOf[IR.Expression.Block]

      val head = nested.expressions.head
      head shouldBe an[IR.Expression.Binding]
      head.getMetadata(TypeSignatures) shouldBe defined
      head.getMetadata(DocumentationComments) shouldBe defined
    }
  }

  "Resolution of inline type signatures" should {
    implicit val ctx: InlineContext = mkInlineContext

    val ir =
      """
        |f a (b = 1 : Int) : Double
        |""".stripMargin.preprocessExpression.get.resolve

    "associate the signature with the typed expression" in {
      ir shouldBe an[IR.Application.Prefix]
      ir.getMetadata(TypeSignatures) shouldBe defined
    }

    "work recursively" in {
      val arg2Value = ir.asInstanceOf[IR.Application.Prefix].arguments(1).value
      arg2Value shouldBe an[IR.Literal.Number]
    }
  }
}
