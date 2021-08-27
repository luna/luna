package org.enso.compiler.test.pass.resolve

import org.enso.compiler.Passes
import org.enso.compiler.context.{FreshNameSupply, ModuleContext}
import org.enso.compiler.core.IR
import org.enso.compiler.data.BindingsMap.{
  Cons,
  ModuleReference,
  Resolution,
  ResolvedConstructor,
  ResolvedModule
}
import org.enso.compiler.pass.resolve.UppercaseNames
import org.enso.compiler.pass.{PassConfiguration, PassGroup, PassManager}
import org.enso.compiler.phase.ExportsResolution
import org.enso.compiler.test.CompilerTest

class UppercaseNamesTest extends CompilerTest {

  // === Test Setup ===========================================================

  def mkModuleContext: ModuleContext =
    buildModuleContext(
      freshNameSupply = Some(new FreshNameSupply)
    )

  val passes = new Passes(defaultConfig)

  val group1 = passes.moduleDiscoveryPasses
  val group2 = new PassGroup(
    passes.functionBodyPasses.passes.takeWhile(_ != UppercaseNames)
  )

  val passConfiguration: PassConfiguration = PassConfiguration()

  implicit val passManager: PassManager =
    new PassManager(List(group1, group2), passConfiguration)

  /** Adds an extension method to analyse an Enso module.
    *
    * @param ir the ir to analyse
    */
  implicit class AnalyseModule(ir: IR.Module) {

    /** Performs tail call analysis on [[ir]].
      *
      * @param context the module context in which analysis takes place
      * @return [[ir]], with tail call analysis metadata attached
      */
    def analyse(implicit context: ModuleContext) = {
      UppercaseNames.runModule(ir, context)
    }
  }

  // === The Tests ============================================================

  "Method definition resolution" should {
    implicit val ctx: ModuleContext = mkModuleContext

    val code         = """
                 |main =
                 |    x1 = My_Cons 1 2 3
                 |    x2 = Constant
                 |    x3 = Add_One 1
                 |    x4 = Test_Module.My_Cons 1 2 3
                 |    x5 = Does_Not_Exist 32
                 |    0
                 |
                 |type My_Cons a b c
                 |
                 |constant = 2
                 |
                 |add_one x = x + 1
                 |
                 |""".stripMargin
    val parsed       = code.toIrModule
    val moduleMapped = passManager.runPassesOnModule(parsed, ctx, group1)
    ctx.module.unsafeSetIr(moduleMapped)
    new ExportsResolution().run(List(ctx.module))
    val allPrecursors = passManager.runPassesOnModule(moduleMapped, ctx, group2)
    val ir            = allPrecursors.analyse

    val bodyExprs = ir
      .bindings(0)
      .asInstanceOf[IR.Module.Scope.Definition.Method.Explicit]
      .body
      .asInstanceOf[IR.Function.Lambda]
      .body
      .asInstanceOf[IR.Expression.Block]
      .expressions
      .map(expr => expr.asInstanceOf[IR.Expression.Binding].expression)

    "resolve visible constructors" in {
      bodyExprs(0)
        .asInstanceOf[IR.Application.Prefix]
        .function
        .getMetadata(UppercaseNames) shouldEqual Some(
        Resolution(
          ResolvedConstructor(
            ModuleReference.Concrete(ctx.module),
            Cons("My_Cons", 3)
          )
        )
      )
    }

    "resolve uppercase method names to applications" in {
      val expr = bodyExprs(1)
      expr shouldBe an[IR.Application.Prefix]
      val app = expr.asInstanceOf[IR.Application.Prefix]
      app.function.asInstanceOf[IR.Name.Literal].name shouldEqual "constant"
      app.arguments.length shouldEqual 1
      app.arguments(0).value.getMetadata(UppercaseNames) shouldEqual Some(
        Resolution(ResolvedModule(ModuleReference.Concrete(ctx.module)))
      )
    }

    "resolve uppercase method names in applications by adding the self argument" in {
      val expr = bodyExprs(2)
      expr shouldBe an[IR.Application.Prefix]
      val app = expr.asInstanceOf[IR.Application.Prefix]
      app.function.asInstanceOf[IR.Name.Literal].name shouldEqual "add_one"
      app.arguments.length shouldEqual 2
      app.arguments(0).value.getMetadata(UppercaseNames) shouldEqual Some(
        Resolution(ResolvedModule(ModuleReference.Concrete(ctx.module)))
      )
    }

    "resolve qualified uses of constructors into a simplified form when possible" in {
      val app = bodyExprs(3).asInstanceOf[IR.Application.Prefix]
      app.arguments.length shouldBe 3
      app.function.getMetadata(UppercaseNames) shouldEqual Some(
        Resolution(
          ResolvedConstructor(
            ModuleReference.Concrete(ctx.module),
            Cons("My_Cons", 3)
          )
        )
      )
    }

    "indicate resolution failures" in {
      val app = bodyExprs(4).asInstanceOf[IR.Application.Prefix]
      app.function shouldBe an[IR.Error.Resolution]
    }
  }
}
