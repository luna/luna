package org.enso.searcher.sql

import java.util.UUID

import org.enso.polyglot.Suggestion

import scala.util.Random

object SuggestionRandom {

  def nextUpdateAllInput(): Seq[(UUID, String)] =
    Seq(UUID.randomUUID() -> nextString())

  def nextGetAllMethodsInput(): Seq[(String, String, String)] =
    Seq(
      (nextString(), nextString(), nextString()),
      (nextString(), nextString(), nextString())
    )

  def nextKinds(): Seq[Suggestion.Kind] =
    Set.fill(1)(nextKind()).toSeq

  def nextSuggestion(): Suggestion = {
    nextKind() match {
      case Suggestion.Kind.Module   => nextSuggestionModule()
      case Suggestion.Kind.Atom     => nextSuggestionAtom()
      case Suggestion.Kind.Method   => nextSuggestionMethod()
      case Suggestion.Kind.Function => nextSuggestionFunction()
      case Suggestion.Kind.Local    => nextSuggestionLocal()
    }
  }

  def nextSuggestionModule(): Suggestion.Module =
    Suggestion.Module(
      module            = nextString(),
      documentation     = optional(nextString()),
      documentationHtml = optional(nextString())
    )

  def nextSuggestionAtom(): Suggestion.Atom =
    Suggestion.Atom(
      externalId        = optional(UUID.randomUUID()),
      module            = "Test.Main",
      name              = nextString(),
      arguments         = Seq(),
      returnType        = nextString(),
      documentation     = optional(nextString()),
      documentationHtml = optional(nextString())
    )

  def nextSuggestionMethod(): Suggestion.Method =
    Suggestion.Method(
      externalId        = optional(UUID.randomUUID()),
      module            = "Test.Main",
      name              = nextString(),
      arguments         = Seq(),
      selfType          = nextString(),
      returnType        = nextString(),
      documentation     = optional(nextString()),
      documentationHtml = optional(nextString())
    )

  def nextSuggestionFunction(): Suggestion.Function =
    Suggestion.Function(
      externalId = optional(UUID.randomUUID()),
      module     = "Test.Main",
      name       = nextString(),
      arguments  = Seq(),
      returnType = nextString(),
      scope      = nextScope()
    )

  def nextSuggestionLocal(): Suggestion.Local =
    Suggestion.Local(
      externalId = optional(UUID.randomUUID()),
      module     = "Test.Main",
      name       = nextString(),
      returnType = nextString(),
      scope      = nextScope()
    )

  def nextScope(): Suggestion.Scope =
    Suggestion.Scope(
      start = nextPosition(),
      end   = nextPosition()
    )

  def nextPosition(): Suggestion.Position =
    Suggestion.Position(
      Random.nextInt(Int.MaxValue),
      Random.nextInt(Int.MaxValue)
    )

  def nextKind(): Suggestion.Kind =
    Random.nextInt(5) match {
      case 0 => Suggestion.Kind.Module
      case 1 => Suggestion.Kind.Atom
      case 2 => Suggestion.Kind.Method
      case 3 => Suggestion.Kind.Function
      case 4 => Suggestion.Kind.Local
      case x => throw new NoSuchElementException(s"nextKind: $x")
    }

  def nextString(): String =
    Random.nextString(Random.nextInt(26) + 5)

  def optional[A](f: => A): Option[A] =
    Option.when(Random.nextBoolean())(f)
}
