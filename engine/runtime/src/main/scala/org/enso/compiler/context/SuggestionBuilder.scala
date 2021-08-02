package org.enso.compiler.context

import org.enso.compiler.core.IR
import org.enso.compiler.data.BindingsMap
import org.enso.compiler.pass.analyse.BindingAnalysis
import org.enso.compiler.pass.resolve.{
  DocumentationComments,
  MethodDefinitions,
  TypeSignatures
}
import org.enso.docs.generator.DocParserWrapper
import org.enso.interpreter.runtime.`type`.Constants
import org.enso.pkg.QualifiedName
import org.enso.polyglot.Suggestion
import org.enso.polyglot.data.Tree
import org.enso.syntax.text.Location
import org.enso.text.editing.IndexedSource

import scala.collection.mutable

/** Module that extracts [[Suggestion]] entries from the [[IR]].
  *
  * @param source the text source
  * @tparam A the type of the text source
  */
final class SuggestionBuilder[A: IndexedSource](val source: A) {

  import SuggestionBuilder._

  /** Build suggestions from the given `ir`.
    *
    * @param module the module name
    * @param ir the input `IR`
    * @return the tree of suggestion entries extracted from the given `IR`
    */
  def build(module: QualifiedName, ir: IR): Tree.Root[Suggestion] = {
    type TreeBuilder =
      mutable.Builder[Tree.Node[Suggestion], Vector[Tree.Node[Suggestion]]]
    val bindings = ir.getMetadata(BindingAnalysis)

    def go(tree: TreeBuilder, scope: Scope): Vector[Tree.Node[Suggestion]] = {
      if (scope.queue.isEmpty) {
        tree.result()
      } else {
        val ir  = scope.queue.dequeue()
        val doc = ir.getMetadata(DocumentationComments).map(_.documentation)
        ir match {
          case IR.Module.Scope.Definition.Atom(name, arguments, _, _, _) =>
            val suggestions =
              buildAtom(bindings, module, name.name, arguments, doc)
            go(tree ++= suggestions.map(Tree.Node(_, Vector())), scope)

          case IR.Module.Scope.Definition.Method
                .Explicit(
                  IR.Name.MethodReference(typePtr, methodName, _, _, _),
                  IR.Function.Lambda(args, body, _, _, _, _),
                  _,
                  _,
                  _
                ) =>
            val typeSignature = ir.getMetadata(TypeSignatures)
            val selfTypeOpt = typePtr
              .getMetadata(MethodDefinitions)
              .flatMap(buildSelfType)
            val methodOpt = selfTypeOpt.map { selfType =>
              buildMethod(
                body.getExternalId,
                module,
                methodName,
                selfType,
                args,
                doc,
                typeSignature,
                bindings
              )
            }
            val subforest = go(
              Vector.newBuilder,
              Scope(body.children, body.location)
            )
            go(tree ++= methodOpt.map(Tree.Node(_, subforest)), scope)

          case IR.Expression.Binding(
                name,
                IR.Function.Lambda(args, body, _, _, _, _),
                _,
                _,
                _
              ) if name.location.isDefined =>
            val typeSignature = ir.getMetadata(TypeSignatures)
            val function = buildFunction(
              body.getExternalId,
              module,
              name,
              args,
              scope.location.get,
              typeSignature,
              bindings
            )
            val subforest = go(
              Vector.newBuilder,
              Scope(body.children, body.location)
            )
            go(tree += Tree.Node(function, subforest), scope)

          case IR.Expression.Binding(name, expr, _, _, _)
              if name.location.isDefined =>
            val typeSignature = ir.getMetadata(TypeSignatures)
            val local = buildLocal(
              expr.getExternalId,
              module,
              name.name,
              scope.location.get,
              typeSignature,
              bindings
            )
            val subforest = go(
              Vector.newBuilder,
              Scope(expr.children, expr.location)
            )
            go(tree += Tree.Node(local, subforest), scope)

          case _ =>
            go(tree, scope)
        }
      }
    }

    val builder: TreeBuilder = Vector.newBuilder
    builder += Tree.Node(
      buildModule(
        module,
        ir.getMetadata(DocumentationComments).map(_.documentation)
      ),
      Vector()
    )

    Tree.Root(
      go(builder, Scope(ir.children, ir.location))
    )
  }

