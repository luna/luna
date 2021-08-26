package org.enso.compiler.test.data

import org.enso.compiler.test.CompilerTest

// TODO [AA] The package repo keeps a concurrent map of QualifiedName.toString
//  The reconstruction takes place in the compiler thread and hence is fine from
//  a concurrency perspective.

// TODO [AA] Do I want the mapping returned from getModuleMap to be mutable? Not sure.

class BindingsMapTest extends CompilerTest {

}
