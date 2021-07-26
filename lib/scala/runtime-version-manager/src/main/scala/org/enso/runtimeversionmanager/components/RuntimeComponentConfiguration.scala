package org.enso.runtimeversionmanager.components

import org.enso.cli.OS

/** Provides configuration of the runtime components. */
trait RuntimeComponentConfiguration {

  /** Return the list of components required for the provided version of
    * the runtime installed on the provided OS.
    *
    * @param version the runtime version
    * @param os the operating system
    * @return the list of required components
    */
  def getRequiredComponents(
    version: GraalVMVersion,
    os: OS
  ): Seq[GraalVMComponent]
}