  /** Build a method suggestion. */
  private def buildMethod(
    externalId: Option[IR.ExternalId],
    module: QualifiedName,
    name: IR.Name,
    selfType: QualifiedName,
    args: Seq[IR.DefinitionArgument],
    doc: Option[String],
    typeSignature: Option[TypeSignatures.Metadata],
    bindings: Option[BindingAnalysis.Metadata]
  ): Suggestion.Method = {
    val typeSig = buildTypeSignatureFromMetadata(typeSignature, bindings)
    val (methodArgs, returnTypeDef) =
      buildMethodArguments(args, typeSig, selfType)
    Suggestion.Method(
      externalId        = externalId,
      module            = module.toString,
      name              = name.name,
      arguments         = methodArgs,
      selfType          = selfType.toString,
      returnType        = buildReturnType(returnTypeDef),
      documentation     = doc,
      documentationHtml = doc.map(DocParserWrapper.runOnPureDoc),
      reexport          = None
    )
  }

  /** Build a function suggestion */
  private def buildFunction(
    externalId: Option[IR.ExternalId],
    module: QualifiedName,
    name: IR.Name,
    args: Seq[IR.DefinitionArgument],
    location: Location,
    typeSignature: Option[TypeSignatures.Metadata],
    bindings: Option[BindingAnalysis.Metadata]
  ): Suggestion.Function = {
    val typeSig = buildTypeSignatureFromMetadata(typeSignature, bindings)
    val (methodArgs, returnTypeDef) =
      buildFunctionArguments(args, typeSig)
    Suggestion.Function(
      externalId = externalId,
      module     = module.toString,
      name       = name.name,
      arguments  = methodArgs,
      returnType = buildReturnType(returnTypeDef),
      scope      = buildScope(location)
    )
  }

  /** Build a local suggestion. */
  private def buildLocal(
    externalId: Option[IR.ExternalId],
    module: QualifiedName,
    name: String,
    location: Location,
    typeSignature: Option[TypeSignatures.Metadata],
    bindings: Option[BindingAnalysis.Metadata]
  ): Suggestion.Local = {
    val typeSig            = buildTypeSignatureFromMetadata(typeSignature, bindings)
    val (_, returnTypeDef) = buildFunctionArguments(Seq(), typeSig)
    Suggestion.Local(
      externalId,
      module.toString,
      name,
      buildReturnType(returnTypeDef),
      buildScope(location)
    )
  }

  /** Build an atom suggestion representing a module. */
  private def buildModule(
    module: QualifiedName,
    doc: Option[String]
  ): Suggestion =
    Suggestion.Module(
      module            = module.toString,
      documentation     = doc,
      documentationHtml = doc.map(DocParserWrapper.runOnPureDoc),
      reexport          = None
    )

  /** Build suggestions for an atom definition. */
  private def buildAtom(
    bindings: Option[BindingAnalysis.Metadata],
    module: QualifiedName,
    name: String,
    arguments: Seq[IR.DefinitionArgument],
    doc: Option[String]
  ): Seq[Suggestion] =
    buildAtomConstructor(module, name, arguments, doc) +:
    buildAtomGetters(bindings, module, name, arguments)

  /** Build an atom constructor. */
  private def buildAtomConstructor(
    module: QualifiedName,
    name: String,
    arguments: Seq[IR.DefinitionArgument],
    doc: Option[String]
  ): Suggestion.Atom =
    Suggestion.Atom(
      externalId        = None,
      module            = module.toString,
      name              = name,
      arguments         = arguments.map(buildArgument),
      returnType        = module.createChild(name).toString,
      documentation     = doc,
      documentationHtml = doc.map(DocParserWrapper.runOnPureDoc),
      reexport          = None
    )

