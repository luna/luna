package org.enso.downloader.archive.internal

import java.nio.file.Path

/** Acts as a function that renames the base of the provided paths to the
  * `newBase`.
  *
  * It changes the first component of the provided paths to the provided
  * `newBase`. It is meant to be used for renaming root directories of extracted
  * archives. The provided paths should be relative.
  *
  * It ensures that all paths converted with this function have the same base,
  * to avoid merging two base directories.
  */
class BaseRenamer(newBase: Path) extends (Path => Path) {
  var lastRoot: Option[Path] = None

  /** Changes the path by renaming its base component.
    */
  override def apply(path: Path): Path = {
    if (path.getNameCount < 1) {
      throw new RuntimeException(
        s"Unexpected archive structure - the file $path cannot " +
        s"have its root renamed."
      )
    }

    val oldRoot = path.getName(0)
    lastRoot match {
      case Some(lastRoot) =>
        if (lastRoot != oldRoot) {
          throw new RuntimeException(
            s"Unexpected archive structure - the archive contains " +
            s"more than one file in the root - $lastRoot, $oldRoot"
          )
        }
      case None =>
        lastRoot = Some(oldRoot)
    }

    if (path.getNameCount == 1)
      newBase
    else {
      val remainingParts = path.subpath(1, path.getNameCount)
      newBase.resolve(remainingParts)
    }
  }
}
