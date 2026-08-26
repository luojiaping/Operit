package com.ai.assistance.operit.services.floating

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.media.AudioAttributes
import android.media.SoundPool
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.view.Choreographer
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.WindowManager
import android.widget.FrameLayout
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.AccelerateInterpolator
import android.view.animation.DecelerateInterpolator
import android.view.animation.Interpolator
import android.view.animation.LinearInterpolator
import android.view.animation.OvershootInterpolator
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.ai.assistance.operit.R
import com.ai.assistance.operit.core.application.ForegroundServiceCompat
import com.ai.assistance.operit.core.tools.AIToolHandler
import com.ai.assistance.operit.core.tools.javascript.JsEngine
import com.ai.assistance.operit.core.tools.packTool.PackageManager
import com.ai.assistance.operit.core.tools.packTool.ToolPkgComposeDslParser
import com.ai.assistance.operit.core.tools.packTool.ToolPkgComposeDslRenderResult
import com.ai.assistance.operit.core.tools.packTool.ToolPkgFloatingWindowAnimation
import com.ai.assistance.operit.core.tools.packTool.ToolPkgFloatingWindowFeedback
import com.ai.assistance.operit.core.tools.packTool.ToolPkgFloatingWindowFollow
import com.ai.assistance.operit.core.tools.packTool.normalizeToolPkgFloatingWindowAnimationEasing
import com.ai.assistance.operit.ui.common.composedsl.RenderToolPkgComposeDslNode
import com.ai.assistance.operit.util.AppLogger
import com.ai.assistance.operit.services.ServiceLifecycleOwner
import java.io.File
import java.util.UUID
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.cancel
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONObject

private const val TOOLPKG_FLOATING_PREFS_NAME = "toolpkg_floating_windows_v4"
private const val FLOATING_WINDOW_STATE_SCHEMA_VERSION = 4
private const val DEFAULT_FLOATING_SNAP_MODE = "quarter"
private const val MIN_FLOATING_WIDTH_DP = 72
private const val MIN_FLOATING_HEIGHT_DP = 72
private const val MIN_FLOATING_ALPHA = 0.2f
private const val MAX_FLOATING_ALPHA = 1f
private const val TOOLPKG_FLOATING_ARGS_PREFIX = "args:"

private fun normalizeSnapMode(value: String?): String {
    return if (value.equals("none", ignoreCase = true)) "none" else DEFAULT_FLOATING_SNAP_MODE
}

private fun serializeFloatingWindowFollow(follow: ToolPkgFloatingWindowFollow): JSONObject {
    return JSONObject()
        .put("windowId", follow.windowId)
        .put("placement", follow.placement)
        .put(
            "offsetDp",
            JSONObject()
                .put("x", follow.offsetXDp.toDouble())
                .put("y", follow.offsetYDp.toDouble())
        )
}

private fun serializeFloatingWindowAnimation(animation: ToolPkgFloatingWindowAnimation): JSONObject {
    return JSONObject()
        .put("scaleX", animation.scaleX.toDouble())
        .put("scaleY", animation.scaleY.toDouble())
        .put("alpha", animation.alpha.toDouble())
        .put("translationXDp", animation.translationXDp.toDouble())
        .put("translationYDp", animation.translationYDp.toDouble())
        .put("durationMs", animation.durationMs)
        .put("easing", animation.easing)
        .put("pivotX", animation.pivotX.toDouble())
        .put("pivotY", animation.pivotY.toDouble())
}

private fun serializeFloatingWindowFeedback(feedback: ToolPkgFloatingWindowFeedback): JSONObject {
    return JSONObject()
        .put("soundResource", feedback.soundResource ?: JSONObject.NULL)
        .put("animation", feedback.animation?.let(::serializeFloatingWindowAnimation) ?: JSONObject.NULL)
}

private fun serializeFloatingWindowFeedback(feedback: PackageManager.ToolPkgFloatingWindowFeedback): JSONObject {
    return JSONObject()
        .put("soundResource", feedback.soundResource ?: JSONObject.NULL)
        .put(
            "animation",
            feedback.animation?.let { animation ->
                JSONObject()
                    .put("scaleX", animation.scaleX.toDouble())
                    .put("scaleY", animation.scaleY.toDouble())
                    .put("alpha", animation.alpha.toDouble())
                    .put("translationXDp", animation.translationXDp.toDouble())
                    .put("translationYDp", animation.translationYDp.toDouble())
                    .put("durationMs", animation.durationMs)
                    .put("easing", animation.easing)
                    .put("pivotX", animation.pivotX.toDouble())
                    .put("pivotY", animation.pivotY.toDouble())
            } ?: JSONObject.NULL
        )
}

private fun parseFloatingWindowFollow(json: JSONObject?): ToolPkgFloatingWindowFollow? {
    if (json == null) return null
    val offset = json.optJSONObject("offsetDp")
    return ToolPkgFloatingWindowFollow(
        windowId = json.getString("windowId").trim(),
        placement = json.getString("placement").trim().lowercase(),
        offsetXDp = offset?.optDouble("x", 0.0)?.toFloat() ?: 0f,
        offsetYDp = offset?.optDouble("y", 0.0)?.toFloat() ?: 0f
    )
}

private fun parseFloatingWindowAnimation(
    json: JSONObject?,
    base: ToolPkgFloatingWindowAnimation? = null
): ToolPkgFloatingWindowAnimation? {
    if (json == null) return null
    val defaults = base ?: ToolPkgFloatingWindowAnimation()
    val animation = ToolPkgFloatingWindowAnimation(
        scaleX = json.optDouble("scaleX", defaults.scaleX.toDouble()).toFloat(),
        scaleY = json.optDouble("scaleY", defaults.scaleY.toDouble()).toFloat(),
        alpha = json.optDouble("alpha", defaults.alpha.toDouble()).toFloat(),
        translationXDp = json.optDouble("translationXDp", defaults.translationXDp.toDouble()).toFloat(),
        translationYDp = json.optDouble("translationYDp", defaults.translationYDp.toDouble()).toFloat(),
        durationMs = json.optLong("durationMs", defaults.durationMs),
        easing = normalizeToolPkgFloatingWindowAnimationEasing(json.optString("easing", defaults.easing)),
        pivotX = json.optDouble("pivotX", defaults.pivotX.toDouble()).toFloat(),
        pivotY = json.optDouble("pivotY", defaults.pivotY.toDouble()).toFloat()
    )
    require(animation.scaleX in 0f..4f && animation.scaleY in 0f..4f) {
        "Floating window animation scale is outside supported bounds"
    }
    require(animation.alpha in 0f..1f) {
        "Floating window animation alpha is outside supported bounds"
    }
    require(
        animation.translationXDp in -1200f..1200f &&
            animation.translationYDp in -1600f..1600f
    ) {
        "Floating window animation translation is outside supported bounds"
    }
    require(animation.durationMs in 0L..5000L) {
        "Floating window animation duration is outside supported bounds"
    }
    require(animation.pivotX in 0f..1f && animation.pivotY in 0f..1f) {
        "Floating window animation pivot is outside supported bounds"
    }
    return animation
}

private fun parseFloatingWindowFeedback(json: JSONObject?): ToolPkgFloatingWindowFeedback {
    if (json == null) return ToolPkgFloatingWindowFeedback()
    val soundResource = if (json.isNull("soundResource")) {
        null
    } else {
        json.optString("soundResource").trim().ifBlank { null }
    }
    val animation = if (!json.has("animation") || json.isNull("animation")) {
        null
    } else {
        parseFloatingWindowAnimation(json.optJSONObject("animation")
            ?: error("Floating window feedback animation must be a JSON object"))
    }
    return ToolPkgFloatingWindowFeedback(
        soundResource = soundResource,
        animation = animation
    )
}

private fun patchFloatingWindowFeedback(
    current: ToolPkgFloatingWindowFeedback,
    json: JSONObject
): ToolPkgFloatingWindowFeedback {
    val soundResource = if (!json.has("soundResource")) {
        current.soundResource
    } else if (json.isNull("soundResource")) {
        null
    } else {
        val value = json.opt("soundResource")
        require(value is String) { "Floating window feedback soundResource must be a string" }
        value.trim().ifBlank { null }
    }
    val animation = if (!json.has("animation")) {
        current.animation
    } else if (json.isNull("animation")) {
        null
    } else {
        parseFloatingWindowAnimation(
            json.optJSONObject("animation")
                ?: error("Floating window feedback animation must be a JSON object"),
            current.animation
        )
    }
    return ToolPkgFloatingWindowFeedback(
        soundResource = soundResource,
        animation = animation
    )
}

