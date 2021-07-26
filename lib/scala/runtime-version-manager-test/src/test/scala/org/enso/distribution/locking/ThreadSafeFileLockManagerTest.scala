package org.enso.distribution.locking

import org.enso.distribution.locking.LockType.Shared
import org.enso.distribution.locking.{
  FileLockManager,
  LockType,
  ThreadSafeFileLockManager
}

import java.nio.file.Path
import org.enso.runtimeversionmanager.test.{NativeTestHelper, TestSynchronizer}
import org.enso.testkit.WithTemporaryDirectory
import org.scalatest.OptionValues
import org.scalatest.concurrent.TimeLimitedTests
import org.scalatest.matchers.should.Matchers
import org.scalatest.time.Span
import org.scalatest.time.SpanSugar.convertIntToGrainOfTime
import org.scalatest.wordspec.AnyWordSpec

class ThreadSafeFileLockManagerTest
    extends AnyWordSpec
    with Matchers
    with WithTemporaryDirectory
    with TimeLimitedTests
    with OptionValues
    with NativeTestHelper {

  val timeLimit: Span = 30.seconds

  class TestableThreadSafeFileLockManager(locksRoot: Path)
      extends ThreadSafeFileLockManager(locksRoot) {

    /** A helper function that can be called by the test suite to release all file locks.
      *
      * It is only safe to call this function if it can be guaranteed that no
      * locks created with this manager are still in scope.
      *
      * Normally all file locks are released automatically when the JVM exits,
      * but as tests run within a single JVM,this is not the case and any
      * dangling locks will cause problems when cleaning the temporary
      * directory.
      */
    def releaseAllLocks(): Unit = {
      localLocks.foreach { case (_, lock) =>
        lock.fileLock.foreach(_.release())
        lock.fileLock = None
      }
      localLocks.clear()
    }
  }

  private var testLocalLockManager: Option[TestableThreadSafeFileLockManager] =
    None

  override def beforeEach(): Unit = {
    super.beforeEach()
    testLocalLockManager = Some(
      new TestableThreadSafeFileLockManager(getTestDirectory)
    )
  }

  override def afterEach(): Unit = {
    testLocalLockManager.foreach(_.releaseAllLocks())
    testLocalLockManager = None
    super.afterEach()
  }

  def lockManager: ThreadSafeFileLockManager = testLocalLockManager.getOrElse(
    throw new IllegalStateException("Test not initialized")
  )

  "ThreadSafeFileLockManager" should {
    "allow multiple concurrent shared locks" in {
      val name = "resource1"
      val r1   = lockManager.acquireLock(name, Shared)
      val r2   = lockManager.acquireLock(name, LockType.Shared)
      r1.release()
      r2.release()
    }

    "allow no other locks if an exclusive lock is held" in {
      val sync     = new TestSynchronizer
      val name     = "resource1"
      val mainLock = lockManager.acquireLock(name, LockType.Exclusive)
      sync.report("acquired main")

      sync.startThread("exclusive") {
        lockManager.tryAcquireLock(name, LockType.Exclusive) shouldEqual None
        sync.signal("exclusive-waited")
        val lock = lockManager.acquireLock(name, LockType.Exclusive)
        sync.report("acquired")
        lock.release()
      }

      sync.startThread("shared") {
        lockManager.tryAcquireLock(name, LockType.Shared) shouldEqual None
        sync.signal("shared-waited")
        val lock = lockManager.acquireLock(name, LockType.Shared)
        sync.report("acquired")
        lock.release()
      }

      sync.waitFor("exclusive-waited")
      sync.waitFor("shared-waited")

      sync.report("releasing main")
      mainLock.release()

      sync.join(5)
      sync.summarizeReports() shouldEqual Seq(
        "acquired main",
        "releasing main",
        "acquired",
        "acquired"
      )
    }

    "allow exclusive lock only when no shared locks are held" in {
      val sync    = new TestSynchronizer
      val name    = "resource1"
      val shared1 = lockManager.acquireLock(name, LockType.Shared)

      sync.startThread("exclusive") {
        lockManager.tryAcquireLock(name, LockType.Exclusive) shouldEqual None
        sync.signal("waited")
        val lock = lockManager.acquireLock(name, LockType.Exclusive)
        sync.report("acquired")
        lock.release()
      }

      sync.waitFor("waited")

      val shared2 = lockManager.acquireLock(name, LockType.Shared)
      lockManager.acquireLock(name, LockType.Shared).release()
      sync.report("releasing-some")
      shared1.release()

      sync.report("releasing-last-one")

      shared2.release()

      sync.join(5)
      sync.summarizeReports() shouldEqual Seq(
        "releasing-some",
        "releasing-last-one",
        "acquired"
      )

    }

    "immediately resolve failed shared tryAcquire" in {
      val name = "resource1"
      lockManager.tryAcquireLock(name, LockType.Exclusive).value
      lockManager.tryAcquireLock(name, LockType.Shared) shouldEqual None
    }

    "immediately resolve failed exclusive tryAcquire" in {
      val name = "resource1"
      lockManager.tryAcquireLock(name, LockType.Exclusive).value
      lockManager.tryAcquireLock(name, LockType.Exclusive) shouldEqual None
    }

    "immediately fail try* if waiting for another process" in {
      val sync = new TestSynchronizer

      val name = "resource1"
      val lockFilePath = FileLockManager
        .lockPath(getTestDirectory, name)
        .toAbsolutePath
        .normalize

      val otherProcess = start(
        Seq("java", "-jar", "locking-test-helper.jar", lockFilePath.toString),
        Seq()
      )

      try {
        otherProcess.waitForMessageOnErrorStream("Lock acquired", 15)

        sync.startThread("exclusive-waiting") {
          assert(
            otherProcess.isAlive,
            "Other process should have acquired lock successfully and be waiting"
          )
          withClue(
            "The exclusive lock should not be acquired immediately as the " +
            "other process is holding a shared lock."
          ) {
            lockManager.tryAcquireLock(
              name,
              LockType.Exclusive
            ) shouldEqual None
          }
          sync.signal("waited")
          val lock = lockManager.acquireLock(name, LockType.Exclusive)
          sync.report("exclusive-acquired")
          lock.release()
        }

        sync.waitFor("waited")
        Thread.sleep(200) // ensure that our thread has started waiting

        /** This would normally succeed, but the [[lockManager]] is busy waiting
          * for the exclusive lock, so it should fail.
          *
          * If it succeeds, that means that the sleep above was too short and that
          * the exclusive lock did not start waiting yet. Or alternatively, that
          * it was already acquired and released but that is later checked by
          * ensuring correct order of event reports.
          */
        lockManager.tryAcquireLock(name, LockType.Shared) shouldEqual None

        sync.report("try-acquire-was-busy")

        otherProcess.sendToInputStream("Close lock\n")

        otherProcess.join(timeoutSeconds = 2).exitCode shouldEqual 0

        sync.join()
        sync.summarizeReports() shouldEqual Seq(
          "try-acquire-was-busy",
          "exclusive-acquired"
        )
      } finally {
        otherProcess.kill()
      }
    }

    "handle distinct resources independently" in {
      val r1 = lockManager.acquireLock("resource1", LockType.Exclusive)
      val r2 = lockManager.acquireLock("resource2", LockType.Exclusive)
      r1.release()
      r2.release()
    }
  }
}
