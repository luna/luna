package org.enso.searcher.sql

import java.nio.file.{Files, Path}
import java.util

import org.enso.testkit.RetrySpec
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

import scala.concurrent.Await
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._
import scala.util.Random

class VersionsRepoTest extends AnyWordSpec with Matchers with RetrySpec {

  val Timeout: FiniteDuration = 20.seconds

  val tmpdir: Path = {
    val tmp = Files.createTempDirectory("versions-repo-test")
    sys.addShutdownHook {
      Files.list(tmp).forEach { path =>
        path.toFile.delete()
      }
      tmp.toFile.delete()
    }
    tmp
  }

  def withRepo(test: SqlVersionsRepo => Any): Any = {
    val tmpdb = Files.createTempFile(tmpdir, "versions-repo", ".db")
    val repo  = new SqlVersionsRepo(SqlDatabase(tmpdb.toFile))
    Await.ready(repo.init, Timeout)
    try test(repo)
    finally {
      Await.ready(repo.clean, Timeout)
      repo.close()
    }
  }

  def nextDigest(): Array[Byte] =
    Random.nextBytes(28)

  "FileVersionsRepo" should {

    "init idempotent" in withRepo { repo =>
      Await.result(repo.init, Timeout)
    }

    "insert digest" taggedAs Retry in withRepo { repo =>
      val module = "Foo.Bar"
      val digest = nextDigest()
      val action =
        for {
          v1 <- repo.setVersion(module, digest)
          v2 <- repo.getVersion(module)
        } yield (v1, v2)

      val (v1, v2) = Await.result(action, Timeout)
      v1 shouldBe None
      v2 shouldBe a[Some[_]]
      util.Arrays.equals(v2.get, digest) shouldBe true
    }

    "set digest" taggedAs Retry in withRepo { repo =>
      val module  = "Foo.Bar"
      val digest1 = nextDigest()
      val digest2 = nextDigest()
      val action =
        for {
          v1 <- repo.setVersion(module, digest1)
          v2 <- repo.setVersion(module, digest2)
          v3 <- repo.getVersion(module)
        } yield (v1, v2, v3)

      val (v1, v2, v3) = Await.result(action, Timeout)
      v1 shouldBe None
      v2 shouldBe a[Some[_]]
      v3 shouldBe a[Some[_]]
      util.Arrays.equals(v2.get, digest1) shouldBe true
      util.Arrays.equals(v3.get, digest2) shouldBe true
    }

    "update digest" taggedAs Retry in withRepo { repo =>
      val module  = "Foo.Bar"
      val digest1 = nextDigest()
      val digest2 = nextDigest()
      val digest3 = nextDigest()
      val action =
        for {
          b1 <- repo.updateVersion(module, digest1)
          v2 <- repo.setVersion(module, digest2)
          b2 <- repo.updateVersion(module, digest2)
          b3 <- repo.updateVersion(module, digest3)
          b4 <- repo.updateVersion(module, digest3)
          v3 <- repo.getVersion(module)
        } yield (v2, v3, b1, b2, b3, b4)

      val (v2, v3, b1, b2, b3, b4) = Await.result(action, Timeout)
      v2 shouldBe a[Some[_]]
      v3 shouldBe a[Some[_]]
      util.Arrays.equals(v2.get, digest1) shouldBe true
      util.Arrays.equals(v3.get, digest3) shouldBe true
      b1 shouldBe true
      b2 shouldBe false
      b3 shouldBe true
      b4 shouldBe false
    }

    "batch update digest" taggedAs Retry in withRepo { repo =>
      val module1 = "Foo.Bar"
      val module2 = "Foo.Baz"
      val digest0 = nextDigest()
      val digest1 = nextDigest()
      val digest2 = nextDigest()
      val input   = Seq(module1 -> digest1, module2 -> digest2)
      val action =
        for {
          _  <- repo.setVersion(module1, digest0)
          _  <- repo.updateVersions(input)
          v1 <- repo.getVersion(module1)
          v2 <- repo.getVersion(module2)
        } yield (v1, v2)

      val (v1, v2) = Await.result(action, Timeout)
      v1 shouldBe a[Some[_]]
      v2 shouldBe a[Some[_]]
      util.Arrays.equals(v1.get, digest1) shouldBe true
      util.Arrays.equals(v2.get, digest2) shouldBe true
    }

    "delete digest" taggedAs Retry in withRepo { repo =>
      val module = "Foo.Bar"
      val digest = nextDigest()
      val action =
        for {
          v1 <- repo.setVersion(module, digest)
          _  <- repo.remove(module)
          v2 <- repo.getVersion(module)
        } yield (v1, v2)

      val (v1, v2) = Await.result(action, Timeout)
      v1 shouldEqual None
      v2 shouldEqual None
    }
  }
}