private fun readPersistedFloatingWindowFeedback(
    preferences: android.content.SharedPreferences,
    storageKey: String,
    prefix: String,
    registration: ToolPkgFloatingWindowFeedback
): ToolPkgFloatingWindowFeedback {
    val soundResource = preferences.getString(
        "$prefixSoundResource:$storageKey",
        registration.soundResource
    )?.trim()?.ifBlank { null }
    val animation = if (!preferences.contains("$prefixAnimation:$storageKey")) {
        registration.animation
    } else {
        preferences.getString("$prefixAnimation:$storageKey", "")
            .orEmpty()
            .takeIf { it.isNotBlank() }
            ?.let { raw -> parseFloatingWindowAnimation(JSONObject(raw)) }
    }
    return registration.copy(
        soundResource = soundResource,
        animation = animation
    )
}

class ToolPkgFloatingWindowService : Service() {
    companion object {
        private const val TAG = "ToolPkgFloatingWindowService"
        private const val NOTIFICATION_ID = 1017
        private const val CHANNEL_ID = "toolpkg_floating_window"
        private const val EXTRA_COMMAND_ID = "toolpkg_command_id"
        private const val EXTRA_COMMAND_JSON = "toolpkg_command_json"
        private const val VISIBLE_PREFIX = "visible:"

        @Volatile
        private var instance: ToolPkgFloatingWindowService? = null
        private val pendingCommands = ConcurrentHashMap<String, CompletableFuture<JSONObject>>()

        fun dispatch(context: Context, command: JSONObject): JSONObject {
            val active = instance
            if (active != null) {
                return active.executeCommandBlocking(command)
            }

            val commandId = UUID.randomUUID().toString()
            val future = CompletableFuture<JSONObject>()
            pendingCommands[commandId] = future
            val intent = Intent(context, ToolPkgFloatingWindowService::class.java)
                .putExtra(EXTRA_COMMAND_ID, commandId)
                .putExtra(EXTRA_COMMAND_JSON, command.toString())
            try {
                ContextCompat.startForegroundService(context, intent)
                return future.get(6, TimeUnit.SECONDS)
            } catch (error: Exception) {
                pendingCommands.remove(commandId)
                AppLogger.e(TAG, "Failed to dispatch ToolPkg floating window command", error)
                return errorState("service_start_failed", error.message)
            }
        }

        fun getPersistedState(context: Context, command: JSONObject): JSONObject {
            val active = instance
            if (active != null) {
                return active.executeCommandBlocking(command)
            }
            val spec = FloatingWindowSpec.fromJson(command.getJSONObject("spec"))
            val key = "${spec.packageName}:${spec.windowId}"
            val prefs = context.getSharedPreferences(TOOLPKG_FLOATING_PREFS_NAME, Context.MODE_PRIVATE)
            val density = context.resources.displayMetrics.density
            val widthDp = prefs.getInt("widthDp:$key", spec.widthDp)
            val heightDp = prefs.getInt("heightDp:$key", spec.heightDp)
            val widthPx = (widthDp * density).toInt().coerceAtLeast(1)
            val heightPx = (heightDp * density).toInt().coerceAtLeast(1)
            return JSONObject()
                .put("schemaVersion", FLOATING_WINDOW_STATE_SCHEMA_VERSION)
                .put("windowId", spec.windowId)
                .put("contentRoute", spec.contentRouteId)
                .put("status", "hidden")
                .put("widthDp", widthDp)
                .put("heightDp", heightDp)
                .put("draggable", spec.draggable)
                .put("resizable", spec.resizable)
                .put("alpha", prefs.getFloat("alpha:$key", 1f).coerceIn(MIN_FLOATING_ALPHA, MAX_FLOATING_ALPHA).toDouble())
                .put("x", prefs.getInt("x:$key", (context.resources.displayMetrics.widthPixels - widthPx).coerceAtLeast(0)))
                .put("y", prefs.getInt("y:$key", (context.resources.displayMetrics.heightPixels - heightPx).coerceAtLeast(0)))
                .put("snapMode", normalizeSnapMode(prefs.getString("snapMode:$key", spec.snapMode)))
                .put("follow", spec.follow?.let(::serializeFloatingWindowFollow) ?: JSONObject.NULL)
                .put("soundEnabled", prefs.getBoolean("soundEnabled:$key", true))
                .put("soundVolume", prefs.getFloat("soundVolume:$key", 1f).coerceIn(0f, 1f).toDouble())
                .put(
                    "pressFeedback",
                    serializeFloatingWindowFeedback(
                        readPersistedFloatingWindowFeedback(prefs, key, "pressFeedback", spec.pressFeedback)
                    )
                )
                .put(
                    "releaseFeedback",
                    serializeFloatingWindowFeedback(
                        readPersistedFloatingWindowFeedback(prefs, key, "releaseFeedback", spec.releaseFeedback)
                    )
                )
                .put("updatedAtMs", System.currentTimeMillis().toString())
        }

        fun onToolPkgRuntimeChanged(context: Context, activePackageNames: Set<String>) {
            instance?.removeDisabledPackages(activePackageNames)
            val preferences = context.getSharedPreferences(TOOLPKG_FLOATING_PREFS_NAME, Context.MODE_PRIVATE)
            val editor = preferences.edit()
            preferences.all.keys
                .filter { key -> key.startsWith(VISIBLE_PREFIX) }
                .map { key -> key.removePrefix(VISIBLE_PREFIX) }
                .filter { storageKey ->
                    val packageName = storageKey.substringBefore(':')
                    !activePackageNames.contains(packageName)
                }
                .forEach { storageKey ->
                    editor.putBoolean("$VISIBLE_PREFIX$storageKey", false)
                }
            editor.apply()
        }

        private fun errorState(code: String, message: String?): JSONObject {
            return JSONObject()
                .put("schemaVersion", 1)
                .put("status", "error")
                .put("errorCode", code)
                .put("errorMessage", message ?: code)
        }
    }

    private val mainHandler = Handler(Looper.getMainLooper())
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val instances = linkedMapOf<WindowKey, ToolPkgFloatingWindowInstance>()
    private lateinit var lifecycleOwner: ServiceLifecycleOwner
    private lateinit var preferences: android.content.SharedPreferences
    private var restoreStarted = false

    override fun onCreate() {
        super.onCreate()
        instance = this
        preferences = getSharedPreferences(TOOLPKG_FLOATING_PREFS_NAME, Context.MODE_PRIVATE)
        lifecycleOwner = ServiceLifecycleOwner()
        lifecycleOwner.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
        createNotificationChannel()
        ForegroundServiceCompat.startForeground(
            service = this,
            notificationId = NOTIFICATION_ID,
            notification = createNotification(),
            types = ForegroundServiceCompat.buildTypes(dataSync = true)
        )
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        lifecycleOwner.handleLifecycleEvent(Lifecycle.Event.ON_START)
        lifecycleOwner.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)
        val commandId = intent?.getStringExtra(EXTRA_COMMAND_ID).orEmpty()
        val commandJson = intent?.getStringExtra(EXTRA_COMMAND_JSON).orEmpty()
        if (commandJson.isNotBlank()) {
            val result =
                try {
                    executeCommandOnMain(JSONObject(commandJson))
                } catch (error: Exception) {
                    AppLogger.e(TAG, "ToolPkg floating window command failed", error)
                    errorState("command_failed", error.message)
                }
            if (commandId.isNotBlank()) {
                pendingCommands.remove(commandId)?.complete(result)
            }
            restoreVisibleWindows()
        } else {
            restoreVisibleWindows()
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        instances.values.toList().forEach { window ->
            window.dispose()
        }
        instances.clear()
        lifecycleOwner.handleLifecycleEvent(Lifecycle.Event.ON_PAUSE)
        lifecycleOwner.handleLifecycleEvent(Lifecycle.Event.ON_STOP)
        lifecycleOwner.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
        serviceScope.cancel()
        instance = null
        super.onDestroy()
    }