  /** Build getter methods from atom arguments. */
  private def buildAtomGetters(
    bindings: Option[BindingAnalysis.Metadata],
    module: QualifiedName,
    name: String,
    arguments: Seq[IR.DefinitionArgument]
  ): Seq[Suggestion] =
    arguments.map { argument =>
      val thisArg = IR.DefinitionArgument.Specified(
        name         = IR.Name.This(argument.name.location),
        ascribedType = None,
        defaultValue = None,
        suspended    = false,
        location     = argument.location
      )
      buildMethod(
        externalId    = None,
        module        = module,
        name          = argument.name,
        selfType      = module.createChild(name),
        args          = Seq(thisArg),
        doc           = None,
        typeSignature = None,
        bindings      = bindings
      )
    }

  /** Build self type from the method definitions metadata.
    *
    * @param metadata the result of successful name resolution
    * @return the qualified type name
    */
  private def buildSelfType(
    metadata: MethodDefinitions.Metadata
  ): Option[QualifiedName] =
    buildResolvedTypeName(metadata.target)

  /** Build type name from the resolved name.
    *
    * @param resolvedName the result of successful name resolution
    * @return the qualified type name
    */
  private def buildResolvedTypeName(
    resolvedName: BindingsMap.ResolvedName
  ): Option[QualifiedName] = {
    resolvedName match {
      case BindingsMap.ResolvedModule(module) =>
        Some(module.getName)
      case BindingsMap.ResolvedConstructor(module, cons) =>
        Some(module.getName.createChild(cons.name))
      case _ =>
        None
    }
  }

  /** Build type signature from the ir metadata.
    *
    * @param typeSignature the type signature metadata
    * @param bindings the binding analysis metadata
    * @return the list of type arguments
    */
  private def buildTypeSignatureFromMetadata(
    typeSignature: Option[TypeSignatures.Metadata],
    bindings: Option[BindingAnalysis.Metadata]
  ): Vector[TypeArg] =
    typeSignature match {
      case Some(TypeSignatures.Signature(typeExpr)) =>
        buildTypeSignature(bindings, typeExpr)
      case _ =>
        Vector()
    }

  /** Build type signature from the type expression.
    *
    * @param bindings the binding analysis metadata
    * @param typeExpr the type signature expression
    * @return the list of type arguments
    */
  private def buildTypeSignature(
    bindings: Option[BindingAnalysis.Metadata],
    typeExpr: IR.Expression
  ): Vector[TypeArg] = {
    def go(typeExpr: IR.Expression, args: Vector[TypeArg]): Vector[TypeArg] =
      typeExpr match {
        case IR.Application.Operator.Binary(left, op, right, _, _, _) =>
          val arg = for {
            leftArg  <- go(left.value, Vector()).headOption
            rightArg <- go(right.value, Vector()).headOption
          } yield TypeArg.Binary(leftArg, rightArg, op.name)
          args :++ arg
        case IR.Function.Lambda(List(targ), body, _, _, _, _) =>
          val typeName = targ.name.name
          val qualifiedTypeName = resolveTypeName(bindings, typeName)
            .getOrElse(QualifiedName.simpleName(typeName))
          val tdef = TypeArg.Value(qualifiedTypeName)
          go(body, args :+ tdef)
        case IR.Application.Prefix(tfun, targs, _, _, _, _) =>
          val appFunction = go(tfun, Vector()).head
          val appArgs     = targs.flatMap(arg => go(arg.value, Vector()))
          args :+ TypeArg.Application(appFunction, appArgs.toVector)
        case tname: IR.Name =>
          val typeName = tname.name
          val qualifiedTypeName = resolveTypeName(bindings, typeName)
            .getOrElse(QualifiedName.simpleName(typeName))
          args :+ TypeArg.Value(qualifiedTypeName)
        case _ =>
          args
      }

    typeExpr match {
      case IR.Application.Operator.Binary(left, _, right, _, _, _) =>
        val arg = TypeArg.Function(go(left.value, Vector()))
        go(right.value, Vector(arg))
      case expr =>
        go(expr, Vector())
    }
  }

