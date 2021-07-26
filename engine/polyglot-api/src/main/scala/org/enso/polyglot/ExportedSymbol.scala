package org.enso.polyglot

import com.fasterxml.jackson.annotation.{JsonSubTypes, JsonTypeInfo}

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
@JsonSubTypes(
  Array(
    new JsonSubTypes.Type(
      value = classOf[ExportedSymbol.Module],
      name  = "exportedModule"
    ),
    new JsonSubTypes.Type(
      value = classOf[ExportedSymbol.Atom],
      name  = "exportedAtom"
    ),
    new JsonSubTypes.Type(
      value = classOf[ExportedSymbol.Method],
      name  = "exportedMethod"
    )
  )
)
sealed trait ExportedSymbol {
  def module: String

  def name: String

  def kind: Suggestion.Kind
}
object ExportedSymbol {

  /** The module symbol.
    *
    * @param module the module name
    */
  case class Module(module: String) extends ExportedSymbol {

    override def name: String =
      module

    override def kind: Suggestion.Kind =
      Suggestion.Kind.Module
  }

  /** The atom symbol.
    *
    * @param module the module defining this atom
    * @param name the atom name
    */
  case class Atom(module: String, name: String) extends ExportedSymbol {

    override def kind: Suggestion.Kind =
      Suggestion.Kind.Atom
  }

  /** The method symbol.
    *
    * @param module the module defining this method
    * @param name the method name
    */
  case class Method(module: String, name: String) extends ExportedSymbol {

    override def kind: Suggestion.Kind =
      Suggestion.Kind.Method
  }
}
