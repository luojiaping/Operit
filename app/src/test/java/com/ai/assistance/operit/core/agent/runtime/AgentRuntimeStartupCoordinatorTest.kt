package com.ai.assistance.operit.core.agent.runtime

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AgentRuntimeStartupCoordinatorTest {
    @Test
    fun recoveryRunsOnceBeforeReadinessCompletes() = runBlocking {
        var recoveryCount = 0
        val coordinator =
            AgentRuntimeStartupCoordinator {
                recoveryCount += 1
                3
            }

        coordinator.start(this)

        assertEquals(3, coordinator.awaitReady())
        assertEquals(1, recoveryCount)
        assertTrue(coordinator.isReady())

        var rejectedSecondStart = false
        try {
            coordinator.start(this)
        } catch (error: IllegalStateException) {
            rejectedSecondStart = true
            assertEquals("Agent runtime startup recovery has already started", error.message)
        }
        assertTrue(rejectedSecondStart)
    }
}
