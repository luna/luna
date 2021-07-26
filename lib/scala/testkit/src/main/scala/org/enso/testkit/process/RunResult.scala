package org.enso.testkit.process

/** A result of running a process.
  *
  * @param exitCode the returned exit code
  * @param stdout contents of the standard output stream
  * @param stderr contents of the standard error stream
  */
case class RunResult(exitCode: Int, stdout: String, stderr: String)
