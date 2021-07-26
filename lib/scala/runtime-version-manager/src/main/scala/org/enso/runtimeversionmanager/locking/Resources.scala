package org.enso.runtimeversionmanager.locking

import nl.gn0s1s.bump.SemVer
import org.enso.distribution.locking.Resource
import org.enso.runtimeversionmanager.components.GraalVMVersion

object Resources {

  /** Synchronizes launcher upgrades. */
  case object LauncherExecutable extends Resource {
    override def name: String = "launcher-executable"
    override def waitMessage: String =
      "Another upgrade is in progress, " +
      "the current process must wait until it is completed."
  }

  /** This resource is held when adding or removing any components. */
  case object AddOrRemoveComponents extends Resource {
    override def name: String = "add-remove-components"
    override def waitMessage: String =
      "Another process is adding or removing components, " +
      "the current process must wait until it finishes."
  }

  /** This resource should be held (shared) by any process that is using the
    * engine to ensure that it will stay available for the duration of the
    * action.
    *
    * It is acquired exclusively when the engine is installed or uninstalled.
    */
  case class Engine(version: SemVer) extends Resource {
    override def name: String = s"engine-$version"
    override def waitMessage: String =
      s"Another process is using engine $version, " +
      "the current process must wait until other processes complete."
  }

  /** This resource should be held (shared) by any process that is using the
    * runtime to ensure that it will stay available for the duration of the
    * action.
    *
    * It is acquired exclusively when the runtime is installed or uninstalled.
    */
  case class Runtime(version: GraalVMVersion) extends Resource {
    override def name: String =
      s"runtime-${version.graalVersion}-${version.java}"
    override def waitMessage: String =
      s"Another process is using $version, " +
      "the current process must wait until other processes complete."
  }
}
