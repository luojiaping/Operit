package com.ai.assistance.operit.core.agent.contract

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AgentContractTest {
    @Test
    fun generatedIdsAreNonEmptyAndDistinct() {
        val sessionId = AgentSessionId.generate()
        val runId = AgentRunId.generate()
        val callId = AgentToolCallId.generate()

        assertTrue(sessionId.value.isNotBlank())
        assertTrue(runId.value.isNotBlank())
        assertTrue(callId.value.isNotBlank())
        assertEquals(3, setOf(sessionId.value, runId.value, callId.value).size)
    }

    @Test
    fun capabilitiesAreStable() {
        assertEquals("agent_runtime_v1", AgentCapabilities.RUNTIME_V1)
        assertEquals("agent_ui_v1", AgentCapabilities.UI_V1)
    }

    @Test
    fun terminalStatesCannotResume() {
        assertTrue(!AgentStateTransitions.canTransition(AgentStatus.COMPLETED, AgentStatus.RUNNING))
        assertTrue(!AgentStateTransitions.canTransition(AgentToolCallStatus.CANCELLED, AgentToolCallStatus.RUNNING))
        assertTrue(AgentStateTransitions.canTransition(AgentStatus.RUNNING, AgentStatus.RUNNING))
    }
}
