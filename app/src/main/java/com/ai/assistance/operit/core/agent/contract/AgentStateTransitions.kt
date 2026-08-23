package com.ai.assistance.operit.core.agent.contract

object AgentStateTransitions {
    fun canTransition(from: AgentStatus, to: AgentStatus): Boolean {
        if (from == to) return true
        return when (from) {
            AgentStatus.IDLE -> to in setOf(AgentStatus.QUEUED, AgentStatus.RUNNING, AgentStatus.CANCELLED)
            AgentStatus.QUEUED -> to in setOf(AgentStatus.RUNNING, AgentStatus.FAILED, AgentStatus.CANCELLED)
            AgentStatus.RUNNING -> to in setOf(
                AgentStatus.WAITING_PERMISSION,
                AgentStatus.WAITING_CHILD,
                AgentStatus.COMPACTING,
                AgentStatus.COMPLETED,
                AgentStatus.FAILED,
                AgentStatus.CANCELLED,
            )
            AgentStatus.WAITING_PERMISSION,
            AgentStatus.WAITING_CHILD,
            AgentStatus.COMPACTING -> to in setOf(AgentStatus.RUNNING, AgentStatus.FAILED, AgentStatus.CANCELLED)
            AgentStatus.COMPLETED,
            AgentStatus.FAILED,
            AgentStatus.CANCELLED -> false
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