  /** Resolve unqualified type name.
    *
    * @param bindings the binding analysis metadata
    * @param name the unqualified type name
    * @return the resolved qualified type name
    */
  private def resolveTypeName(
    bindings: Option[BindingAnalysis.Metadata],
    name: String
  ): Option[QualifiedName] = {
    bindings
      .flatMap(_.resolveUppercaseName(name).toOption)
      .flatMap(buildResolvedTypeName)
  }

  /** Build arguments of a method.
    *
    * @param vargs the list of value arguments
    * @param targs the list of type arguments
    * @param selfType the self type of a method
    * @return the list of arguments with a method return type
    */
  private def buildMethodArguments(
    vargs: Seq[IR.DefinitionArgument],
    targs: Seq[TypeArg],
    selfType: QualifiedName
  ): (Seq[Suggestion.Argument], Option[TypeArg]) = {
    @scala.annotation.tailrec
    def go(
      vargs: Seq[IR.DefinitionArgument],
      targs: Seq[TypeArg],
      acc: Vector[Suggestion.Argument]
    ): (Vector[Suggestion.Argument], Option[TypeArg]) =
      if (vargs.isEmpty) {
        (acc, targs.lastOption)
      } else {
        vargs match {
          case IR.DefinitionArgument.Specified(
                name: IR.Name.This,
                _,
                defaultValue,
                suspended,
                _,
                _,
                _
              ) +: vtail =>
            val thisArg = Suggestion.Argument(
              name         = name.name,
              reprType     = selfType.toString,
              isSuspended  = suspended,
              hasDefault   = defaultValue.isDefined,
              defaultValue = defaultValue.flatMap(buildDefaultValue)
            )
            go(vtail, targs, acc :+ thisArg)
          case varg +: vtail =>
            targs match {
              case targ +: ttail =>
                go(vtail, ttail, acc :+ buildTypedArgument(varg, targ))
              case _ =>
                go(vtail, targs, acc :+ buildArgument(varg))
            }
        }
      }

    go(vargs, targs, Vector())
  }

  /** Build arguments of a function.
    *
    * @param vargs the list of value arguments
    * @param targs the list of type arguments
    * @return the list of arguments with a function return type
    */
  private def buildFunctionArguments(
    vargs: Seq[IR.DefinitionArgument],
    targs: Seq[TypeArg]
  ): (Seq[Suggestion.Argument], Option[TypeArg]) = {
    @scala.annotation.tailrec
    def go(
      vargs: Seq[IR.DefinitionArgument],
      targs: Seq[TypeArg],
      acc: Vector[Suggestion.Argument]
    ): (Seq[Suggestion.Argument], Option[TypeArg]) =
      if (vargs.isEmpty) {
        (acc, targs.lastOption)
      } else {
        vargs match {
          case varg +: vtail =>
            targs match {
              case targ +: ttail =>
                go(vtail, ttail, acc :+ buildTypedArgument(varg, targ))
              case _ =>
                go(vtail, targs, acc :+ buildArgument(varg))
            }
        }
      }

    go(vargs, targs, Vector())
  }

  /** Build suggestion argument from a typed definition.
    *
    * @param varg the value argument
    * @param targ the type argument
    * @return the suggestion argument
    */
  private def buildTypedArgument(
    varg: IR.DefinitionArgument,
    targ: TypeArg
  ): Suggestion.Argument =
    Suggestion.Argument(
      name         = varg.name.name,
      reprType     = buildTypeArgumentName(targ),
      isSuspended  = varg.suspended,
      hasDefault   = varg.defaultValue.isDefined,
      defaultValue = varg.defaultValue.flatMap(buildDefaultValue)
    )

