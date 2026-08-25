package com.ai.assistance.operit.plugins.toolpkg

import com.ai.assistance.operit.plugins.OperitPlugin

internal object DeepSeekWhaleHostBridgePlugin : OperitPlugin {
    override val id: String = "builtin.toolpkg.deepseek-whale-data"

    override fun register() {
        DeepSeekWhaleHostService.register()
    }
}
