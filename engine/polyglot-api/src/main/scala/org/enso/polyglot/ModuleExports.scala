package org.enso.polyglot

/** The module exporting a set of symbols.
  *
  * @param module the module name
  * @param symbols the set of exported symbols
  */
case class ModuleExports(module: String, symbols: Set[ExportedSymbol])
