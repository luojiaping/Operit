package com.ai.assistance.operit.plugins.toolpkg

import com.ai.assistance.operit.core.agent.registry.AgentPluginRegistry
import com.ai.assistance.operit.core.tools.packTool.PackageManager
import com.ai.assistance.operit.core.tools.packTool.ToolPkgContainerRuntime
import com.ai.assistance.operit.plugins.OperitPlugin
import java.util.concurrent.atomic.AtomicBoolean

object ToolPkgAgentProfileBridge : OperitPlugin {
    private val installed = AtomicBoolean(false)
    private val synchronizedPackageIds = linkedSetOf<String>()

    override val id: String = "operit.toolpkg.agent-profile-bridge"

    private val runtimeChangeListener =
        PackageManager.ToolPkgRuntimeChangeListener { activeContainers ->
            sync(activeContainers)
        }

    override fun register() {
        if (!installed.compareAndSet(false, true)) {
            return
        }
        val manager = toolPkgPackageManager()
        manager.addToolPkgRuntimeChangeListener(runtimeChangeListener)
        sync(manager.getEnabledToolPkgContainerRuntimes())
    }

    private fun sync(activeContainers: List<ToolPkgContainerRuntime>) {
        synchronized(synchronizedPackageIds) {
            synchronizedPackageIds.forEach { pluginId ->
                AgentPluginRegistry.global.removePlugin(pluginId)
            }
            synchronizedPackageIds.clear()
            activeContainers.forEach { runtime ->
                if (runtime.agentProfiles.isNotEmpty()) {
                    runtime.agentProfiles.forEach(AgentPluginRegistry.global::register)
                    synchronizedPackageIds += runtime.packageName
                }
            }
        }
    }
}
