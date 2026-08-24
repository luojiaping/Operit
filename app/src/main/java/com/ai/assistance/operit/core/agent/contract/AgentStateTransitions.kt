package com.ai.assistance.operit.core.agent.contract

object AgentStateTransitions {
    fun canTransition(from: AgentSessionStatus, to: AgentSessionStatus): Boolean {
        if (from == to) return true
        return when (from) {
            AgentSessionStatus.IDLE ->
                to in setOf(
                    AgentSessionStatus.RUNNING,
                    AgentSessionStatus.COMPLETED,
                    AgentSessionStatus.FAILED,
                    AgentSessionStatus.CANCELLED,
                )
            AgentSessionStatus.RUNNING ->
                to in setOf(
                    AgentSessionStatus.IDLE,
                    AgentSessionStatus.COMPLETED,
                    AgentSessionStatus.FAILED,
                    AgentSessionStatus.CANCELLED,
                )
            AgentSessionStatus.COMPLETED,
            AgentSessionStatus.FAILED,
            AgentSessionStatus.CANCELLED -> false
        }
    }

    fun canTransition(from: AgentRunStatus, to: AgentRunStatus): Boolean {
        if (from == to) return true
        return when (from) {
            AgentRunStatus.QUEUED ->
                to in setOf(AgentRunStatus.RUNNING, AgentRunStatus.FAILED, AgentRunStatus.CANCELLED)
            AgentRunStatus.RUNNING ->
                to in setOf(
                    AgentRunStatus.WAITING_PERMISSION,
                    AgentRunStatus.WAITING_TOOL,
                    AgentRunStatus.WAITING_CHILD,
                    AgentRunStatus.COMPACTING,
                    AgentRunStatus.COMPLETED,
                    AgentRunStatus.FAILED,
                    AgentRunStatus.CANCELLED,
                )
            AgentRunStatus.WAITING_PERMISSION,
            AgentRunStatus.WAITING_TOOL,
            AgentRunStatus.WAITING_CHILD,
            AgentRunStatus.COMPACTING ->
                to in setOf(AgentRunStatus.RUNNING, AgentRunStatus.FAILED, AgentRunStatus.CANCELLED)
            AgentRunStatus.COMPLETED,
            AgentRunStatus.FAILED,
            AgentRunStatus.CANCELLED -> false
        }
    }

    fun canTransition(from: AgentStepStatus, to: AgentStepStatus): Boolean {
        if (from == to) return true
        return when (from) {
            AgentStepStatus.QUEUED ->
                to in setOf(AgentStepStatus.RUNNING, AgentStepStatus.FAILED, AgentStepStatus.CANCELLED)
            AgentStepStatus.RUNNING ->
                to in setOf(AgentStepStatus.COMPLETED, AgentStepStatus.FAILED, AgentStepStatus.CANCELLED)
            AgentStepStatus.COMPLETED,
            AgentStepStatus.FAILED,
            AgentStepStatus.CANCELLED -> false
        }
    }

    fun canTransition(from: AgentToolCallStatus, to: AgentToolCallStatus): Boolean {
        if (from == to) return true
        return when (from) {
            AgentToolCallStatus.QUEUED -> to in setOf(
                AgentToolCallStatus.RUNNING,
                AgentToolCallStatus.FAILED,
                AgentToolCallStatus.CANCELLED,
            )
            AgentToolCallStatus.RUNNING -> to in setOf(
                AgentToolCallStatus.COMPLETED,
                AgentToolCallStatus.FAILED,
                AgentToolCallStatus.CANCELLED,
            )
            AgentToolCallStatus.COMPLETED,
            AgentToolCallStatus.FAILED,
            AgentToolCallStatus.CANCELLED -> false
        }
    }
}
