package org.enso.compiler.context
import org.enso.polyglot.Suggestion
import org.enso.polyglot.data.{These, Tree}
import org.enso.polyglot.runtime.Runtime.Api

object SuggestionDiff {

  /** Compute difference between the two suggestion trees.
    *
    * @param prev the tree before
    * @param current the tree after
    * @return the tree with updates
    */
  def compute(
    prev: Tree[Suggestion],
    current: Tree[Suggestion]
  ): Tree[Api.SuggestionUpdate] =
    Tree
      .zipBy(prev, current)(compare)
      .map(diff)
      .filter {
        case Api.SuggestionUpdate(
              _,
              Api.SuggestionAction.Modify(
                None,
                None,
                None,
                None,
                None,
                None,
                None
              )
            ) =>
          false
        case _ =>
          true
      }

  /** Compare two suggestions for equality.
    *
    * It is used to find a matching suggestion when joining the previous and
    * the current suggestion trees. Since there is no stable identifier between
    * the two trees, we assume that there are no two suggestions in the same
    * module in the same scope with the same name of the same kind.
    */
  private def compare(a: Suggestion, b: Suggestion): Boolean =
    a.name == b.name &&
    a.module == b.module &&
    Suggestion.Kind(a) == Suggestion.Kind(b) &&
    Suggestion.SelfType(a) == Suggestion.SelfType(b)

  /** Build the suggestion update from the result of tree joining operation.
    *
    * @param elem the result of joining two trees
    * @return the suggestion update
    */
  private def diff(elem: These[Suggestion, Suggestion]): Api.SuggestionUpdate =
    elem match {
      case These.Here(e) =>
        Api.SuggestionUpdate(e, Api.SuggestionAction.Remove())
      case These.There(e) =>
        Api.SuggestionUpdate(e, Api.SuggestionAction.Add())
      case These.Both(e1: Suggestion.Module, e2: Suggestion.Module) =>
        diffModules(e1, e2)
      case These.Both(e1: Suggestion.Atom, e2: Suggestion.Atom) =>
        diffAtoms(e1, e2)
      case These.Both(e1: Suggestion.Method, e2: Suggestion.Method) =>
        diffMethods(e1, e2)
      case These.Both(e1: Suggestion.Function, e2: Suggestion.Function) =>
        diffFunctions(e1, e2)
      case These.Both(e1: Suggestion.Local, e2: Suggestion.Local) =>
        diffLocals(e1, e2)
      case These.Both(e1, e2) =>
        // this case is not possible because suggestions are grouped by kind
        // in the `compare` method.
        throw new IllegalStateException(s"Illegal diff: $e1, $e2")
    }

  private def zipArguments(
    as: Seq[Suggestion.Argument],
    bs: Seq[Suggestion.Argument]
  ): Seq[These[Suggestion.Argument, Suggestion.Argument]] = {
    val acc = Vector.newBuilder[These[Suggestion.Argument, Suggestion.Argument]]
    val (b, unmatched) =
      as.foldLeft((acc, bs)) {
        case ((acc, b +: bs), a) =>
          (acc += These.Both(a, b), bs)
        case ((acc, _), a) =>
          (acc += These.Here(a), Seq())
      }
    unmatched.foreach(arg => b += These.There(arg))
    b.result()
  }

  private def diffArguments(
    as: Seq[Suggestion.Argument],
    bs: Seq[Suggestion.Argument]
  ): Seq[Api.SuggestionArgumentAction] =
    zipArguments(as, bs).zipWithIndex
      .map {
        case (These.Here(_), idx)    => Api.SuggestionArgumentAction.Remove(idx)
        case (These.There(a), idx)   => Api.SuggestionArgumentAction.Add(idx, a)
        case (These.Both(a, b), idx) => diffArgument(idx, a, b)
      }
      .filter {
        case Api.SuggestionArgumentAction.Modify(
              _,
              None,
              None,
              None,
              None,
              None
            ) =>
          false
        case _ =>
          true
      }

