package com.ai.assistance.operit.plugins.chatview

import android.content.Context
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.ai.assistance.operit.ui.common.markdown.XmlRenderPluginRegistry
import com.ai.assistance.operit.ui.common.markdown.XmlRenderResult
import com.ai.assistance.operit.util.AppLogger
import java.util.concurrent.CopyOnWriteArrayList
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

object ChatInputSlots {
    const val ABOVE_INPUT = "above_input"
    const val INPUT_DRAWER = "input_drawer"
    const val INPUT_TOOLBAR_RIGHT = "input_toolbar_right"
}

/** Render parameters supplied by the host chat input surface. */
data class ChatInputSlotRenderParams(
    val context: Context,
    val slot: String,
    val chatId: String? = null,
    val runtime: String,
    val inputStyle: String = "",
    val isProcessing: Boolean = false,
    val isInputFocused: Boolean = false,
    val inputText: String = ""
)

sealed class ChatInputSlotRenderResult {
    data class Text(val text: String) : ChatInputSlotRenderResult()

    data class ComposeDslScreen(
        val containerPackageName: String,
        val screenPath: String,
        val state: Map<String, Any?> = emptyMap(),
        val memo: Map<String, Any?> = emptyMap(),
        val moduleSpec: Map<String, Any?>? = null
    ) : ChatInputSlotRenderResult()
}

/** Host-owned UI regions inside the chat input surface. */
interface ChatInputSlotPlugin {
    val id: String

    fun supports(slot: String): Boolean

    suspend fun resolve(params: ChatInputSlotRenderParams): List<ChatInputSlotRenderResult>
}

object ChatInputSlotPluginRegistry {
    private const val TAG = "ChatInputSlots"
    private val plugins = CopyOnWriteArrayList<ChatInputSlotPlugin>()
    private val changeVersionMutable = MutableStateFlow(0)
    val changeVersion: StateFlow<Int> = changeVersionMutable.asStateFlow()

    @Synchronized
    fun register(plugin: ChatInputSlotPlugin) {
        unregister(plugin.id)
        plugins.add(plugin)
        notifyChanged()
    }

    @Synchronized
    fun unregister(pluginId: String) {
        val changed = plugins.removeAll { it.id == pluginId }
        if (changed) {
            notifyChanged()
        }
    }

    fun notifyChanged() {
        changeVersionMutable.update { current -> current + 1 }
    }

    @Composable
    fun RenderSlot(
        params: ChatInputSlotRenderParams,
        modifier: Modifier = Modifier
    ): Boolean {
        val registryVersion = changeVersion.collectAsState().value
        val slotName = params.slot.trim().lowercase()
        val matchedPlugins = plugins.filter { plugin -> plugin.supports(slotName) }
        if (matchedPlugins.isEmpty()) {
            return false
        }

        // `inputText` is intentionally not part of the effect keys: keystrokes must not
        // re-resolve every registered hook and re-render their compose_dsl screens.
        var results by remember(
            registryVersion,
            slotName,
            params.chatId,
            params.runtime,
            params.inputStyle,
            params.isProcessing,
            params.isInputFocused
        ) {
            mutableStateOf(emptyList<ChatInputSlotRenderResult>())
        }

        LaunchedEffect(
            registryVersion,
            slotName,
            params.chatId,
            params.runtime,
            params.inputStyle,
            params.isProcessing,
            params.isInputFocused
        ) {
            val resolved = mutableListOf<ChatInputSlotRenderResult>()
            matchedPlugins.forEach { plugin ->
                try {
                    resolved.addAll(plugin.resolve(params.copy(slot = slotName)))
                } catch (error: CancellationException) {
                    throw error
                } catch (error: Exception) {
                    AppLogger.e(
                        TAG,
                        "Chat input slot plugin failed: ${plugin.id}, slot=$slotName",
                        error
                    )
                }
            }
            results = resolved
        }

        if (results.isEmpty()) {
            return false
        }

        Column(modifier = modifier) {
            results.forEachIndexed { index, result ->
                when (result) {
                    is ChatInputSlotRenderResult.Text -> {
                        Text(
                            text = result.text,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 4.dp)
                        )
                    }

                    is ChatInputSlotRenderResult.ComposeDslScreen -> {
                        XmlRenderPluginRegistry.RenderComposeDslScreen(
                            result = XmlRenderResult.ComposeDslScreen(
                                containerPackageName = result.containerPackageName,
                                screenPath = result.screenPath,
                                state = result.state,
                                memo = result.memo,
                                moduleSpec = result.moduleSpec
                            ),
                            modifier = Modifier.fillMaxWidth(),
                            xmlStream = null,
                            renderInstanceKey =
                                "input_slot:$slotName:${result.containerPackageName}:${result.screenPath}:$index"
                        )
                    }
                }
            }
        }
        return true
    }
}
