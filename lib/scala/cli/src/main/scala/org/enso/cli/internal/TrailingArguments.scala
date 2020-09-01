package org.enso.cli.internal

import org.enso.cli.Argument

class TrailingArguments[A: Argument](
  metavar: String,
  helpComment: Option[String]
) extends BaseOpts[Seq[A]] {
  val empty                                = Right(Nil)
  var value: Either[List[String], List[A]] = empty

  override private[cli] val trailingArguments = Some(metavar)

  override private[cli] def wantsArgument() = true

  override private[cli] def consumeArgument(
    arg: String,
    commandPrefix: Seq[String]
  ): ParserContinuation = {
    value = for {
      currentArguments <- value
      parsed           <- Argument[A].read(arg)
    } yield parsed :: currentArguments
    ParserContinuation.ContinueNormally
  }

  override private[cli] def reset(): Unit = {
    value = empty
  }

  override private[cli] def result(commandPrefix: Seq[String]) =
    value.map(_.reverse)

  override def additionalHelp(): Seq[String] = helpComment.toSeq
}
