package org.enso.downloader.archive

import org.enso.downloader.archive.POSIXPermissions

import java.nio.file.attribute.PosixFilePermissions
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

import scala.jdk.CollectionConverters._

class POSIXPermissionsSpec extends AnyWordSpec with Matchers {
  "POSIXPermissions" should {
    "decode permissions correctly" in {
      def assertPair(octal: String, textual: String): Unit = {
        val mode      = Integer.parseInt(octal, 8)
        val decoded   = POSIXPermissions.decode(mode)
        val reference = PosixFilePermissions.fromString(textual)
        decoded.asScala.toSet shouldEqual reference.asScala.toSet
      }

      assertPair("000", "---------")
      assertPair("111", "--x--x--x")
      assertPair("222", "-w--w--w-")
      assertPair("444", "r--r--r--")
      assertPair("777", "rwxrwxrwx")

      assertPair("644", "rw-r--r--")
      assertPair("012", "-----x-w-")
      assertPair("421", "r---w---x")
    }
  }
}
