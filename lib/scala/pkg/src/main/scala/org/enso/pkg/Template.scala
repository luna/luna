package org.enso.pkg

/** Base trait for the project templates. */
sealed trait Template {

  /** The template name. */
  def name: String
}
object Template {

  /** Create a template from string.
    *
    * @param template the template name
    * @return the template for the provided name
    */
  def fromString(template: String): Option[Template] =
    allTemplates.find(_.name == template.toLowerCase)

  /** The default project template. */
  case object Default extends Template {
    override val name = "default"
  }

  /** The example project template. */
  case object Example extends Template {
    override val name = "example"
  }

  val allTemplates = Seq(Default, Example)
}
