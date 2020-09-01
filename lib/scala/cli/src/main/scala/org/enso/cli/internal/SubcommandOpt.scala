package org.enso.cli.internal

import cats.data.NonEmptyList
import org.enso.cli.{CLIOutput, Command, Opts, Spelling, Subcommand}

class SubcommandOpt[A](subcommands: NonEmptyList[Command[A]])
    extends BaseSubcommandOpt[A, A] {
  override def availableSubcommands: NonEmptyList[Command[A]] = subcommands

  override def handleUnknownCommand(command: String): ParserContinuation = {
    val similar =
      Spelling
        .selectClosestMatches(command, subcommands.toList.map(_.name))
    val suggestions =
      if (similar.isEmpty)
        "\n\nPossible subcommands are\n" +
        subcommands.toList
          .map(CLIOutput.indent + _.name + "\n")
          .mkString
      else
        "\n\nThe most similar subcommands are\n" +
        similar.map(CLIOutput.indent + _ + "\n").mkString
    addError(s"`$command` is not a valid subcommand." + suggestions)
    ParserContinuation.ContinueNormally
  }

  override private[cli] def result(commandPrefix: Seq[String]) =
    if (errors.nonEmpty)
      Left(errors.reverse)
    else
      selectedCommand match {
        case Some(command) => command.opts.result(commandPrefix)
        case None =>
          Left(List("Expected a subcommand.", help(commandPrefix)))
      }
}