    private fun executeCommandBlocking(command: JSONObject): JSONObject {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            return executeCommandOnMain(command)
        }
        val future = CompletableFuture<JSONObject>()
        mainHandler.post {
            try {
                future.complete(executeCommandOnMain(command))
            } catch (error: Exception) {
                future.complete(errorState("command_failed", error.message))
            }
        }
        return try {
            future.get(6, TimeUnit.SECONDS)
        } catch (error: Exception) {
            AppLogger.e(TAG, "Timed out waiting for ToolPkg floating window command", error)
            errorState("command_timeout", error.message)
        }
    }

    private fun removeDisabledPackages(activePackageNames: Set<String>) {
        mainHandler.post {
            instances.keys
                .filter { key -> !activePackageNames.contains(key.packageName) }
                .toList()
                .forEach { key ->
                    hideWindow(key)
                }
        }
    }

    private fun executeCommandOnMain(command: JSONObject): JSONObject {
        check(Looper.myLooper() == Looper.getMainLooper()) { "Floating window commands require main thread" }
        val operation = command.optString("operation").trim().lowercase()
        val packageName = command.optString("packageName").trim()
        val windowId = command.optString("windowId").trim()
        require(packageName.isNotBlank()) { "Floating window package name is required" }
        require(windowId.isNotBlank()) { "Floating window id is required" }
        val key = WindowKey(packageName, windowId)
        return when (operation) {
            "show" -> showWindow(key, command)
            "hide" -> hideWindow(key, command.getJSONObject("spec"))
            "update" -> updateWindow(
                key,
                command.optJSONObject("patch") ?: JSONObject(),
                command.getJSONObject("spec")
            )
            "get" -> getWindow(key, command.getJSONObject("spec"))
            else -> errorState("invalid_operation", "Unsupported floating window operation: $operation")
        }
    }

    private fun showWindow(key: WindowKey, command: JSONObject): JSONObject {
        val spec = FloatingWindowSpec.fromJson(command.getJSONObject("spec"))
        val packageManager =
            PackageManager.getInstance(this, AIToolHandler.getInstance(this))
        packageManager.ensureInitialized()
        check(packageManager.isPackageEnabled(spec.packageName)) {
            "ToolPkg package is disabled: ${spec.packageName}"
        }
        val routeArgs = if (command.has("routeArgs")) {
            command.getJSONObject("routeArgs").toString()
        } else {
            "{}"
        }
        val persistedSnapMode = preferences.getString("snapMode:${storageKey(key)}", null)
        val effectiveSpec =
            if (persistedSnapMode.isNullOrBlank()) {
                spec
            } else {
                spec.copy(snapMode = normalizeSnapMode(persistedSnapMode))
            }
        effectiveSpec.follow?.let { follow ->
            check(instances.containsKey(WindowKey(key.packageName, follow.windowId))) {
                "Follow window anchor is not visible: ${key.packageName}:${follow.windowId}"
            }
        }
        val existing = instances[key]
        if (existing != null) {
            existing.updateRouteArgs(routeArgs)
            persistVisibility(key, true, routeArgs)
            existing.requestRender()
            positionWindowFromAnchor(existing)
            return existing.state("visible")
        }

        val created =
            ToolPkgFloatingWindowInstance(
                service = this,
                spec = effectiveSpec,
                routeArgsJson = routeArgs
            )
        return try {
            instances[key] = created
            persistVisibility(key, true, routeArgs)
            created.show()
            positionWindowFromAnchor(created)
            created.state("visible")
        } catch (error: Exception) {
            instances.remove(key)
            created.dispose()
            persistVisibility(key, false, "")
            AppLogger.e(TAG, "Failed to show ToolPkg floating window: ${key.packageName}:${key.windowId}", error)
            errorState("show_failed", error.message)
        }
    }

    private fun hideWindow(key: WindowKey, specJson: JSONObject? = null): JSONObject {
        instances.values
            .filter { instance ->
                instance.spec.follow?.windowId.equals(key.windowId, ignoreCase = true) &&
                    instance.spec.packageName == key.packageName
            }
            .map { instance -> WindowKey(instance.spec.packageName, instance.spec.windowId) }
            .forEach { followerKey -> hideWindow(followerKey) }
        val removed = instances.remove(key)
        removed?.dispose()
        persistVisibility(key, false, "")
        if (instances.isEmpty()) {
            mainHandler.postDelayed({
                if (instances.isEmpty()) {
                    stopSelf()
                }
            }, 100L)
        }
        if (removed != null) return removed.state("hidden")
        val spec = specJson?.let { FloatingWindowSpec.fromJson(it) }
            ?: error("Floating window specification is required: ${key.packageName}:${key.windowId}")
        return persistedState(key, spec, "hidden")
    }

    private fun updateWindow(key: WindowKey, patch: JSONObject, specJson: JSONObject): JSONObject {
        val window = instances[key]
        if (window == null) {
            val spec = FloatingWindowSpec.fromJson(specJson)
            persistPatch(key, patch, spec)
            return persistedState(key, spec, "hidden")
        }
        window.applyPatch(patch)
        return window.state("visible")
    }

    private fun getWindow(key: WindowKey, specJson: JSONObject): JSONObject {
        val window = instances[key]
        if (window != null) return window.state("visible")
        return persistedState(key, FloatingWindowSpec.fromJson(specJson), "hidden")
    }

    private fun positionWindowFromAnchor(window: ToolPkgFloatingWindowInstance) {
        val follow = window.spec.follow
        if (follow != null) {
            val anchor = instances[WindowKey(window.spec.packageName, follow.windowId)]
                ?: error("Follow window anchor is not visible: ${window.spec.packageName}:${follow.windowId}")
            val anchorParams = anchor.currentLayoutParams()
                ?: error("Follow window anchor layout is unavailable: ${follow.windowId}")
            val followerParams = window.currentLayoutParams()
                ?: error("Follow window layout is unavailable: ${window.spec.windowId}")
            val screenWidth = resources.displayMetrics.widthPixels
            val screenHeight = resources.displayMetrics.heightPixels
            // Keep placement and visual spacing in the package declaration; the host only applies the generic geometry.
            val (baseX, baseY) = when (follow.placement) {
                "above" -> {
                    anchorParams.x + (anchorParams.width - followerParams.width) / 2 to
                        anchorParams.y - followerParams.height
                }
                "below" -> {
                    anchorParams.x + (anchorParams.width - followerParams.width) / 2 to
                        anchorParams.y + anchorParams.height
                }
                "start" -> {
                    anchorParams.x - followerParams.width to
                        anchorParams.y + (anchorParams.height - followerParams.height) / 2
                }
                "end" -> {
                    anchorParams.x + anchorParams.width to
                        anchorParams.y + (anchorParams.height - followerParams.height) / 2
                }
                "center" -> {
                    anchorParams.x + (anchorParams.width - followerParams.width) / 2 to
                        anchorParams.y + (anchorParams.height - followerParams.height) / 2
                }
                else -> error("Unsupported floating window follow placement: ${follow.placement}")
            }
            val density = resources.displayMetrics.density
            val x = (baseX + (follow.offsetXDp * density).toInt())
                .coerceIn(0, (screenWidth - followerParams.width).coerceAtLeast(0))
            val y = (baseY + (follow.offsetYDp * density).toInt())
                .coerceIn(0, (screenHeight - followerParams.height).coerceAtLeast(0))
            window.applyFollowPosition(x, y)
        }

        positionFollowersOf(window)
    }

    internal fun onWindowPositionChanged(packageName: String, windowId: String) {
        instances[WindowKey(packageName, windowId)]?.let { window ->
            positionFollowersOf(window)
        }
    }

    internal fun onWindowLayoutChanged(packageName: String, windowId: String) {
        instances[WindowKey(packageName, windowId)]?.let { window ->
            if (window.spec.follow != null) {
                positionWindowFromAnchor(window)
            } else {
                positionFollowersOf(window)
            }
        }
    }

    private fun positionFollowersOf(anchor: ToolPkgFloatingWindowInstance) {
        instances.values
            .filter { candidate ->
                candidate.spec.packageName == anchor.spec.packageName &&
                    candidate.spec.follow?.windowId.equals(anchor.spec.windowId, ignoreCase = true)
            }
            .forEach { follower ->
                positionWindowFromAnchor(follower)
            }
    }

    private fun persistPatch(key: WindowKey, patch: JSONObject, spec: FloatingWindowSpec) {
        val storageKey = storageKey(key)
        val editor = preferences.edit()
        val pressFeedbackPatch = if (patch.has("pressFeedback") && !patch.isNull("pressFeedback")) {
            patch.optJSONObject("pressFeedback")
                ?: error("pressFeedback must be a JSON object")
        } else {
            null
        }
        val releaseFeedbackPatch = if (patch.has("releaseFeedback") && !patch.isNull("releaseFeedback")) {
            patch.optJSONObject("releaseFeedback")
                ?: error("releaseFeedback must be a JSON object")
        } else {
            null
        }
        fun persistFeedback(prefix: String, feedback: ToolPkgFloatingWindowFeedback) {
            editor.putString("$prefixSoundResource:$storageKey", feedback.soundResource.orEmpty())
            editor.putString(
                "$prefixAnimation:$storageKey",
                feedback.animation?.let(::serializeFloatingWindowAnimation)?.toString().orEmpty()
            )
        }
        patch.optInt("widthDp", -1).takeIf { it > 0 }?.let { widthDp ->
            editor.putInt("widthDp:$storageKey", widthDp.coerceIn(MIN_FLOATING_WIDTH_DP, 1200))
        }
        patch.optInt("heightDp", -1).takeIf { it > 0 }?.let { heightDp ->
            editor.putInt("heightDp:$storageKey", heightDp.coerceIn(MIN_FLOATING_HEIGHT_DP, 1600))
        }
        if (patch.has("alpha")) {
            editor.putFloat(
                "alpha:$storageKey",
                patch.optDouble("alpha", 1.0).toFloat().coerceIn(MIN_FLOATING_ALPHA, MAX_FLOATING_ALPHA)
            )
        }
        if (patch.has("x")) editor.putInt("x:$storageKey", patch.optInt("x"))
        if (patch.has("y")) editor.putInt("y:$storageKey", patch.optInt("y"))
        if (patch.has("snapMode")) {
            editor.putString("snapMode:$storageKey", normalizeSnapMode(patch.optString("snapMode")))
        }
        if (patch.has("soundEnabled")) editor.putBoolean("soundEnabled:$storageKey", patch.optBoolean("soundEnabled"))
        if (patch.has("soundVolume")) {
            editor.putFloat(
                "soundVolume:$storageKey",
                patch.optDouble("soundVolume", 1.0).toFloat().coerceIn(0f, 1f)
            )
        }
        if (patch.has("pressFeedback")) {
            val feedback = if (pressFeedbackPatch == null) {
                ToolPkgFloatingWindowFeedback()
            } else {
                patchFloatingWindowFeedback(
                    readPersistedFloatingWindowFeedback(
                        preferences,
                        storageKey,
                        "pressFeedback",
                        spec.pressFeedback
                    ),
                    pressFeedbackPatch
                )
            }
            persistFeedback("pressFeedback", feedback)
        }
        if (patch.has("releaseFeedback")) {
            val feedback = if (releaseFeedbackPatch == null) {
                ToolPkgFloatingWindowFeedback()
            } else {
                patchFloatingWindowFeedback(
                    readPersistedFloatingWindowFeedback(
                        preferences,
                        storageKey,
                        "releaseFeedback",
                        spec.releaseFeedback
                    ),
                    releaseFeedbackPatch
                )
            }
            persistFeedback("releaseFeedback", feedback)
        }
        if (patch.has("routeArgs")) {
            editor.putString(
                "$TOOLPKG_FLOATING_ARGS_PREFIX$storageKey",
                patch.getJSONObject("routeArgs").toString()
            )
        }
        editor.apply()
    }

    private fun persistedState(
        key: WindowKey,
        spec: FloatingWindowSpec,
        status: String
    ): JSONObject {
        val storageKey = storageKey(key)
        val density = resources.displayMetrics.density
        val widthDp = preferences.getInt("widthDp:$storageKey", spec.widthDp)
        val heightDp = preferences.getInt("heightDp:$storageKey", spec.heightDp)
        return JSONObject()
            .put("schemaVersion", FLOATING_WINDOW_STATE_SCHEMA_VERSION)
            .put("windowId", spec.windowId)
            .put("contentRoute", spec.contentRouteId)
            .put("status", status)
            .put("widthDp", widthDp)
            .put("heightDp", heightDp)
            .put("draggable", spec.draggable)
            .put("resizable", spec.resizable)
            .put("alpha", preferences.getFloat("alpha:$storageKey", 1f).toDouble())
            .put("x", preferences.getInt("x:$storageKey", (resources.displayMetrics.widthPixels - dpToPx(widthDp, density)).coerceAtLeast(0)))
            .put("y", preferences.getInt("y:$storageKey", (resources.displayMetrics.heightPixels - dpToPx(heightDp, density)).coerceAtLeast(0)))
            .put("snapMode", normalizeSnapMode(preferences.getString("snapMode:$storageKey", spec.snapMode)))
            .put("follow", spec.follow?.let(::serializeFloatingWindowFollow) ?: JSONObject.NULL)
            .put("soundEnabled", preferences.getBoolean("soundEnabled:$storageKey", true))
            .put("soundVolume", preferences.getFloat("soundVolume:$storageKey", 1f).toDouble())
            .put(
                "pressFeedback",
                serializeFloatingWindowFeedback(
                    readPersistedFloatingWindowFeedback(
                        preferences,
                        storageKey,
                        "pressFeedback",
                        spec.pressFeedback
                    )
                )
            )
            .put(
                "releaseFeedback",
                serializeFloatingWindowFeedback(
                    readPersistedFloatingWindowFeedback(
                        preferences,
                        storageKey,
                        "releaseFeedback",
                        spec.releaseFeedback
                    )
                )
            )
            .put("updatedAtMs", System.currentTimeMillis().toString())
    }

    private fun dpToPx(value: Int, density: Float): Int = (value * density).toInt().coerceAtLeast(1)

    private fun persistVisibility(key: WindowKey, visible: Boolean, routeArgsJson: String) {
        val storageKey = storageKey(key)
        preferences.edit()
            .putBoolean("$VISIBLE_PREFIX$storageKey", visible)
            .putString("$TOOLPKG_FLOATING_ARGS_PREFIX$storageKey", routeArgsJson)
            .apply()
    }

    private fun restoreVisibleWindows() {
        if (restoreStarted) return
        restoreStarted = true
        serviceScope.launch(Dispatchers.IO) {
            val packageManager = PackageManager.getInstance(this@ToolPkgFloatingWindowService, AIToolHandler.getInstance(this@ToolPkgFloatingWindowService))
            try {
                packageManager.ensureInitialized()
                val registeredWindows = packageManager.getToolPkgFloatingWindows(this@ToolPkgFloatingWindowService)
                val visibleWindows = registeredWindows.filter { window ->
                    val key = WindowKey(window.containerPackageName, window.windowId)
                    val storageKey = storageKey(key)
                    preferences.getBoolean("$VISIBLE_PREFIX$storageKey", false)
                }
                val visibleByKey = visibleWindows.associateBy { window ->
                    WindowKey(window.containerPackageName, window.windowId)
                }
                fun followDepth(
                    window: PackageManager.ToolPkgFloatingWindow,
                    path: Set<WindowKey> = emptySet()
                ): Int {
                    val anchorWindowId = window.follow?.windowId ?: return 0
                    val key = WindowKey(window.containerPackageName, window.windowId)
                    require(key !in path) { "Visible floating window follow cycle detected" }
                    val anchorKey = WindowKey(window.containerPackageName, anchorWindowId)
                    val anchor = visibleByKey[anchorKey]
                        ?: error("Visible floating window anchor is missing: ${window.containerPackageName}:$anchorWindowId")
                    return 1 + followDepth(anchor, path + key)
                }
                val restored = visibleWindows.sortedBy { window -> followDepth(window) }.map { window ->
                    val key = WindowKey(window.containerPackageName, window.windowId)
                    val storageKey = storageKey(key)
                    JSONObject()
                        .put("operation", "show")
                        .put("packageName", window.containerPackageName)
                        .put("windowId", window.windowId)
                        .put("spec", serializeWindow(window))
                        .also { command ->
                            val routeArgs = preferences.getString("$TOOLPKG_FLOATING_ARGS_PREFIX$storageKey", "").orEmpty()
                            if (routeArgs.isNotBlank()) {
                                command.put("routeArgs", JSONObject(routeArgs))
                            }
                        }
                }
                withContext(Dispatchers.Main.immediate) {
                    restored.forEach { command ->
                        executeCommandOnMain(command)
                    }
                    if (restored.isEmpty() && instances.isEmpty()) {
                        stopSelf()
                    }
                }
            } catch (error: Exception) {
                AppLogger.e(TAG, "Failed to restore ToolPkg floating windows", error)
            }
        }
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
            .put("snapMode", window.snapMode)
            .put("follow", window.follow?.let { follow ->
                serializeFloatingWindowFollow(
                    ToolPkgFloatingWindowFollow(
                        windowId = follow.windowId,
                        placement = follow.placement,
                        offsetXDp = follow.offsetXDp,
                        offsetYDp = follow.offsetYDp
                    )
                )
            } ?: JSONObject.NULL)
            .put("pressFeedback", serializeFloatingWindowFeedback(window.pressFeedback))
            .put("releaseFeedback", serializeFloatingWindowFeedback(window.releaseFeedback))
            .put("refreshIntervalMs", window.refreshIntervalMs)
            .put("refreshFunction", window.refreshFunction ?: JSONObject.NULL)
            .put("refreshFunctionSource", window.refreshFunctionSource ?: JSONObject.NULL)
    }

    private fun storageKey(key: WindowKey): String = "${key.packageName}:${key.windowId}"

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID,
                    getString(R.string.toolpkg_floating_window_title),
                    NotificationManager.IMPORTANCE_LOW
                ).apply {
                    description = getString(R.string.toolpkg_floating_window_description)
                    setShowBadge(false)
                }
            )
        }
    }

    private fun createNotification() =
        NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_menu_view)
            .setContentTitle(getString(R.string.toolpkg_floating_window_title))
            .setContentText(getString(R.string.toolpkg_floating_window_running))
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .build()

    private data class WindowKey(val packageName: String, val windowId: String)
}

