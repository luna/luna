package org.enso.interpreter.instrument.job

import java.util.UUID

import org.enso.interpreter.instrument.InstrumentFrame
import org.enso.interpreter.instrument.execution.{Executable, RuntimeContext}
import org.enso.polyglot.runtime.Runtime.Api

/** A job responsible for executing a call stack for the provided context.
  *
  * @param contextId an identifier of a context to execute
  * @param stack a call stack to execute
  */
class ExecuteJob(
  contextId: UUID,
  stack: List[InstrumentFrame]
) extends Job[Unit](
      List(contextId),
      isCancellable = true,
      // TODO[MK]: make this interruptible when https://github.com/oracle/graal/issues/3590
      // is resolved
      mayInterruptIfRunning = false
    ) {

  def this(exe: Executable) =
    this(exe.contextId, exe.stack.toList)

  /** @inheritdoc */
  override def run(implicit ctx: RuntimeContext): Unit = {
    ctx.locking.acquireContextLock(contextId)
    ctx.locking.acquireReadCompilationLock()
    ctx.executionService.getContext.getThreadManager.enter()
    try {
      val outcome = ProgramExecutionSupport.runProgram(contextId, stack)
      outcome.foreach {
        case diagnostic: Api.ExecutionResult.Diagnostic =>
          ctx.endpoint.sendToClient(
            Api.Response(Api.ExecutionUpdate(contextId, Seq(diagnostic)))
          )
        case failure: Api.ExecutionResult.Failure =>
          ctx.endpoint.sendToClient(
            Api.Response(Api.ExecutionFailed(contextId, failure))
          )
      }
      ctx.endpoint.sendToClient(
        Api.Response(Api.ExecutionComplete(contextId))
      )
    } finally {
      ctx.executionService.getContext.getThreadManager.leave()
      ctx.locking.releaseReadCompilationLock()
      ctx.locking.releaseContextLock(contextId)
    }
  }

}
