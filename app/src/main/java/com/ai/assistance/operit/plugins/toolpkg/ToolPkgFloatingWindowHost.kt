package com.ai.assistance.operit.plugins.toolpkg

import com.ai.assistance.operit.core.tools.packTool.PackageManager
import com.ai.assistance.operit.services.floating.ToolPkgFloatingWindowService
import java.util.concurrent.atomic.AtomicBoolean
import org.json.JSONObject

internal object ToolPkgFloatingWindowHost {
    const val CAPABILITY = "toolpkg.floating_window.v1"
    private val registered = AtomicBoolean(false)

    fun register() {
        if (!registered.compareAndSet(false, true)) {
            return
        }
        ToolPkgHostBridge.register(CAPABILITY, ToolPkgHostBridge.Handler(::handle))
    }

    private fun handle(request: ToolPkgHostBridge.Request): JSONObject {
        val operation = request.payload.optString("operation").trim().lowercase()
        val windowId = request.payload.optString("windowId").trim()
        require(operation == "show" || operation == "hide" || operation == "update") {
            "Unsupported floating window operation: $operation"
        }
        require(windowId.isNotBlank()) { "Floating window id is required" }

        val window =
            request.packageManager
                .getToolPkgFloatingWindows(resolveContext = request.context)
                .firstOrNull { candidate ->
                    candidate.containerPackageName == request.packageName &&
                        candidate.windowId.equals(windowId, ignoreCase = true)
                } ?: error("Floating window is not registered: $windowId")

        val command =
            JSONObject()
                .put("operation", operation)
                .put("packageName", request.packageName)
                .put("windowId", window.windowId)
                .put("spec", serializeWindow(window))
        request.payload.optJSONObject("routeArgs")?.let { routeArgs ->
            command.put("routeArgs", routeArgs)
        }
        request.payload.optJSONObject("patch")?.let { patch ->
            command.put("patch", patch)
        }
        val result = ToolPkgFloatingWindowService.dispatch(request.context, command)
        if (result.optString("status").equals("error", ignoreCase = true)) {
            throw IllegalStateException(
                result.optString("errorMessage").trim().ifBlank {
                    "ToolPkg floating window operation failed"
                }
            )
        }
        return result
    }

    private fun serializeWindow(window: PackageManager.ToolPkgFloatingWindow): JSONObject {
        return JSONObject()
            .put("packageName", window.containerPackageName)
            .put("windowId", window.windowId)
            .put("contentRouteId", window.contentRouteId)
            .put("title", window.title)
            .put("description", window.description)
            .put("icon", window.icon ?: JSONObject.NULL)
            .put("widthDp", window.widthDp)
            .put("heightDp", window.heightDp)
            .put("draggable", window.draggable)
            .put("resizable", window.resizable)
            .put("refreshIntervalMs", window.refreshIntervalMs)
            .put("refreshFunction", window.refreshFunction ?: JSONObject.NULL)
            .put("refreshFunctionSource", window.refreshFunctionSource ?: JSONObject.NULL)
    }
}
