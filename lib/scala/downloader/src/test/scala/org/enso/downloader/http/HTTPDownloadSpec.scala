package org.enso.downloader.http

import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

class HTTPDownloadSpec extends AnyWordSpec with Matchers {
  "HTTPDownload" should {
    "accept gzipped responses and decode them correctly" in {
      // TODO [RW] Write the test using the simple-library-server (#1805)
      /** It should:
        * - generate a 2kb yaml file
        * - run the server
        * - download the file and verify its contents
        * - if possible, check that it was indeed compressed
        */
    }
  }
}
