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
        val stepId = AgentStepId.generate()
        val modelRequestId = AgentModelRequestId.generate()

        assertTrue(sessionId.value.isNotBlank())
        assertTrue(runId.value.isNotBlank())
        assertTrue(callId.value.isNotBlank())
        assertTrue(stepId.value.isNotBlank())
        assertTrue(modelRequestId.value.isNotBlank())
        assertEquals(
            5,
            setOf(sessionId.value, runId.value, callId.value, stepId.value, modelRequestId.value).size,
        )
    }

    @Test
    fun capabilitiesAreStable() {
        assertEquals("agent_runtime_v1", AgentCapabilities.RUNTIME_V1)
        assertEquals("agent_ui_v1", AgentCapabilities.UI_V1)
    }

    @Test
    fun terminalStatesCannotResume() {
        assertTrue(
            !AgentStateTransitions.canTransition(
                AgentSessionStatus.COMPLETED,
                AgentSessionStatus.RUNNING,
            )
        )
        assertTrue(!AgentStateTransitions.canTransition(AgentToolCallStatus.CANCELLED, AgentToolCallStatus.RUNNING))
        assertTrue(AgentStateTransitions.canTransition(AgentSessionStatus.RUNNING, AgentSessionStatus.IDLE))
        assertTrue(!AgentStateTransitions.canTransition(AgentRunStatus.COMPLETED, AgentRunStatus.RUNNING))
    }
}
