package org.enso.yaml

import io.circe.yaml.Printer
import io.circe.{yaml, Decoder, Encoder}

import java.io.FileReader
import java.nio.file.Path
import scala.util.{Try, Using}

/** A helper for parsing YAML configs. */
object YamlHelper {

  /** Parses a string representation of a YAML configuration of type `R`. */
  def parseString[R](
    yamlString: String
  )(implicit decoder: Decoder[R]): Either[ParseError, R] =
    yaml.parser
      .parse(yamlString)
      .flatMap(_.as[R])
      .left
      .map(ParseError(_))

  /** Tries to load and parse a YAML file at the provided path. */
  def load[R](path: Path)(implicit decoder: Decoder[R]): Try[R] =
    Using(new FileReader(path.toFile)) { reader =>
      yaml.parser
        .parse(reader)
        .flatMap(_.as[R])
        .toTry
    }.flatten

  /** Saves a YAML representation of an object into a string. */
  def toYaml[A](obj: A)(implicit encoder: Encoder[A]): String =
    Printer.spaces2.copy(preserveOrder = true).pretty(encoder(obj))
}