  /** Build the name of type argument.
    *
    * @param targ the type argument
    * @return the name of type argument
    */
  private def buildTypeArgumentName(targ: TypeArg): String = {
    def go(targ: TypeArg, level: Int): String =
      targ match {
        case TypeArg.Value(name) => name.toString
        case TypeArg.Function(Vector(typeArg)) =>
          val typeName = go(typeArg, level)
          if (level > 0) s"($typeName)" else typeName
        case TypeArg.Function(types) =>
          val typeList = types.map(go(_, level + 1))
          if (level > 0) typeList.mkString("(", " -> ", ")")
          else typeList.mkString(" -> ")
        case TypeArg.Binary(l, r, op) =>
          val left  = go(l, level + 1)
          val right = go(r, level + 1)
          s"$left $op $right"
        case TypeArg.Application(fun, args) =>
          val funText  = go(fun, level)
          val argsList = args.map(go(_, level + 1)).mkString(" ")
          val typeName = s"$funText $argsList"
          if (level > 0) s"($typeName)" else typeName
      }

    go(targ, 0)
  }

  /** Build suggestion argument from an untyped definition.
    *
    * @param arg the value argument
    * @return the suggestion argument
    */
  private def buildArgument(arg: IR.DefinitionArgument): Suggestion.Argument =
    Suggestion.Argument(
      name         = arg.name.name,
      reprType     = Any,
      isSuspended  = arg.suspended,
      hasDefault   = arg.defaultValue.isDefined,
      defaultValue = arg.defaultValue.flatMap(buildDefaultValue)
    )

  /** Build return type from the type definition.
    *
    * @param typeDef the type definition
    * @return the type name
    */
  private def buildReturnType(typeDef: Option[TypeArg]): String =
    typeDef.map(buildTypeArgumentName).getOrElse(Any)

  /** Build argument default value from the expression.
    *
    * @param expr the argument expression
    * @return the argument default value
    */
  private def buildDefaultValue(expr: IR): Option[String] =
    expr match {
      case IR.Literal.Number(_, value, _, _, _) => Some(value)
      case IR.Literal.Text(text, _, _, _)       => Some(text)
      case _                                    => None
    }

  /** Build scope from the location. */
  private def buildScope(location: Location): Suggestion.Scope =
    Suggestion.Scope(toPosition(location.start), toPosition(location.end))

  /** Convert absolute position index to the relative position of a suggestion.
    *
    * @param index the absolute position in the source
    * @return the relative position
    */
  private def toPosition(index: Int): Suggestion.Position = {
    val pos = IndexedSource[A].toPosition(index, source)
    Suggestion.Position(pos.line, pos.character)
  }
}

object SuggestionBuilder {

  /** Create the suggestion builder.
    *
    * @param source the text source
    * @tparam A the type of the text source
    */
  def apply[A: IndexedSource](source: A): SuggestionBuilder[A] =
    new SuggestionBuilder[A](source)

  /** A single level of an `IR`.
    *
    * @param queue the nodes in the scope
    * @param location the scope location
    */
  private case class Scope(queue: mutable.Queue[IR], location: Option[Location])

  private object Scope {

    /** Create new scope from the list of items.
      *
      * @param items the list of IR nodes
      * @param location the identified IR location
      * @return new scope
      */
    def apply(items: Seq[IR], location: Option[IR.IdentifiedLocation]): Scope =
      new Scope(mutable.Queue(items: _*), location.map(_.location))
  }

  /** The base trait for argument types. */
  sealed private trait TypeArg
  private object TypeArg {

    /** Type with the name, like `A`.
      *
      * @param name the name of the type
      */
    case class Value(name: QualifiedName) extends TypeArg

    /** Function type, like `A -> A`.
      *
      * @param signature the list of types defining the function
      */
    case class Function(signature: Vector[TypeArg]) extends TypeArg

    /** Binary operator, like `A | B`
      *
      * @param left the left hand side of a binary operator
      * @param right the right hand side of a binary operator
      * @param operator the binary operator
      */
    case class Binary(left: TypeArg, right: TypeArg, operator: String)
        extends TypeArg

    /** Function application, like `Either A B`.
      *
      * @param function the function type
      * @param arguments the list of argument types
      */
    case class Application(
      function: TypeArg,
      arguments: Vector[TypeArg]
    ) extends TypeArg

  }

  val Any: String = Constants.ANY

}
