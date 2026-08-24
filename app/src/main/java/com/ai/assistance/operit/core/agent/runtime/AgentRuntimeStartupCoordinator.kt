package com.ai.assistance.operit.core.agent.runtime

import com.ai.assistance.operit.util.AppLogger
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * Owns the one process-start recovery pass required before an Agent run can be reserved.
 *
 * Recovery is asynchronous so application startup does not block the main thread. Callers must
 * await [awaitReady] before resolving an Agent invocation; a failed recovery keeps the runtime
 * unavailable instead of allowing a run to race the failed startup pass.
 */
class AgentRuntimeStartupCoordinator(
    private val recoverInterruptedRuns: suspend () -> Int,
) {
    private val started = AtomicBoolean(false)
    private val ready = CompletableDeferred<Int>()
    private var recoveryJob: Job? = null

    fun start(scope: CoroutineScope) {
        check(started.compareAndSet(false, true)) {
            "Agent runtime startup recovery has already started"
        }
        recoveryJob =
            scope.launch(Dispatchers.IO) {
                try {
                    ready.complete(recoverInterruptedRuns())
                } catch (cancellation: CancellationException) {
                    ready.cancel(cancellation)
                    throw cancellation
                } catch (error: Throwable) {
                    AppLogger.e(TAG, "Agent runtime startup recovery failed", error)
                    ready.completeExceptionally(error)
                }
            }
    }

    suspend fun awaitReady(): Int {
        check(started.get()) {
            "Agent runtime startup recovery has not started"
        }
        return ready.await()
    }

    fun isReady(): Boolean = ready.isCompleted && !ready.isCancelled

    fun cancel() {
        recoveryJob?.cancel()
    }

    private companion object {
        const val TAG = "AgentRuntimeStartup"
    }
}