private data class FloatingWindowSpec(
    val packageName: String,
    val windowId: String,
    val contentRouteId: String,
    val title: String,
    val description: String,
    val widthDp: Int,
    val heightDp: Int,
    val draggable: Boolean,
    val resizable: Boolean,
    val snapMode: String,
    val follow: ToolPkgFloatingWindowFollow?,
    val pressFeedback: ToolPkgFloatingWindowFeedback,
    val releaseFeedback: ToolPkgFloatingWindowFeedback,
    val refreshIntervalMs: Long,
    val refreshFunction: String?,
    val refreshFunctionSource: String?
) {
    companion object {
        fun fromJson(json: JSONObject): FloatingWindowSpec {
            return FloatingWindowSpec(
                packageName = json.getString("packageName"),
                windowId = json.getString("windowId"),
                contentRouteId = json.getString("contentRouteId"),
                title = json.optString("title"),
                description = json.optString("description"),
                widthDp = json.optInt("widthDp", 320),
                heightDp = json.optInt("heightDp", 420),
                draggable = json.optBoolean("draggable", true),
                resizable = json.optBoolean("resizable", true),
                snapMode = normalizeSnapMode(json.optString("snapMode", DEFAULT_FLOATING_SNAP_MODE)),
                follow = parseFloatingWindowFollow(json.optJSONObject("follow")),
                pressFeedback = parseFloatingWindowFeedback(json.optJSONObject("pressFeedback")),
                releaseFeedback = parseFloatingWindowFeedback(json.optJSONObject("releaseFeedback")),
                refreshIntervalMs = json.optLong("refreshIntervalMs", 60_000L),
                refreshFunction = json.optString("refreshFunction").trim().ifBlank { null },
                refreshFunctionSource = json.optString("refreshFunctionSource").trim().ifBlank { null }
            )
        }
    }
}

