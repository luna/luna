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

  case object Orders extends Template {
    override val name = "orders"
  }

  case object Restaurants extends Template {
    override val name = "restaurants"
  }

  case object Stargazers extends Template {
    override val name = "stargazers"
  }

  val allTemplates = Seq(Default, Orders, Restaurants, Stargazers)
}
