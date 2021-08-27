package org.enso.compiler.test.data

import org.enso.compiler.Passes
import org.enso.compiler.context.{FreshNameSupply, ModuleContext}
import org.enso.compiler.core.IR
import org.enso.compiler.pass.{PassConfiguration, PassGroup, PassManager}
import org.enso.compiler.pass.analyse.BindingAnalysis
import org.enso.compiler.test.CompilerTest

// TODO [AA] The package repo keeps a concurrent map of QualifiedName.toString
//  The reconstruction takes place in the compiler thread and hence is fine from
//  a concurrency perspective.

// TODO [AA] Do I want the mapping returned from getModuleMap to be mutable? Not sure.

class BindingsMapTest extends CompilerTest {

  // === Test Setup ===========================================================

  def mkModuleContext: ModuleContext =
    buildModuleContext(
      freshNameSupply = Some(new FreshNameSupply)
    )

  val passes = new Passes(defaultConfig)

  val precursorPasses: PassGroup = passes.getPrecursors(BindingAnalysis).get

  val passConfiguration: PassConfiguration = PassConfiguration()

  implicit val passManager: PassManager =
    new PassManager(List(precursorPasses), passConfiguration)

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
    def analyse(implicit context: ModuleContext): IR.Module = {
      BindingAnalysis.runModule(ir, context)
    }
  }

  // === The Tests ============================================================

  "The BindingsMap's conversion to abstract form" should {
    "convert all module references to abstract form" in {
      pending
    }

    "lose no other information" in {
      pending
    }
  }

  "The BindingsMap's conversion to concrete form" should {
    "convert all module references to concrete form" in {
      pending
    }

    "fail with `None` if any part of the conversion fails" in {
      pending
    }
  }

  "The BindingsMap's conversions" should {
    "be lossless when round-tripping in the same state" in {
      pending
    }

    "fail completely when any link cannot be reformed" in {
      pending
    }
  }

}
