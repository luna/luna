package org.enso.yaml

import io.circe.{yaml, Decoder}

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
}