  private def diffArgument(
    index: Int,
    a: Suggestion.Argument,
    b: Suggestion.Argument
  ): Api.SuggestionArgumentAction = {
    var op = Api.SuggestionArgumentAction.Modify(index)
    if (a.name != b.name) {
      op = op.copy(name = Some(b.name))
    }
    if (a.reprType != b.reprType) {
      op = op.copy(reprType = Some(b.reprType))
    }
    if (a.isSuspended != b.isSuspended) {
      op.copy(isSuspended = Some(b.isSuspended))
    }
    if (a.hasDefault != b.hasDefault) {
      op.copy(hasDefault = Some(b.hasDefault))
    }
    if (a.defaultValue != b.defaultValue) {
      op.copy(defaultValue = Some(b.defaultValue))
    }
    op
  }

  private def diffModules(
    e1: Suggestion.Module,
    e2: Suggestion.Module
  ): Api.SuggestionUpdate = {
    var op = Api.SuggestionAction.Modify()
    if (e1.documentation != e2.documentation) {
      op = op.copy(documentation = Some(e2.documentation))
    }
    Api.SuggestionUpdate(e1, op)
  }

  private def diffAtoms(
    e1: Suggestion.Atom,
    e2: Suggestion.Atom
  ): Api.SuggestionUpdate = {
    var op = Api.SuggestionAction.Modify()
    if (e1.externalId != e2.externalId) {
      op = op.copy(externalId = Some(e2.externalId))
    }
    if (e1.arguments != e2.arguments) {
      op = op.copy(arguments = Some(diffArguments(e1.arguments, e2.arguments)))
    }
    if (e1.returnType != e2.returnType) {
      op = op.copy(returnType = Some(e2.returnType))
    }
    if (e1.documentation != e2.documentation) {
      op = op.copy(documentation = Some(e2.documentation))
    }
    if (e1.documentationHtml != e2.documentationHtml) {
      op = op.copy(documentationHtml = Some(e2.documentationHtml))
    }
    Api.SuggestionUpdate(e1, op)
  }

  private def diffMethods(
    e1: Suggestion.Method,
    e2: Suggestion.Method
  ): Api.SuggestionUpdate = {
    var op = Api.SuggestionAction.Modify()
    if (e1.externalId != e2.externalId) {
      op = op.copy(externalId = Some(e2.externalId))
    }
    if (e1.arguments != e2.arguments) {
      op = op.copy(arguments = Some(diffArguments(e1.arguments, e2.arguments)))
    }
    if (e1.returnType != e2.returnType) {
      op = op.copy(returnType = Some(e2.returnType))
    }
    if (e1.documentation != e2.documentation) {
      op = op.copy(documentation = Some(e2.documentation))
    }
    if (e1.documentationHtml != e2.documentationHtml) {
      op = op.copy(documentationHtml = Some(e2.documentationHtml))
    }
    Api.SuggestionUpdate(e1, op)
  }

  private def diffFunctions(
    e1: Suggestion.Function,
    e2: Suggestion.Function
  ): Api.SuggestionUpdate = {
    var op = Api.SuggestionAction.Modify()
    if (e1.externalId != e2.externalId) {
      op = op.copy(externalId = Some(e2.externalId))
    }
    if (e1.arguments != e2.arguments) {
      op = op.copy(arguments = Some(diffArguments(e1.arguments, e2.arguments)))
    }
    if (e1.returnType != e2.returnType) {
      op = op.copy(returnType = Some(e2.returnType))
    }
    if (e1.scope != e2.scope) {
      op = op.copy(scope = Some(e2.scope))
    }
    Api.SuggestionUpdate(e1, op)
  }

  private def diffLocals(
    e1: Suggestion.Local,
    e2: Suggestion.Local
  ): Api.SuggestionUpdate = {
    var op = Api.SuggestionAction.Modify()
    if (e1.externalId != e2.externalId) {
      op = op.copy(externalId = Some(e2.externalId))
    }
    if (e1.returnType != e2.returnType) {
      op = op.copy(returnType = Some(e2.returnType))
    }
    if (e1.scope != e2.scope) {
      op = op.copy(scope = Some(e2.scope))
    }
    Api.SuggestionUpdate(e1, op)
  }
}