private enum class FloatingWindowTouchResult {
    Pass,
    DragStarted,
    Dragging,
    Finished
}

private class ToolPkgFloatingWindowFrameLayout(
    context: Context,
    private val touchHandler: (MotionEvent) -> FloatingWindowTouchResult
) : FrameLayout(context) {
    override fun dispatchTouchEvent(event: MotionEvent): Boolean {
        val result = touchHandler(event)
        return when (result) {
            FloatingWindowTouchResult.DragStarted -> {
                cancelComposeGesture(event)
                true
            }
            FloatingWindowTouchResult.Dragging,
            FloatingWindowTouchResult.Finished -> true
            FloatingWindowTouchResult.Pass -> {
                val dispatched = super.dispatchTouchEvent(event)
                if (event.actionMasked == MotionEvent.ACTION_DOWN) true else dispatched
            }
        }
    }

    private fun cancelComposeGesture(event: MotionEvent) {
        val cancelEvent = MotionEvent.obtain(event)
        cancelEvent.action = MotionEvent.ACTION_CANCEL
        super.dispatchTouchEvent(cancelEvent)
        cancelEvent.recycle()
    }
}

private class ToolPkgFloatingWindowInstance(
    private val service: ToolPkgFloatingWindowService,
    val spec: FloatingWindowSpec,
    routeArgsJson: String
) {
    private val tag = "ToolPkgFloatingWindowInstance"
    private val packageManager =
        PackageManager.getInstance(service, AIToolHandler.getInstance(service))
    private val instanceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val lifecycleOwner = ServiceLifecycleOwner()
    private val renderMutex = Mutex()
    private val renderState = mutableStateOf<ToolPkgComposeDslRenderResult?>(null)
    private val renderError = mutableStateOf<String?>(null)
    private var routeArgsJsonValue = routeArgsJson
    private var composeView: ComposeView? = null
    private var windowView: View? = null
    private var isAdded = false
    private var disposed = false
    private var refreshJob: Job? = null
    private var jsEngine: JsEngine? = null
    private var executionContextKey: String = ""
    private var route: PackageManager.ToolPkgUiRoute? = null
    private var scriptScreenPath = ""
    private var script: String? = null
    private var firstRender = true
    private var lastTouchX = 0f
    private var lastTouchY = 0f
    private var originX = 0
    private var originY = 0
    private var originWidth = 0
    private var originHeight = 0
    private var dragging = false
    private var resizing = false
    private var layoutParams: WindowManager.LayoutParams? = null
    private var snapMode = spec.snapMode
    private var soundEnabled = true
    private var soundVolume = 1f
    private var pressFeedback = spec.pressFeedback
    private var releaseFeedback = spec.releaseFeedback
    private val soundPool = SoundPool.Builder()
        .setMaxStreams(4)
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()
        )
        .build()
        .also { pool ->
            pool.setOnLoadCompleteListener { _, sampleId, status ->
                Handler(Looper.getMainLooper()).post {
                    onSoundLoadComplete(sampleId, status)
                }
            }
        }
    private var pressSoundId = 0
    private var releaseSoundId = 0
    private var pressSoundLoaded = false
    private var releaseSoundLoaded = false
    private var pendingPressPlayback = false
    private var pendingReleasePlayback = false
    private val pendingSoundLoads = mutableMapOf<Int, Boolean>()
    private var soundLoadGeneration = 0
    // WindowManager updates cross the system-server boundary; one update per raw MOVE makes drag visibly stall.
    private val choreographer = Choreographer.getInstance()
    private val positionFrameCallback = Choreographer.FrameCallback {
        positionFrameScheduled = false
        applyPositionNow()
    }
    private var positionFrameScheduled = false
    private val touchSlop = ViewConfiguration.get(service).scaledTouchSlop

    fun show() {
        check(!disposed) { "Floating window instance is disposed" }
        lifecycleOwner.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
        lifecycleOwner.handleLifecycleEvent(Lifecycle.Event.ON_START)
        lifecycleOwner.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)
        val window = ToolPkgFloatingWindowFrameLayout(service) { event ->
            handleTouch(event)
        }
        window.setViewTreeLifecycleOwner(lifecycleOwner)
        window.setViewTreeViewModelStoreOwner(lifecycleOwner)
        window.setViewTreeSavedStateRegistryOwner(lifecycleOwner)
        val view = ComposeView(service).apply {
            setViewTreeLifecycleOwner(lifecycleOwner)
            setViewTreeViewModelStoreOwner(lifecycleOwner)
            setViewTreeSavedStateRegistryOwner(lifecycleOwner)
            setContent {
                MaterialTheme {
                    val result = renderState.value
                    val error = renderError.value
                    Box(modifier = Modifier.fillMaxSize()) {
                        when {
                            result != null -> {
                                RenderToolPkgComposeDslNode(
                                    node = result.tree,
                                    modifier = Modifier.fillMaxSize(),
                                    onAction = ::dispatchAction,
                                    onTextInputAction = { actionId, text ->
                                        dispatchAction(actionId, text)
                                    },
                                    onFlushTextInput = {
                                        requestRender()
                                    }
                                )
                            }
                            error != null -> Text(error)
                            else -> Text("Loading")
                        }
                    }
                }
            }
        }
        window.addView(
            view,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        )
        composeView = view
        windowView = window
        loadPersistedSettings()
        layoutParams = createLayoutParams()
        val params = layoutParams ?: error("Floating window layout params are unavailable")
        val windowManager = service.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        windowManager.addView(window, params)
        isAdded = true
        prepareSounds()
        requestRender()
        startRefreshLoop()
    }

    fun requestRender(forceLoad: Boolean = false) {
        if (disposed) return
        instanceScope.launch {
            renderInitialOrCurrent(forceLoad)
        }
    }

    fun updateRouteArgs(routeArgsJson: String) {
        routeArgsJsonValue = routeArgsJson
    }

    fun applyPatch(patch: JSONObject) {
        if (patch.has("routeArgs")) {
            routeArgsJsonValue = patch.getJSONObject("routeArgs").toString()
        }
        val params = layoutParams ?: return
        patch.optInt("widthDp", -1).takeIf { it > 0 }?.let { widthDp ->
            params.width = dpToPx(widthDp.coerceIn(MIN_FLOATING_WIDTH_DP, 1200))
        }
        patch.optInt("heightDp", -1).takeIf { it > 0 }?.let { heightDp ->
            params.height = dpToPx(heightDp.coerceIn(MIN_FLOATING_HEIGHT_DP, 1600))
        }
        if (patch.has("alpha")) {
            params.alpha = patch.optDouble("alpha", 1.0).toFloat()
                .coerceIn(MIN_FLOATING_ALPHA, MAX_FLOATING_ALPHA)
        }
        if (patch.has("x")) params.x = patch.optInt("x")
        if (patch.has("y")) params.y = patch.optInt("y")
        if (patch.has("snapMode")) snapMode = normalizeSnapMode(patch.optString("snapMode"))
        if (patch.has("soundEnabled")) {
            soundEnabled = patch.optBoolean("soundEnabled")
            if (!soundEnabled) {
                pendingPressPlayback = false
                pendingReleasePlayback = false
            }
        }
        if (patch.has("soundVolume")) {
            soundVolume = patch.optDouble("soundVolume", 1.0).toFloat().coerceIn(0f, 1f)
        }
        if (patch.has("pressFeedback")) {
            pressFeedback = if (patch.isNull("pressFeedback")) {
                ToolPkgFloatingWindowFeedback()
            } else {
                patchFloatingWindowFeedback(
                    current = pressFeedback,
                    json = patch.optJSONObject("pressFeedback")
                        ?: error("pressFeedback must be a JSON object")
                )
            }
        }
        if (patch.has("releaseFeedback")) {
            releaseFeedback = if (patch.isNull("releaseFeedback")) {
                ToolPkgFloatingWindowFeedback()
            } else {
                patchFloatingWindowFeedback(
                    current = releaseFeedback,
                    json = patch.optJSONObject("releaseFeedback")
                        ?: error("releaseFeedback must be a JSON object")
                )
            }
        }
        layoutParams = params
        windowView?.let { view ->
            (service.getSystemService(Context.WINDOW_SERVICE) as WindowManager)
                .updateViewLayout(view, params)
        }
        persistLayout(params)
        if (patch.has("pressFeedback") || patch.has("releaseFeedback")) {
            prepareSounds()
        }
        service.onWindowLayoutChanged(spec.packageName, spec.windowId)
        requestRender()
    }

    fun dispose() {
        if (disposed) return
        disposed = true
        refreshJob?.cancel()
        refreshJob = null
        soundLoadGeneration += 1
        if (positionFrameScheduled) {
            choreographer.removeFrameCallback(positionFrameCallback)
            positionFrameScheduled = false
        }
        windowView?.animate()?.cancel()
        pendingSoundLoads.clear()
        pendingPressPlayback = false
        pendingReleasePlayback = false
        instanceScope.cancel()
        val view = windowView
        if (view != null && isAdded) {
            try {
                (service.getSystemService(Context.WINDOW_SERVICE) as WindowManager).removeView(view)
            } catch (error: Exception) {
                AppLogger.e(tag, "Failed to remove ToolPkg floating window", error)
            }
        }
        composeView = null
        windowView = null
        soundPool.release()
        pressSoundId = 0
        releaseSoundId = 0
        isAdded = false
        lifecycleOwner.handleLifecycleEvent(Lifecycle.Event.ON_PAUSE)
        lifecycleOwner.handleLifecycleEvent(Lifecycle.Event.ON_STOP)
        lifecycleOwner.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
        jsEngine?.let { engine ->
            jsEngine = null
            Handler(Looper.getMainLooper()).postDelayed({
                packageManager.releaseToolPkgExecutionEngine(executionContextKey, engine)
            }, 120L)
        }
    }

    fun state(status: String): JSONObject {
        val params = layoutParams ?: createLayoutParams()
        val density = service.resources.displayMetrics.density
        return JSONObject()
            .put("schemaVersion", FLOATING_WINDOW_STATE_SCHEMA_VERSION)
            .put("windowId", spec.windowId)
            .put("contentRoute", spec.contentRouteId)
            .put("status", status)
            .put("widthDp", (params.width / density).toInt())
            .put("heightDp", (params.height / density).toInt())
            .put("draggable", spec.draggable)
            .put("resizable", spec.resizable)
            .put("alpha", params.alpha.toDouble())
            .put("x", params.x)
            .put("y", params.y)
            .put("snapMode", snapMode)
            .put("follow", spec.follow?.let(::serializeFloatingWindowFollow) ?: JSONObject.NULL)
            .put("soundEnabled", soundEnabled)
            .put("soundVolume", soundVolume.toDouble())
            .put("pressFeedback", serializeFloatingWindowFeedback(pressFeedback))
            .put("releaseFeedback", serializeFloatingWindowFeedback(releaseFeedback))
            .put("instanceId", executionContextKey)
            .put("updatedAtMs", System.currentTimeMillis().toString())
    }

    private fun dispatchAction(actionId: String, payload: Any?) {
        instanceScope.launch {
            renderMutex.withLock {
                renderAction(actionId, payload)
            }
        }
    }

    private suspend fun renderInitialOrCurrent(forceLoad: Boolean) {
        renderMutex.withLock {
            withContext(Dispatchers.IO) {
                packageManager.ensureInitialized()
            }
            val routeInfo = resolveRoute() ?: return
            val engine = resolveEngine()
            val options = buildRuntimeOptions(routeInfo, renderState.value)
            try {
                val raw = withContext(Dispatchers.IO) {
                    if (firstRender || forceLoad) {
                        firstRender = false
                        engine.executeComposeDslScript(
                            script = resolveScript(),
                            runtimeOptions = options
                        )
                    } else {
                        engine.rerenderComposeDslTree(runtimeOptions = options)
                    }
                }
                val parsed = ToolPkgComposeDslParser.parseRenderResult(raw)
                    ?: error("Invalid compose_dsl floating window result")
                val finalResult = dispatchInitialLoadIfNeeded(engine, options, parsed)
                withContext(Dispatchers.Main.immediate) {
                    renderError.value = null
                    renderState.value = finalResult
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                AppLogger.e(tag, "Floating window render failed", error)
                withContext(Dispatchers.Main.immediate) {
                    renderError.value = error.message ?: error.javaClass.simpleName
                }
            }
        }
    }

    private suspend fun renderAction(actionId: String, payload: Any?) {
        withContext(Dispatchers.IO) {
            packageManager.ensureInitialized()
        }
        val routeInfo = resolveRoute() ?: return
        val engine = resolveEngine()
        val options = buildRuntimeOptions(routeInfo, renderState.value)
        try {
            val raw = withContext(Dispatchers.IO) {
                engine.executeComposeDslAction(
                    actionId = actionId,
                    payload = payload,
                    runtimeOptions = options
                )
            }
            val parsed = ToolPkgComposeDslParser.parseRenderResult(raw)
                ?: error("Invalid compose_dsl floating window action result")
            withContext(Dispatchers.Main.immediate) {
                renderError.value = null
                renderState.value = parsed
            }
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            AppLogger.e(tag, "Floating window action failed: $actionId", error)
            withContext(Dispatchers.Main.immediate) {
                renderError.value = error.message ?: error.javaClass.simpleName
            }
        }
    }

    private suspend fun dispatchInitialLoadIfNeeded(
        engine: JsEngine,
        options: Map<String, Any?>,
        parsed: ToolPkgComposeDslRenderResult
    ): ToolPkgComposeDslRenderResult {
        val actionId = ToolPkgComposeDslParser.extractActionId(parsed.tree.props["onLoad"])
            ?: return parsed
        val raw = withContext(Dispatchers.IO) {
            engine.executeComposeDslAction(actionId = actionId, runtimeOptions = options)
        }
        return ToolPkgComposeDslParser.parseRenderResult(raw)
            ?: error("Invalid compose_dsl floating window onLoad result")
    }

    private fun resolveRoute(): PackageManager.ToolPkgUiRoute? {
        if (route != null) return route
        route = packageManager
            .getToolPkgUiRoutes(resolveContext = service)
            .firstOrNull { candidate ->
                candidate.containerPackageName == spec.packageName &&
                    candidate.routeId.equals(spec.contentRouteId, ignoreCase = true)
            }
        val resolved = route ?: error("Floating window route not found: ${spec.contentRouteId}")
        scriptScreenPath = packageManager
            .getToolPkgComposeDslScreenPath(spec.packageName, resolved.uiModuleId)
            .orEmpty()
        return resolved
    }

    private fun resolveEngine(): JsEngine {
        val existing = jsEngine
        if (existing != null) return existing
        check(route != null) { "Floating window route is not resolved" }
        executionContextKey =
            "toolpkg_floating:${spec.packageName}:${spec.windowId}:${UUID.randomUUID()}"
        return packageManager
            .acquireToolPkgExecutionEngine(executionContextKey, spec.packageName)
            .also { jsEngine = it }
    }

    private fun resolveScript(): String {
        if (script == null) {
            val resolvedRoute = route ?: error("Floating window route is not resolved")
            script = packageManager.getToolPkgComposeDslScript(
                containerPackageName = spec.packageName,
                uiModuleId = resolvedRoute.uiModuleId
            ) ?: error("Floating window compose_dsl script is unavailable")
        }
        return script.orEmpty()
    }

    private fun buildRuntimeOptions(
        routeInfo: PackageManager.ToolPkgUiRoute,
        previous: ToolPkgComposeDslRenderResult?
    ): Map<String, Any?> {
        return mapOf(
            "packageName" to spec.packageName,
            "toolPkgId" to spec.packageName,
            "uiModuleId" to routeInfo.uiModuleId,
            "__operit_ui_package_name" to spec.packageName,
            "__operit_ui_toolpkg_id" to spec.packageName,
            "__operit_ui_module_id" to routeInfo.uiModuleId,
            "__operit_toolpkg_runtime_kind" to "ui",
            "routeInstanceId" to executionContextKey,
            "__operit_route_instance_id" to executionContextKey,
            "executionContextKey" to executionContextKey,
            "__operit_compose_execution_context_key" to executionContextKey,
            "__operit_script_screen" to scriptScreenPath,
            "moduleSpec" to routeInfo.moduleSpec + mapOf("routeArgsJson" to routeArgsJsonValue),
            "state" to (previous?.state ?: emptyMap<String, Any?>()),
            "memo" to (previous?.memo ?: emptyMap<String, Any?>())
        )
    }

    private fun startRefreshLoop() {
        if (spec.refreshIntervalMs <= 0L || spec.refreshFunction.isNullOrBlank()) return
        refreshJob = instanceScope.launch(Dispatchers.IO) {
            while (isActive) {
                delay(spec.refreshIntervalMs)
                if (!isActive || disposed) return@launch
                try {
                    val result = packageManager.runToolPkgMainHook(
                        containerPackageName = spec.packageName,
                        functionName = spec.refreshFunction.orEmpty(),
                        event = "toolpkg_floating_window_refresh",
                        eventName = "refresh",
                        pluginId = spec.windowId,
                        inlineFunctionSource = spec.refreshFunctionSource,
                        eventPayload = mapOf(
                            "windowId" to spec.windowId,
                            "instanceId" to executionContextKey,
                            "routeArgsJson" to routeArgsJsonValue
                        ),
                        executionContextKey = "toolpkg_floating_refresh:${spec.packageName}:${spec.windowId}",
                        runtimeKind = "main",
                        dispatchIntermediateOnMain = false
                    )
                    result.onFailure { error ->
                        AppLogger.e(tag, "Floating window refresh function failed", error)
                    }
                    withContext(Dispatchers.Main.immediate) {
                        requestRender(forceLoad = true)
                    }
                } catch (error: CancellationException) {
                    throw error
                } catch (error: Exception) {
                    AppLogger.e(tag, "Floating window refresh loop failed", error)
                }
            }
        }
    }

    private fun createLayoutParams(): WindowManager.LayoutParams {
        val prefs = service.getSharedPreferences(TOOLPKG_FLOATING_PREFS_NAME, Context.MODE_PRIVATE)
        val key = "${spec.packageName}:${spec.windowId}"
        val width = dpToPx(prefs.getInt("widthDp:$key", spec.widthDp))
        val height = dpToPx(prefs.getInt("heightDp:$key", spec.heightDp))
        val displayMetrics = service.resources.displayMetrics
        return WindowManager.LayoutParams(
            width,
            height,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = prefs.getInt("x:$key", (displayMetrics.widthPixels - width).coerceAtLeast(0))
            y = prefs.getInt("y:$key", (displayMetrics.heightPixels - height).coerceAtLeast(0))
            alpha = prefs.getFloat("alpha:$key", 1f).coerceIn(MIN_FLOATING_ALPHA, MAX_FLOATING_ALPHA)
        }
    }

    private fun loadPersistedSettings() {
        val prefs = service.getSharedPreferences(TOOLPKG_FLOATING_PREFS_NAME, Context.MODE_PRIVATE)
        val key = "${spec.packageName}:${spec.windowId}"
        snapMode = normalizeSnapMode(prefs.getString("snapMode:$key", spec.snapMode))
        soundEnabled = prefs.getBoolean("soundEnabled:$key", true)
        soundVolume = prefs.getFloat("soundVolume:$key", 1f).coerceIn(0f, 1f)
        pressFeedback = readPersistedFloatingWindowFeedback(prefs, key, "pressFeedback", spec.pressFeedback)
        releaseFeedback = readPersistedFloatingWindowFeedback(prefs, key, "releaseFeedback", spec.releaseFeedback)
    }

    private fun dpToPx(value: Int): Int {
        return (value * service.resources.displayMetrics.density).toInt().coerceAtLeast(1)
    }

    @Suppress("UNUSED_PARAMETER")
    private fun handleTouch(event: MotionEvent): FloatingWindowTouchResult {
        if (disposed) return FloatingWindowTouchResult.Pass
        val params = layoutParams ?: return FloatingWindowTouchResult.Pass
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                playPressFeedback()
                if (!spec.draggable) return FloatingWindowTouchResult.Pass
                lastTouchX = event.rawX
                lastTouchY = event.rawY
                originX = params.x
                originY = params.y
                originWidth = params.width
                originHeight = params.height
                dragging = false
                resizing =
                    spec.resizable &&
                        event.x >= viewWidth() - dpToPx(36) &&
                        event.y >= viewHeight() - dpToPx(36)
                return FloatingWindowTouchResult.Pass
            }
            MotionEvent.ACTION_MOVE -> {
                if (!spec.draggable) return FloatingWindowTouchResult.Pass
                val dx = event.rawX - lastTouchX
                val dy = event.rawY - lastTouchY
                if (resizing) {
                    val wasDragging = dragging
                    params.width = (originWidth + dx.toInt()).coerceIn(dpToPx(MIN_FLOATING_WIDTH_DP), dpToPx(1200))
                    params.height = (originHeight + dy.toInt()).coerceIn(dpToPx(MIN_FLOATING_HEIGHT_DP), dpToPx(1600))
                    updatePosition(params)
                    dragging = true
                    return if (wasDragging) {
                        FloatingWindowTouchResult.Dragging
                    } else {
                        FloatingWindowTouchResult.DragStarted
                    }
                }
                val wasDragging = dragging
                if (!wasDragging && dx * dx + dy * dy < touchSlop * touchSlop) {
                    return FloatingWindowTouchResult.Pass
                }
                dragging = true
                val displayMetrics = service.resources.displayMetrics
                params.x = (originX + dx.toInt())
                    .coerceIn(0, (displayMetrics.widthPixels - params.width).coerceAtLeast(0))
                params.y = (originY + dy.toInt())
                    .coerceIn(0, (displayMetrics.heightPixels - params.height).coerceAtLeast(0))
                updatePosition(params)
                return if (wasDragging) {
                    FloatingWindowTouchResult.Dragging
                } else {
                    FloatingWindowTouchResult.DragStarted
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                playReleaseFeedback()
                if (!spec.draggable) return FloatingWindowTouchResult.Pass
                if (resizing) {
                    flushPositionUpdate()
                    persistLayout(params)
                    resizing = false
                    service.onWindowLayoutChanged(spec.packageName, spec.windowId)
                    return FloatingWindowTouchResult.Finished
                }
                if (!dragging) return FloatingWindowTouchResult.Pass
                settlePosition(params)
                dragging = false
                return FloatingWindowTouchResult.Finished
            }
        }
        return FloatingWindowTouchResult.Pass
    }

    private fun updatePosition(params: WindowManager.LayoutParams) {
        layoutParams = params
        if (!positionFrameScheduled) {
            positionFrameScheduled = true
            choreographer.postFrameCallback(positionFrameCallback)
        }
    }

    private fun flushPositionUpdate() {
        if (positionFrameScheduled) {
            choreographer.removeFrameCallback(positionFrameCallback)
            positionFrameScheduled = false
        }
        applyPositionNow()
    }

    private fun applyPositionNow() {
        if (disposed || !isAdded) return
        val view = windowView ?: return
        val params = layoutParams ?: return
        try {
            (service.getSystemService(Context.WINDOW_SERVICE) as WindowManager)
                .updateViewLayout(view, params)
            service.onWindowPositionChanged(spec.packageName, spec.windowId)
        } catch (error: Exception) {
            AppLogger.e(tag, "Failed to update floating window position", error)
        }
    }

    fun currentLayoutParams(): WindowManager.LayoutParams? = layoutParams

    fun applyFollowPosition(x: Int, y: Int) {
        val params = layoutParams ?: return
        params.x = x
        params.y = y
        layoutParams = params
        applyPositionNow()
    }

    private fun settlePosition(params: WindowManager.LayoutParams) {
        if (snapMode == "none") {
            flushPositionUpdate()
            persistLayout(params)
            return
        }
        val screenWidth = service.resources.displayMetrics.widthPixels
        val screenHeight = service.resources.displayMetrics.heightPixels
        val centerX = params.x + params.width / 2
        val centerY = params.y + params.height / 2
        params.x = when {
            centerX < screenWidth / 4 -> 0
            centerX > screenWidth * 3 / 4 -> (screenWidth - params.width).coerceAtLeast(0)
            else -> params.x.coerceIn(0, (screenWidth - params.width).coerceAtLeast(0))
        }
        params.y = when {
            centerY < screenHeight / 4 -> 0
            centerY > screenHeight * 3 / 4 -> (screenHeight - params.height).coerceAtLeast(0)
            else -> params.y.coerceIn(0, (screenHeight - params.height).coerceAtLeast(0))
        }
        updatePosition(params)
        flushPositionUpdate()
        val key = "${spec.packageName}:${spec.windowId}"
        service.getSharedPreferences(TOOLPKG_FLOATING_PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putInt("x:$key", params.x)
            .putInt("y:$key", params.y)
            .apply()
    }

    private fun persistLayout(params: WindowManager.LayoutParams) {
        val density = service.resources.displayMetrics.density
        val key = "${spec.packageName}:${spec.windowId}"
        service.getSharedPreferences(TOOLPKG_FLOATING_PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putInt("widthDp:$key", (params.width / density).toInt())
            .putInt("heightDp:$key", (params.height / density).toInt())
            .putInt("x:$key", params.x)
            .putInt("y:$key", params.y)
            .putFloat("alpha:$key", params.alpha.coerceIn(MIN_FLOATING_ALPHA, MAX_FLOATING_ALPHA))
            .putString("snapMode:$key", snapMode)
            .putBoolean("soundEnabled:$key", soundEnabled)
            .putFloat("soundVolume:$key", soundVolume)
            .putString("pressFeedbackSoundResource:$key", pressFeedback.soundResource.orEmpty())
            .putString(
                "pressFeedbackAnimation:$key",
                pressFeedback.animation?.let(::serializeFloatingWindowAnimation)?.toString().orEmpty()
            )
            .putString("releaseFeedbackSoundResource:$key", releaseFeedback.soundResource.orEmpty())
            .putString(
                "releaseFeedbackAnimation:$key",
                releaseFeedback.animation?.let(::serializeFloatingWindowAnimation)?.toString().orEmpty()
            )
            .putString("$TOOLPKG_FLOATING_ARGS_PREFIX$key", routeArgsJsonValue)
            .apply()
    }

    private fun playPressFeedback() {
        animateFeedback(pressFeedback.animation)
        playSound(pressFeedback.soundResource, press = true)
    }

    private fun playReleaseFeedback() {
        animateFeedback(releaseFeedback.animation)
        playSound(releaseFeedback.soundResource, press = false)
    }

    private fun animateFeedback(animation: ToolPkgFloatingWindowAnimation?) {
        val view = windowView ?: return
        if (animation == null) {
            view.animate().cancel()
            view.scaleX = 1f
            view.scaleY = 1f
            view.alpha = 1f
            view.translationX = 0f
            view.translationY = 0f
            return
        }
        view.pivotX = view.width * animation.pivotX
        view.pivotY = view.height * animation.pivotY
        view.animate()
            .cancel()
        view.animate()
            .scaleX(animation.scaleX)
            .scaleY(animation.scaleY)
            .alpha(animation.alpha)
            .translationX(animation.translationXDp * service.resources.displayMetrics.density)
            .translationY(animation.translationYDp * service.resources.displayMetrics.density)
            .setDuration(animation.durationMs)
            .setInterpolator(animationInterpolator(animation.easing))
            .start()
    }

    private fun animationInterpolator(easing: String): Interpolator {
        return when (easing) {
            "linear" -> LinearInterpolator()
            "accelerate" -> AccelerateInterpolator()
            "decelerate" -> DecelerateInterpolator()
            "accelerateDecelerate" -> AccelerateDecelerateInterpolator()
            "overshoot" -> OvershootInterpolator(1.15f)
            else -> error("Unsupported floating window animation easing: $easing")
        }
    }

    private fun prepareSounds() {
        // Copy and decode before touch playback; pending taps bridge the asynchronous SoundPool load.
        val generation = ++soundLoadGeneration
        if (pressSoundId != 0) soundPool.unload(pressSoundId)
        if (releaseSoundId != 0) soundPool.unload(releaseSoundId)
        pressSoundId = 0
        releaseSoundId = 0
        pressSoundLoaded = false
        releaseSoundLoaded = false
        pendingPressPlayback = false
        pendingReleasePlayback = false
        pendingSoundLoads.clear()
        val requested = listOf(
            true to pressFeedback.soundResource,
            false to releaseFeedback.soundResource
        )
        instanceScope.launch(Dispatchers.IO) {
            try {
                val files = requested.mapNotNull { (press, resourceKey) ->
                    if (resourceKey.isNullOrBlank()) return@mapNotNull null
                    val outputDir = File(service.cacheDir, "toolpkg-floating-audio")
                    if (!outputDir.exists()) outputDir.mkdirs()
                    val safeName = resourceKey.replace(Regex("[^A-Za-z0-9_.-]"), "_")
                    val outputFile = File(outputDir, "${spec.packageName}_$safeName")
                    if (!outputFile.exists() && !packageManager.copyToolPkgResourceToFile(spec.packageName, resourceKey, outputFile)) {
                        AppLogger.e(tag, "Floating window sound resource unavailable: $resourceKey")
                        return@mapNotNull null
                    }
                    press to outputFile
                }
                withContext(Dispatchers.Main.immediate) {
                    if (disposed || generation != soundLoadGeneration) return@withContext
                    files.forEach { (press, file) ->
                        val soundId = soundPool.load(file.absolutePath, 1)
                        if (soundId == 0) {
                            AppLogger.e(tag, "Failed to queue floating window sound load: ${file.name}")
                        } else {
                            pendingSoundLoads[soundId] = press
                            if (press) {
                                pressSoundId = soundId
                            } else {
                                releaseSoundId = soundId
                            }
                        }
                    }
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                AppLogger.e(tag, "Failed to prepare floating window sounds", error)
            }
        }
    }

    private fun onSoundLoadComplete(soundId: Int, status: Int) {
        if (disposed) return
        val press = pendingSoundLoads.remove(soundId) ?: return
        if (status != 0) {
            AppLogger.e(tag, "Floating window sound load failed: sampleId=$soundId status=$status")
            if (press) {
                pendingPressPlayback = false
            } else {
                pendingReleasePlayback = false
            }
            return
        }
        if (press) {
            pressSoundLoaded = true
            if (pendingPressPlayback) {
                pendingPressPlayback = false
                playLoadedSound(soundId, press = true)
            }
        } else {
            releaseSoundLoaded = true
            if (pendingReleasePlayback) {
                pendingReleasePlayback = false
                playLoadedSound(soundId, press = false)
            }
        }
    }

    private fun playSound(resourceKey: String?, press: Boolean) {
        if (!soundEnabled || soundVolume <= 0f || resourceKey.isNullOrBlank()) return
        val soundId = if (press) pressSoundId else releaseSoundId
        val loaded = if (press) pressSoundLoaded else releaseSoundLoaded
        if (soundId == 0 || !loaded) {
            if (press) {
                pendingPressPlayback = true
            } else {
                pendingReleasePlayback = true
            }
            return
        }
        playLoadedSound(soundId, press)
    }

    private fun playLoadedSound(soundId: Int, press: Boolean) {
        if (!soundEnabled || soundVolume <= 0f) return
        val streamId = soundPool.play(soundId, soundVolume, soundVolume, 1, 0, 1f)
        if (streamId == 0) {
            AppLogger.e(tag, "Floating window sound playback failed: sampleId=$soundId press=$press")
        }
    }

    private fun viewWidth(): Int = windowView?.width ?: layoutParams?.width ?: 0

    private fun viewHeight(): Int = windowView?.height ?: layoutParams?.height ?: 0
}
