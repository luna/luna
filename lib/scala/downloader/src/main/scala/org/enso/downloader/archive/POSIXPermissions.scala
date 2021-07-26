package org.enso.downloader.archive

import java.nio.file.attribute.PosixFilePermission
import java.util

object POSIXPermissions {

  /** Parses POSIX file permissions stored in a binary format into a set of Java
    * enumerations corresponding to these permissions.
    */
  def decode(mode: Int): java.util.Set[PosixFilePermission] = {
    val res =
      util.EnumSet.noneOf[PosixFilePermission](classOf[PosixFilePermission])

    val others = mode & 7
    val group  = (mode >> 3) & 7
    val owner  = (mode >> 6) & 7

    if ((owner & 4) != 0) {
      res.add(PosixFilePermission.OWNER_READ)
    }
    if ((owner & 2) != 0) {
      res.add(PosixFilePermission.OWNER_WRITE)
    }
    if ((owner & 1) != 0) {
      res.add(PosixFilePermission.OWNER_EXECUTE)
    }

    if ((group & 4) != 0) {
      res.add(PosixFilePermission.GROUP_READ)
    }
    if ((group & 2) != 0) {
      res.add(PosixFilePermission.GROUP_WRITE)
    }
    if ((group & 1) != 0) {
      res.add(PosixFilePermission.GROUP_EXECUTE)
    }

    if ((others & 4) != 0) {
      res.add(PosixFilePermission.OTHERS_READ)
    }
    if ((others & 2) != 0) {
      res.add(PosixFilePermission.OTHERS_WRITE)
    }
    if ((others & 1) != 0) {
      res.add(PosixFilePermission.OTHERS_EXECUTE)
    }
    res
  }

}
