package org.enso.cli.task

/** Defines the unit in which the progress amount is measured. */
sealed trait ProgressUnit

object ProgressUnit {

  /** Specifies that progress amount is measured in bytes. */
  case object Bytes extends ProgressUnit {
    override val toString: String = "bytes"
  }

  /** Does not specify a particular progress unit. */
  case object Unspecified extends ProgressUnit {
    override val toString: String = "unspecified"
  }

  /** Converts a unit to its string representation. */
  def toString(unit: ProgressUnit): String = unit.toString

  /** Creates a unit from its string representation, falling back to
    * [[Unspecified]] if it cannot be recognized.
    */
  def fromString(str: String): ProgressUnit =
    if (str == Bytes.toString) Bytes else Unspecified
}
