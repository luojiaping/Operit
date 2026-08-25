package com.ai.assistance.operit.services.floating

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.media.MediaPlayer
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
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

private const val TOOLPKG_FLOATING_PREFS_NAME = "toolpkg_floating_windows_v2"
private const val DEFAULT_FLOATING_SNAP_MODE = "quarter"
private const val MIN_FLOATING_ALPHA = 0.2f
private const val MAX_FLOATING_ALPHA = 1f

private fun normalizeSnapMode(value: String?): String {
    return if (value.equals("none", ignoreCase = true)) "none" else DEFAULT_FLOATING_SNAP_MODE
}

class ToolPkgFloatingWindowService : Service() {
    companion object {
        private const val TAG = "ToolPkgFloatingWindowService"
        private const val NOTIFICATION_ID = 1017
        private const val CHANNEL_ID = "toolpkg_floating_window"
        private const val EXTRA_COMMAND_ID = "toolpkg_command_id"
        private const val EXTRA_COMMAND_JSON = "toolpkg_command_json"
        private const val VISIBLE_PREFIX = "visible:"
        private const val ARGS_PREFIX = "args:"

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
                .put("schemaVersion", 2)
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
                .put("soundEnabled", prefs.getBoolean("soundEnabled:$key", true))
                .put("soundVolume", prefs.getFloat("soundVolume:$key", 1f).coerceIn(0f, 1f).toDouble())
                .put("pressSoundResource", prefs.getString("pressSoundResource:$key", spec.pressSoundResource) ?: JSONObject.NULL)
                .put("releaseSoundResource", prefs.getString("releaseSoundResource:$key", spec.releaseSoundResource) ?: JSONObject.NULL)
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
        val routeArgs = command.optJSONObject("routeArgs")?.toString().orEmpty()
        val persistedSnapMode = preferences.getString("snapMode:${storageKey(key)}", null)
        val effectiveSpec =
            if (persistedSnapMode.isNullOrBlank()) {
                spec
            } else {
                spec.copy(snapMode = normalizeSnapMode(persistedSnapMode))
            }
        val existing = instances[key]
        if (existing != null) {
            existing.updateRouteArgs(routeArgs)
            persistVisibility(key, true, routeArgs)
            existing.requestRender()
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
        return removed?.state("hidden")
            ?: persistedState(
                key,
                specJson?.let { FloatingWindowSpec.fromJson(it) }
                    ?: FloatingWindowSpec(
                        packageName = key.packageName,
                        windowId = key.windowId,
                        contentRouteId = "",
                        title = "",
                        description = "",
                        widthDp = 320,
                        heightDp = 420,
                        draggable = true,
                        resizable = true,
                        snapMode = DEFAULT_FLOATING_SNAP_MODE,
                        pressSoundResource = null,
                        releaseSoundResource = null,
                        refreshIntervalMs = 60_000L,
                        refreshFunction = null,
                        refreshFunctionSource = null
                    ),
                "hidden"
            )
    }

    private fun updateWindow(key: WindowKey, patch: JSONObject, specJson: JSONObject): JSONObject {
        val window = instances[key]
        if (window == null) {
            val spec = FloatingWindowSpec.fromJson(specJson)
            persistPatch(key, patch)
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

    private fun persistPatch(key: WindowKey, patch: JSONObject) {
        val storageKey = storageKey(key)
        val editor = preferences.edit()
        patch.optInt("widthDp", -1).takeIf { it > 0 }?.let { widthDp ->
            editor.putInt("widthDp:$storageKey", widthDp.coerceIn(120, 1200))
        }
        patch.optInt("heightDp", -1).takeIf { it > 0 }?.let { heightDp ->
            editor.putInt("heightDp:$storageKey", heightDp.coerceIn(120, 1600))
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
        if (patch.has("pressSoundResource")) {
            editor.putString("pressSoundResource:$storageKey", patch.optString("pressSoundResource").trim())
        }
        if (patch.has("releaseSoundResource")) {
            editor.putString("releaseSoundResource:$storageKey", patch.optString("releaseSoundResource").trim())
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
            .put("schemaVersion", 2)
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
            .put("soundEnabled", preferences.getBoolean("soundEnabled:$storageKey", true))
            .put("soundVolume", preferences.getFloat("soundVolume:$storageKey", 1f).toDouble())
            .put("pressSoundResource", preferences.getString("pressSoundResource:$storageKey", spec.pressSoundResource) ?: JSONObject.NULL)
            .put("releaseSoundResource", preferences.getString("releaseSoundResource:$storageKey", spec.releaseSoundResource) ?: JSONObject.NULL)
            .put("updatedAtMs", System.currentTimeMillis().toString())
    }

    private fun dpToPx(value: Int, density: Float): Int = (value * density).toInt().coerceAtLeast(1)

    private fun persistVisibility(key: WindowKey, visible: Boolean, routeArgsJson: String) {
        val storageKey = storageKey(key)
        preferences.edit()
            .putBoolean("$VISIBLE_PREFIX$storageKey", visible)
            .putString("$ARGS_PREFIX$storageKey", routeArgsJson)
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
                val restored = registeredWindows.mapNotNull { window ->
                    val key = WindowKey(window.containerPackageName, window.windowId)
                    val storageKey = storageKey(key)
                    if (!preferences.getBoolean("$VISIBLE_PREFIX$storageKey", false)) {
                        null
                    } else {
                        val command =
                            JSONObject()
                                .put("operation", "show")
                                .put("packageName", window.containerPackageName)
                                .put("windowId", window.windowId)
                                .put("spec", serializeWindow(window))
                        val routeArgs = preferences.getString("$ARGS_PREFIX$storageKey", "").orEmpty()
                        if (routeArgs.isNotBlank()) {
                            command.put("routeArgs", JSONObject(routeArgs))
                        }
                        command
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
            .put("pressSoundResource", window.pressSoundResource ?: JSONObject.NULL)
            .put("releaseSoundResource", window.releaseSoundResource ?: JSONObject.NULL)
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
    val pressSoundResource: String?,
    val releaseSoundResource: String?,
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
                pressSoundResource = json.optString("pressSoundResource").trim().ifBlank { null },
                releaseSoundResource = json.optString("releaseSoundResource").trim().ifBlank { null },
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
    private val spec: FloatingWindowSpec,
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
    private var pressSoundResource = spec.pressSoundResource
    private var releaseSoundResource = spec.releaseSoundResource
    private var pressPlayer: MediaPlayer? = null
    private var releasePlayer: MediaPlayer? = null

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
            routeArgsJsonValue = patch.optJSONObject("routeArgs")?.toString().orEmpty()
        }
        val params = layoutParams ?: return
        patch.optInt("widthDp", -1).takeIf { it > 0 }?.let { widthDp ->
            params.width = dpToPx(widthDp.coerceIn(120, 1200))
        }
        patch.optInt("heightDp", -1).takeIf { it > 0 }?.let { heightDp ->
            params.height = dpToPx(heightDp.coerceIn(120, 1600))
        }
        if (patch.has("alpha")) {
            params.alpha = patch.optDouble("alpha", 1.0).toFloat()
                .coerceIn(MIN_FLOATING_ALPHA, MAX_FLOATING_ALPHA)
        }
        if (patch.has("x")) params.x = patch.optInt("x")
        if (patch.has("y")) params.y = patch.optInt("y")
        if (patch.has("snapMode")) snapMode = normalizeSnapMode(patch.optString("snapMode"))
        if (patch.has("soundEnabled")) soundEnabled = patch.optBoolean("soundEnabled")
        if (patch.has("soundVolume")) {
            soundVolume = patch.optDouble("soundVolume", 1.0).toFloat().coerceIn(0f, 1f)
        }
        if (patch.has("pressSoundResource")) {
            pressSoundResource = if (patch.isNull("pressSoundResource")) {
                null
            } else {
                patch.optString("pressSoundResource").trim().ifBlank { null }
            }
        }
        if (patch.has("releaseSoundResource")) {
            releaseSoundResource = if (patch.isNull("releaseSoundResource")) {
                null
            } else {
                patch.optString("releaseSoundResource").trim().ifBlank { null }
            }
        }
        layoutParams = params
        windowView?.let { view ->
            (service.getSystemService(Context.WINDOW_SERVICE) as WindowManager)
                .updateViewLayout(view, params)
        }
        persistLayout(params)
        requestRender()
    }

    fun dispose() {
        if (disposed) return
        disposed = true
        refreshJob?.cancel()
        refreshJob = null
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
        releasePlayers()
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
            .put("schemaVersion", 2)
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
            .put("soundEnabled", soundEnabled)
            .put("soundVolume", soundVolume.toDouble())
            .put("pressSoundResource", pressSoundResource ?: JSONObject.NULL)
            .put("releaseSoundResource", releaseSoundResource ?: JSONObject.NULL)
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
        pressSoundResource = prefs.getString("pressSoundResource:$key", spec.pressSoundResource)
        releaseSoundResource = prefs.getString("releaseSoundResource:$key", spec.releaseSoundResource)
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
                    params.width = (originWidth + dx.toInt()).coerceIn(dpToPx(120), dpToPx(1200))
                    params.height = (originHeight + dy.toInt()).coerceIn(dpToPx(120), dpToPx(1600))
                    updatePosition(params)
                    dragging = true
                    return if (wasDragging) {
                        FloatingWindowTouchResult.Dragging
                    } else {
                        FloatingWindowTouchResult.DragStarted
                    }
                }
                val wasDragging = dragging
                if (!wasDragging && dx * dx + dy * dy < 64f) {
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
                    persistLayout(params)
                    resizing = false
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
        windowView?.let { view ->
            try {
                (service.getSystemService(Context.WINDOW_SERVICE) as WindowManager)
                    .updateViewLayout(view, params)
            } catch (error: Exception) {
                AppLogger.e(tag, "Failed to update floating window position", error)
            }
        }
    }

    private fun settlePosition(params: WindowManager.LayoutParams) {
        if (snapMode == "none") {
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
            .putString("pressSoundResource:$key", pressSoundResource)
            .putString("releaseSoundResource:$key", releaseSoundResource)
            .apply()
    }

    private fun playPressFeedback() {
        animatePressState(pressed = true)
        playSound(pressSoundResource, press = true)
    }

    private fun playReleaseFeedback() {
        animatePressState(pressed = false)
        playSound(releaseSoundResource, press = false)
    }

    private fun animatePressState(pressed: Boolean) {
        val view = windowView ?: return
        view.pivotX = view.width / 2f
        view.pivotY = view.height.toFloat()
        view.animate()
            .cancel()
        view.animate()
            .scaleX(if (pressed) 1.05f else 1f)
            .scaleY(if (pressed) 0.88f else 1f)
            .setDuration(if (pressed) 90L else 220L)
            .setInterpolator(OvershootInterpolator(1.15f))
            .start()
    }

    private fun playSound(resourceKey: String?, press: Boolean) {
        if (!soundEnabled || soundVolume <= 0f || resourceKey.isNullOrBlank()) return
        val outputDir = File(service.cacheDir, "toolpkg-floating-audio")
        if (!outputDir.exists()) outputDir.mkdirs()
        val safeName = resourceKey.replace(Regex("[^A-Za-z0-9_.-]"), "_")
        val outputFile = File(outputDir, "${spec.packageName}_$safeName")
        if (!outputFile.exists() && !packageManager.copyToolPkgResourceToFile(spec.packageName, resourceKey, outputFile)) {
            AppLogger.e(tag, "Floating window sound resource unavailable: $resourceKey")
            return
        }
        val current = if (press) pressPlayer else releasePlayer
        current?.release()
        val player = MediaPlayer()
        try {
            player.setDataSource(outputFile.absolutePath)
            player.setVolume(soundVolume, soundVolume)
            player.setOnCompletionListener { completed ->
                completed.release()
                if (press) pressPlayer = null else releasePlayer = null
            }
            player.setOnErrorListener { failed, _, _ ->
                failed.release()
                if (press) pressPlayer = null else releasePlayer = null
                true
            }
            player.prepare()
            player.start()
            if (press) pressPlayer = player else releasePlayer = player
        } catch (error: Exception) {
            player.release()
            AppLogger.e(tag, "Failed to play floating window sound: $resourceKey", error)
        }
    }

    private fun releasePlayers() {
        pressPlayer?.release()
        releasePlayer?.release()
        pressPlayer = null
        releasePlayer = null
    }

    private fun viewWidth(): Int = windowView?.width ?: layoutParams?.width ?: 0

    private fun viewHeight(): Int = windowView?.height ?: layoutParams?.height ?: 0
}
