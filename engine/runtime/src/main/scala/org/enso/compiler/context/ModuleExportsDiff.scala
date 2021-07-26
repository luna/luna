package org.enso.compiler.context

import org.enso.polyglot.ModuleExports
import org.enso.polyglot.runtime.Runtime.Api

object ModuleExportsDiff {

  /** Compute difference between the module exports.
    *
    * @param prev exports before
    * @param current exports after
    * @return the list of updates
    */
  def compute(
    prev: ModuleExports,
    current: ModuleExports
  ): Seq[Api.ExportsUpdate] = {
    val added   = current.symbols.diff(prev.symbols)
    val removed = prev.symbols.diff(current.symbols)
    val addedUpdate = Option.when(added.nonEmpty) {
      Api.ExportsUpdate(current.copy(symbols = added), Api.ExportsAction.Add())
    }
    val removedUpdate = Option.when(removed.nonEmpty) {
      Api.ExportsUpdate(
        current.copy(symbols = removed),
        Api.ExportsAction.Remove()
      )
    }
    (addedUpdate ++ removedUpdate).toSeq
  }
}
