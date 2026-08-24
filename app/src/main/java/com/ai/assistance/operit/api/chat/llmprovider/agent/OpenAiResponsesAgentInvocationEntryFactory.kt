package com.ai.assistance.operit.api.chat.llmprovider.agent

import android.content.Context
import com.ai.assistance.operit.core.agent.runtime.AgentInvocationEntry
import com.ai.assistance.operit.core.agent.runtime.AgentInvocationException
import com.ai.assistance.operit.core.application.OperitApplication
import com.ai.assistance.operit.data.preferences.ModelConfigManager
import com.ai.assistance.operit.data.repository.AgentExecutionRepository

object OpenAiResponsesAgentInvocationEntryFactory {
    fun create(context: Context): AgentInvocationEntry {
        val application = context.applicationContext as? OperitApplication
            ?: throw AgentInvocationException(
                "Agent invocation requires OperitApplication as the application context"
            )
        val repository = AgentExecutionRepository.getInstance(context)
        return AgentInvocationEntry(
            routeResolver = repository::resolveRoute,
            store = repository,
            modelResolver =
                OpenAiResponsesAgentModelResolver(
                    modelConfigManager = ModelConfigManager(context),
                ),
            startupCoordinator = application.getAgentRuntimeStartupCoordinator(),
        )
    }
}
