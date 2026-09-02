package com.ai.assistance.operit.ui.features.settings.screens

import android.content.Context
import android.graphics.Color as AndroidColor
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Colorize
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.ai.assistance.operit.R
import com.ai.assistance.operit.data.preferences.GlobalChatStyle
import com.ai.assistance.operit.data.preferences.GlobalInputStyle
import com.ai.assistance.operit.data.preferences.GlobalPresentationManager
import com.ai.assistance.operit.data.preferences.GlobalPresentationSnapshot
import com.ai.assistance.operit.data.preferences.GlobalThemeMode
import com.ai.assistance.operit.data.theme.packages.PublishedThemeInstallationV2
import com.ai.assistance.operit.data.theme.packages.ThemeComponentCatalogV2
import com.ai.assistance.operit.data.theme.packages.ThemeInstanceV2
import com.ai.assistance.operit.data.theme.packages.ThemePackageDefaultV2
import com.ai.assistance.operit.data.theme.packages.ThemePackageInstallerV2
import com.ai.assistance.operit.data.theme.packages.ThemePackageRuntimeLinkerV2
import com.ai.assistance.operit.data.theme.packages.ThemePackageSelectionRepositoryV2
import com.ai.assistance.operit.data.theme.packages.ThemePackageCoordinateV2
import com.ai.assistance.operit.data.theme.packages.ThemeParameterControlV2
import com.ai.assistance.operit.data.theme.packages.ThemeParameterDefinitionV2
import com.ai.assistance.operit.data.theme.packages.ThemeParameterSectionV2
import com.ai.assistance.operit.data.theme.packages.ThemeParameterValueV2
import com.ai.assistance.operit.data.theme.packages.ThemeRuntimeRepositoryV2
import com.ai.assistance.operit.ui.theme.ThemeComponentStateV2
import com.ai.assistance.operit.ui.theme.ThemeComponentSurfaceV2
import com.ai.assistance.operit.util.AppLogger
import com.ai.assistance.operit.util.ImageBitmapLimiter
import java.io.ByteArrayOutputStream
import java.util.Locale
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val THEME_RESOURCE_MAX_BYTES = 48 * 1024 * 1024
private const val TAG = "ThemePackages"

@Composable
fun ThemePackagesScreen(
    onGoBack: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    val installer = remember(context) { ThemePackageInstallerV2.getInstance(context) }
    val selectionRepository = remember(context) { ThemePackageSelectionRepositoryV2.getInstance(context) }
    val globalPresentation = remember(context) { GlobalPresentationManager.getInstance(context) }
    val activeInstance by selectionRepository.selectionFlow.collectAsState(
        initial = ThemeInstanceV2.defaultBundled(),
    )
    val presentation by globalPresentation.snapshotFlow.collectAsState(
        initial = GlobalPresentationSnapshot.default(),
    )
    var installed by remember { mutableStateOf<List<PublishedThemeInstallationV2>>(emptyList()) }
    var busy by remember { mutableStateOf(false) }
    var showThemePicker by remember { mutableStateOf(false) }
    var pendingResourceParameterId by rememberSaveable { mutableStateOf<String?>(null) }
    var pendingResourceCoordinateKey by rememberSaveable { mutableStateOf<String?>(null) }
    var colorDialogDefinition by remember { mutableStateOf<ThemeParameterDefinitionV2?>(null) }

    fun reload() {
        scope.launch(Dispatchers.IO) {
            val catalog = installer.catalog()
            withContext(Dispatchers.Main) { installed = catalog.installations }
        }
    }

    LaunchedEffect(Unit) { reload() }

    val activeRuntime =
        remember(activeInstance.reference.coordinate) {
            ThemeRuntimeRepositoryV2.require(activeInstance.reference.coordinate)
        }
    val resolvedParameters =
        remember(activeInstance, activeRuntime) {
            ThemePackageRuntimeLinkerV2.resolveParameters(activeInstance, activeRuntime)
        }
    val activeDefinitions =
        remember(activeRuntime, resolvedParameters) {
            activeRuntime.parameterDefinitions.values.filter { definition ->
                activeRuntime.parameterOwners[definition.id] == activeRuntime.coordinate &&
                    resolvedParameters.isUserVisible(definition)
            }.sortedWith(
                compareBy<ThemeParameterDefinitionV2>(
                    { definition -> requireNotNull(definition.section).ordinal },
                    ThemeParameterDefinitionV2::order,
                    ThemeParameterDefinitionV2::id,
                ),
            )
        }

    val importLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
            if (uri == null) return@rememberLauncherForActivityResult
            busy = true
            scope.launch {
                val message =
                    try {
                        val staged = withContext(Dispatchers.IO) { stageImport(context, uri) }
                        try {
                            val coordinate = installer.import(staged)
                            reload()
                            context.getString(R.string.theme_packages_import_success, coordinate.packageId.value)
                        } finally {
                            withContext(Dispatchers.IO) { staged.delete() }
                        }
                    } catch (error: Throwable) {
                        AppLogger.e(TAG, "Theme package import failed.", error)
                        error.message ?: context.getString(R.string.theme_packages_import_failed)
                    }
                busy = false
                snackbar.showSnackbar(message)
            }
        }

    val resourceLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            val parameterId = pendingResourceParameterId
            val expectedCoordinateKey = pendingResourceCoordinateKey
            pendingResourceParameterId = null
            pendingResourceCoordinateKey = null
            if (uri == null || parameterId == null || expectedCoordinateKey == null) {
                return@rememberLauncherForActivityResult
            }
            if (activeInstance.reference.coordinate.selectionKey() != expectedCoordinateKey) {
                scope.launch {
                    snackbar.showSnackbar(context.getString(R.string.theme_packages_resource_selection_expired))
                }
                return@rememberLauncherForActivityResult
            }
            val definition = activeRuntime.parameterDefinitions[parameterId]
            val resourceControl = definition?.control as? ThemeParameterControlV2
            if (definition == null || resourceControl?.resourceMimeTypes() == null) {
                scope.launch {
                    snackbar.showSnackbar(context.getString(R.string.theme_packages_resource_selection_expired))
                }
                return@rememberLauncherForActivityResult
            }
            scope.launch {
                val message =
                    try {
                        withContext(Dispatchers.IO) {
                            validateThemeResourceUri(context, uri, resourceControl)
                        }
                        installer.replaceActiveResourceParameter(
                            expectedCoordinate = activeInstance.reference.coordinate,
                            parameterId = definition.id,
                            uri = uri,
                            value = definition.resourceValue(uri.toString()),
                        )
                        context.getString(R.string.theme_packages_resource_selected)
                    } catch (error: Throwable) {
                        AppLogger.e(TAG, "Theme resource selection failed.", error)
                        error.message ?: context.getString(R.string.theme_packages_resource_invalid)
                    }
                snackbar.showSnackbar(message)
            }
        }

    val selectedInstallation =
        installed.firstOrNull { installation ->
            installation.coordinate == activeInstance.reference.coordinate
        }
    val selectedTitle =
        selectedInstallation?.manifest?.displayName?.resolve(Locale.getDefault().language)
            ?: activeInstance.reference.coordinate.packageId.value
    val selectedSubtitle =
        selectedInstallation?.let { installation ->
            "${installation.coordinate.packageId.value} · v${installation.coordinate.version.value}"
        } ?: activeInstance.reference.coordinate.packageId.value

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        containerColor = Color.Transparent,
        contentColor = LocalContentColor.current,
    ) { padding ->
        ThemePackagesContent(
            selectedTitle = selectedTitle,
            selectedSubtitle = selectedSubtitle,
            installed = installed,
            presentation = presentation,
            definitions = activeDefinitions,
            resolvedValues = resolvedParameters.values,
            overriddenIds = resolvedParameters.overriddenIds,
            busy = busy,
            onOpenThemePicker = { showThemePicker = true },
            onRefresh = ::reload,
            onImport = { importLauncher.launch("*/*") },
            onThemeModeChange = { mode -> scope.launch { globalPresentation.setThemeMode(mode) } },
            onFontScaleChange = { scale -> scope.launch { globalPresentation.setFontScale(scale) } },
            onChatStyleChange = { style -> scope.launch { globalPresentation.setChatStyle(style) } },
            onInputStyleChange = { style -> scope.launch { globalPresentation.setInputStyle(style) } },
            onParameterChange = { definition, value ->
                scope.launch {
                    try {
                        replaceActiveParameter(
                            context = context,
                            expected = activeInstance,
                            parameterId = definition.id,
                            value = value,
                        )
                    } catch (error: Throwable) {
                        AppLogger.e(TAG, "Theme parameter update failed.", error)
                        snackbar.showSnackbar(
                            error.message ?: context.getString(R.string.theme_packages_parameter_update_failed),
                        )
                    }
                }
            },
            onParameterClear = { definition ->
                scope.launch {
                    try {
                        if (definition.control.resourceMimeTypes() != null) {
                            installer.clearActiveResourceParameter(
                                expectedCoordinate = activeInstance.reference.coordinate,
                                parameterId = definition.id,
                            )
                        } else {
                            clearActiveParameter(
                                context = context,
                                expected = activeInstance,
                                parameterId = definition.id,
                            )
                        }
                    } catch (error: Throwable) {
                        AppLogger.e(TAG, "Theme parameter reset failed.", error)
                        snackbar.showSnackbar(
                            error.message ?: context.getString(R.string.theme_packages_parameter_update_failed),
                        )
                    }
                }
            },
            onOpenResourcePicker = { definition ->
                val mimeTypes = requireNotNull(definition.control.resourceMimeTypes()) {
                    "Theme resource picker requested for a non-resource parameter."
                }
                pendingResourceParameterId = definition.id
                pendingResourceCoordinateKey = activeInstance.reference.coordinate.selectionKey()
                resourceLauncher.launch(mimeTypes.toTypedArray())
            },
            onOpenColorDialog = { definition -> colorDialogDefinition = definition },
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(padding),
        )
    }

    if (showThemePicker) {
        ThemePickerDialog(
            installed = installed,
            activeInstance = activeInstance,
            isLinked = ThemeRuntimeRepositoryV2::isLinked,
            onDismiss = { showThemePicker = false },
            onActivate = { installation ->
                showThemePicker = false
                scope.launch {
                    try {
                        installer.activate(installation.coordinate)
                    } catch (error: Throwable) {
                        AppLogger.e(TAG, "Theme package activation failed.", error)
                        snackbar.showSnackbar(
                            error.message ?: context.getString(R.string.theme_packages_activation_unavailable),
                        )
                    }
                }
            },
            onUninstall = { installation ->
                scope.launch {
                    val message =
                        try {
                            installer.uninstall(installation.coordinate)
                            reload()
                            context.getString(R.string.theme_packages_uninstalled)
                        } catch (error: Throwable) {
                            AppLogger.e(TAG, "Theme package uninstall failed.", error)
                            error.message ?: context.getString(R.string.theme_packages_uninstall_failed)
                        }
                    snackbar.showSnackbar(message)
                }
            },
        )
    }

    colorDialogDefinition?.let { definition ->
        val currentArgb =
            (resolvedParameters.values[definition.id] as? ThemeParameterValueV2.ColorValue)?.argb
                ?: error("Theme color parameter ${definition.id} has no resolved color.")
        AccentColorDialog(
            title = definition.label.resolve(Locale.getDefault().language),
            initialArgb = currentArgb,
            onDismiss = { colorDialogDefinition = null },
            onConfirm = { argb ->
                colorDialogDefinition = null
                scope.launch {
                    try {
                        val defaultArgb =
                            (definition.defaultValue as? ThemeParameterValueV2.ColorValue)?.argb
                        if (argb == defaultArgb) {
                            clearActiveParameter(
                                context = context,
                                expected = activeInstance,
                                parameterId = definition.id,
                            )
                        } else {
                            replaceActiveParameter(
                                context = context,
                                expected = activeInstance,
                                parameterId = definition.id,
                                value = ThemeParameterValueV2.ColorValue(argb),
                            )
                        }
                    } catch (error: Throwable) {
                        AppLogger.e(TAG, "Theme custom color update failed.", error)
                        snackbar.showSnackbar(
                            error.message ?: context.getString(R.string.theme_packages_parameter_update_failed),
                        )
                    }
                }
            },
        )
    }
}

@Composable
internal fun ThemePackagesContent(
    selectedTitle: String,
    selectedSubtitle: String,
    installed: List<PublishedThemeInstallationV2>,
    presentation: GlobalPresentationSnapshot,
    definitions: List<ThemeParameterDefinitionV2>,
    resolvedValues: Map<String, ThemeParameterValueV2>,
    overriddenIds: Set<String>,
    busy: Boolean,
    onOpenThemePicker: () -> Unit,
    onRefresh: () -> Unit,
    onImport: () -> Unit,
    onThemeModeChange: (GlobalThemeMode) -> Unit,
    onFontScaleChange: (Float) -> Unit,
    onChatStyleChange: (GlobalChatStyle) -> Unit,
    onInputStyleChange: (GlobalInputStyle) -> Unit,
    onParameterChange: (ThemeParameterDefinitionV2, ThemeParameterValueV2) -> Unit,
    onParameterClear: (ThemeParameterDefinitionV2) -> Unit,
    onOpenResourcePicker: (ThemeParameterDefinitionV2) -> Unit,
    onOpenColorDialog: (ThemeParameterDefinitionV2) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.theme_packages_title),
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.weight(1f),
            )
            if (busy) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    strokeWidth = 2.dp,
                )
                Spacer(modifier = Modifier.width(8.dp))
            }
            IconButton(onClick = onRefresh, enabled = !busy) {
                Icon(
                    imageVector = Icons.Default.Refresh,
                    contentDescription = stringResource(R.string.theme_packages_refresh),
                )
            }
            IconButton(onClick = onImport, enabled = !busy) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = stringResource(R.string.theme_packages_import),
                )
            }
        }

        ThemeSettingsSection(title = stringResource(R.string.theme_packages_section_theme)) {
            ThemeSelectionRow(
                title = selectedTitle,
                subtitle = selectedSubtitle,
                enabled = installed.isNotEmpty(),
                onClick = onOpenThemePicker,
            )
        }

        ThemeSettingsSection(title = stringResource(R.string.theme_packages_section_appearance)) {
            ThemeModeControl(
                selected = presentation.themeMode,
                onSelected = onThemeModeChange,
            )
            ThemeFontScaleControl(
                value = presentation.fontScale,
                onValueCommitted = onFontScaleChange,
            )
            ThemeParameterSectionControls(
                definitions = definitions,
                section = ThemeParameterSectionV2.APPEARANCE,
                resolvedValues = resolvedValues,
                overriddenIds = overriddenIds,
                onParameterChange = onParameterChange,
                onParameterClear = onParameterClear,
                onOpenResourcePicker = onOpenResourcePicker,
                onOpenColorDialog = onOpenColorDialog,
            )
        }

        ThemeSettingsSection(title = stringResource(R.string.theme_packages_section_conversation)) {
            ThemeChatStyleControl(
                selected = presentation.chatStyle,
                onSelected = onChatStyleChange,
            )
            ThemeParameterSectionControls(
                definitions = definitions,
                section = ThemeParameterSectionV2.CONVERSATION,
                resolvedValues = resolvedValues,
                overriddenIds = overriddenIds,
                onParameterChange = onParameterChange,
                onParameterClear = onParameterClear,
                onOpenResourcePicker = onOpenResourcePicker,
                onOpenColorDialog = onOpenColorDialog,
            )
        }

        val composerDefinitions = definitions.filter { definition -> definition.section == ThemeParameterSectionV2.COMPOSER }
        ThemeSettingsSection(title = stringResource(R.string.theme_packages_section_composer)) {
            ThemeInputStyleControl(
                selected = presentation.inputStyle,
                onSelected = onInputStyleChange,
            )
            ThemeParameterSectionControls(
                definitions = composerDefinitions,
                section = ThemeParameterSectionV2.COMPOSER,
                resolvedValues = resolvedValues,
                overriddenIds = overriddenIds,
                onParameterChange = onParameterChange,
                onParameterClear = onParameterClear,
                onOpenResourcePicker = onOpenResourcePicker,
                onOpenColorDialog = onOpenColorDialog,
            )
        }

        val appChromeDefinitions = definitions.filter { definition -> definition.section == ThemeParameterSectionV2.APP_CHROME }
        if (appChromeDefinitions.isNotEmpty()) {
            ThemeSettingsSection(title = stringResource(R.string.theme_packages_section_app_chrome)) {
                ThemeParameterSectionControls(
                    definitions = appChromeDefinitions,
                    section = ThemeParameterSectionV2.APP_CHROME,
                    resolvedValues = resolvedValues,
                    overriddenIds = overriddenIds,
                    onParameterChange = onParameterChange,
                    onParameterClear = onParameterClear,
                    onOpenResourcePicker = onOpenResourcePicker,
                    onOpenColorDialog = onOpenColorDialog,
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))
    }
}

@Composable
private fun ThemeParameterSectionControls(
    definitions: List<ThemeParameterDefinitionV2>,
    section: ThemeParameterSectionV2,
    resolvedValues: Map<String, ThemeParameterValueV2>,
    overriddenIds: Set<String>,
    onParameterChange: (ThemeParameterDefinitionV2, ThemeParameterValueV2) -> Unit,
    onParameterClear: (ThemeParameterDefinitionV2) -> Unit,
    onOpenResourcePicker: (ThemeParameterDefinitionV2) -> Unit,
    onOpenColorDialog: (ThemeParameterDefinitionV2) -> Unit,
) {
    definitions
        .filter { definition -> definition.section == section }
        .forEach { definition ->
            ThemeParameterControl(
                definition = definition,
                value = resolvedValues[definition.id],
                hasOverride = definition.id in overriddenIds,
                onValueChange = { changed -> onParameterChange(definition, changed) },
                onClear = { onParameterClear(definition) },
                onOpenResourcePicker = { onOpenResourcePicker(definition) },
                onOpenColorDialog = { onOpenColorDialog(definition) },
            )
        }
}

@Composable
private fun ThemeSettingsSection(
    title: String,
    content: @Composable () -> Unit,
) {
    Text(
        text = title,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 16.dp, bottom = 6.dp),
    )
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.58f))
    Column(modifier = Modifier.padding(vertical = 4.dp), content = { content() })
}

@Composable
private fun ThemeSelectionRow(
    title: String,
    subtitle: String,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    ThemeComponentSurfaceV2(
        component = ThemeComponentCatalogV2.LIST_ITEM,
        state = if (enabled) ThemeComponentStateV2.NORMAL else ThemeComponentStateV2.DISABLED,
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp).clickable(enabled = enabled, onClick = onClick),
        applyContentPadding = false,
    ) {
        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
            Text(text = title, style = MaterialTheme.typography.titleMedium)
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = LocalContentColor.current.copy(alpha = 0.72f),
            )
        }
    }
}

@Composable
private fun ThemeModeControl(
    selected: GlobalThemeMode,
    onSelected: (GlobalThemeMode) -> Unit,
) {
    ThemeControlLabel(text = stringResource(R.string.global_presentation_theme_mode))
    val options =
        GlobalThemeMode.entries.map { mode ->
            ThemeChoiceOption(
                value = mode,
                label =
                    stringResource(
                        when (mode) {
                            GlobalThemeMode.SYSTEM -> R.string.global_presentation_theme_mode_system
                            GlobalThemeMode.LIGHT -> R.string.global_presentation_theme_mode_light
                            GlobalThemeMode.DARK -> R.string.global_presentation_theme_mode_dark
                        },
                    ),
            )
        }
    ThemeChoiceSelector(selected = selected, options = options, onSelected = onSelected)
}

@Composable
private fun ThemeFontScaleControl(
    value: Float,
    onValueCommitted: (Float) -> Unit,
) {
    var localValue by remember(value) { mutableFloatStateOf(value) }
    ThemeControlLabel(
        text = stringResource(R.string.global_presentation_font_scale),
        value = "${(localValue * 100).roundToInt()}%",
        onReset = {
            localValue = GlobalPresentationSnapshot.DEFAULT_FONT_SCALE
            onValueCommitted(GlobalPresentationSnapshot.DEFAULT_FONT_SCALE)
        },
    )
    Slider(
        value = localValue,
        onValueChange = { localValue = it },
        onValueChangeFinished = { onValueCommitted(localValue) },
        valueRange = GlobalPresentationSnapshot.MIN_FONT_SCALE..GlobalPresentationSnapshot.MAX_FONT_SCALE,
        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
    )
}

@Composable
private fun ThemeChatStyleControl(
    selected: GlobalChatStyle,
    onSelected: (GlobalChatStyle) -> Unit,
) {
    ThemeControlLabel(text = stringResource(R.string.global_presentation_chat_style))
    val options =
        GlobalChatStyle.entries.map { style ->
            ThemeChoiceOption(
                value = style,
                label =
                    stringResource(
                        when (style) {
                            GlobalChatStyle.CURSOR -> R.string.global_presentation_chat_style_cursor
                            GlobalChatStyle.BUBBLE -> R.string.global_presentation_chat_style_bubble
                        },
                    ),
            )
        }
    ThemeChoiceSelector(selected = selected, options = options, onSelected = onSelected)
}

@Composable
private fun ThemeInputStyleControl(
    selected: GlobalInputStyle,
    onSelected: (GlobalInputStyle) -> Unit,
) {
    ThemeControlLabel(text = stringResource(R.string.global_presentation_input_style))
    val options =
        GlobalInputStyle.entries.map { style ->
            ThemeChoiceOption(
                value = style,
                label =
                    stringResource(
                        when (style) {
                            GlobalInputStyle.AGENT -> R.string.global_presentation_input_style_agent
                            GlobalInputStyle.CLASSIC -> R.string.global_presentation_input_style_classic
                        },
                    ),
            )
        }
    ThemeChoiceSelector(selected = selected, options = options, onSelected = onSelected)
}

private data class ThemeChoiceOption<T>(
    val value: T,
    val label: String,
)

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun <T> ThemeChoiceSelector(
    selected: T,
    options: List<ThemeChoiceOption<T>>,
    onSelected: (T) -> Unit,
) {
    val configuration = LocalConfiguration.current
    if (usesStackedThemeChoiceLayout(configuration.screenWidthDp, configuration.fontScale)) {
        Column(
            modifier = Modifier.selectableGroup().padding(bottom = 8.dp),
        ) {
            options.forEach { option ->
                val isSelected = selected == option.value
                Row(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .selectable(
                                selected = isSelected,
                                role = Role.RadioButton,
                                onClick = { onSelected(option.value) },
                            )
                            .padding(vertical = 7.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = option.label,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f),
                    )
                    RadioButton(selected = isSelected, onClick = null)
                }
            }
        }
    } else {
        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
            options.forEachIndexed { index, option ->
                SegmentedButton(
                    selected = selected == option.value,
                    onClick = { onSelected(option.value) },
                    shape = SegmentedButtonDefaults.itemShape(index = index, count = options.size),
                ) {
                    Text(text = option.label)
                }
            }
        }
    }
}

internal fun usesStackedThemeChoiceLayout(
    screenWidthDp: Int,
    fontScale: Float,
): Boolean = screenWidthDp < 440 || fontScale > 1.25f

@Composable
private fun ThemeParameterControl(
    definition: ThemeParameterDefinitionV2,
    value: ThemeParameterValueV2?,
    hasOverride: Boolean,
    onValueChange: (ThemeParameterValueV2) -> Unit,
    onClear: () -> Unit,
    onOpenResourcePicker: () -> Unit,
    onOpenColorDialog: () -> Unit,
) {
    val onReset = onClear.takeIf { hasOverride }
    when (val control = definition.control) {
        is ThemeParameterControlV2.ColorPalette -> {
            val color = value as? ThemeParameterValueV2.ColorValue
                ?: error("Theme color control received a non-color value.")
            val defaultArgb = (definition.defaultValue as? ThemeParameterValueV2.ColorValue)?.argb
            ThemeColorControl(
                title = definition.label.resolve(Locale.getDefault().language),
                description = definition.description?.resolve(Locale.getDefault().language),
                control = control,
                currentArgb = color.argb,
                onPick = { argb ->
                    if (argb == defaultArgb) {
                        onClear()
                    } else {
                        onValueChange(ThemeParameterValueV2.ColorValue(argb))
                    }
                },
                onOpenCustom = onOpenColorDialog,
                onReset = onReset,
            )
        }

        ThemeParameterControlV2.Toggle -> {
            val selected = (value as? ThemeParameterValueV2.BooleanValue)?.value
                ?: error("Theme toggle control received a non-boolean value.")
            val default = (definition.defaultValue as? ThemeParameterValueV2.BooleanValue)?.value
            ThemeToggleControl(
                title = definition.label.resolve(Locale.getDefault().language),
                description = definition.description?.resolve(Locale.getDefault().language),
                checked = selected,
                onCheckedChange = { changed ->
                    if (changed == default) {
                        onClear()
                    } else {
                        onValueChange(ThemeParameterValueV2.BooleanValue(changed))
                    }
                },
                onReset = onReset,
            )
        }

        is ThemeParameterControlV2.Choice -> {
            val selected = (value as? ThemeParameterValueV2.OptionValue)?.value
                ?: error("Theme choice control received a non-option value.")
            val default = (definition.defaultValue as? ThemeParameterValueV2.OptionValue)?.value
            ThemeControlLabel(
                text = definition.label.resolve(Locale.getDefault().language),
                description = definition.description?.resolve(Locale.getDefault().language),
                onReset = onReset,
            )
            ThemeChoiceSelector(
                selected = selected,
                options =
                    control.options.map { option ->
                        ThemeChoiceOption(
                            value = option.id,
                            label = option.label.resolve(Locale.getDefault().language),
                        )
                    },
                onSelected = { selectedOption ->
                    if (selectedOption == default) {
                        onClear()
                    } else {
                        onValueChange(ThemeParameterValueV2.OptionValue(selectedOption))
                    }
                },
            )
        }

        is ThemeParameterControlV2.Slider -> {
            val selected = (value as? ThemeParameterValueV2.FloatValue)?.value
                ?: error("Theme slider control received a non-numeric value.")
            val default = (definition.defaultValue as? ThemeParameterValueV2.FloatValue)?.value
            ThemeParameterSliderControl(
                title = definition.label.resolve(Locale.getDefault().language),
                description = definition.description?.resolve(Locale.getDefault().language),
                control = control,
                value = selected,
                onValueCommitted = { changed ->
                    if (changed == default) {
                        onClear()
                    } else {
                        onValueChange(ThemeParameterValueV2.FloatValue(changed))
                    }
                },
                onReset = onReset,
            )
        }

        is ThemeParameterControlV2.ImagePicker,
        is ThemeParameterControlV2.VideoPicker,
        is ThemeParameterControlV2.FontPicker
        -> {
            val uri = (value as? ThemeParameterValueV2.ImageUriValue)?.uri
                ?: (value as? ThemeParameterValueV2.VideoUriValue)?.uri
                ?: (value as? ThemeParameterValueV2.FontUriValue)?.uri
            ThemeResourceControl(
                title = definition.label.resolve(Locale.getDefault().language),
                description = definition.description?.resolve(Locale.getDefault().language),
                uri = uri,
                showPreview = control is ThemeParameterControlV2.ImagePicker,
                actionLabel =
                    stringResource(
                        when (control) {
                            is ThemeParameterControlV2.ImagePicker -> R.string.theme_packages_pick_image
                            is ThemeParameterControlV2.VideoPicker -> R.string.theme_packages_pick_video
                            is ThemeParameterControlV2.FontPicker -> R.string.theme_packages_pick_font
                            else -> error("Theme resource control must be image, video, or font.")
                        },
                    ),
                onPick = onOpenResourcePicker,
                onClear = onReset,
            )
        }

        is ThemeParameterControlV2.ColorPairPalette,
        ThemeParameterControlV2.AuthorValue
        -> error("Theme package exposes a parameter control that is not valid for the user settings surface.")
    }
}

@Composable
private fun ThemeToggleControl(
    title: String,
    description: String?,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    onReset: (() -> Unit)?,
) {
    ThemeControlLabel(text = title, description = description, onReset = onReset)
    Row(
        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
        horizontalArrangement = Arrangement.End,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun ThemeParameterSliderControl(
    title: String,
    description: String?,
    control: ThemeParameterControlV2.Slider,
    value: Float,
    onValueCommitted: (Float) -> Unit,
    onReset: (() -> Unit)?,
) {
    var localValue by remember(value) { mutableFloatStateOf(value) }
    ThemeControlLabel(
        text = title,
        description = description,
        value = "${(localValue * 100).roundToInt()}%",
        onReset = onReset,
    )
    Slider(
        value = localValue,
        onValueChange = { changed -> localValue = snapThemeParameterSliderValue(changed, control) },
        onValueChangeFinished = { onValueCommitted(localValue) },
        valueRange = control.minimum..control.maximum,
        steps = ((control.maximum - control.minimum) / control.step).roundToInt().minus(1).coerceAtLeast(0),
        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
    )
}

internal fun snapThemeParameterSliderValue(
    value: Float,
    control: ThemeParameterControlV2.Slider,
): Float =
    (control.minimum + ((value - control.minimum) / control.step).roundToInt() * control.step)
        .coerceIn(control.minimum, control.maximum)

@Composable
@OptIn(ExperimentalLayoutApi::class)
private fun ThemeColorControl(
    title: String,
    description: String?,
    control: ThemeParameterControlV2.ColorPalette,
    currentArgb: Long,
    onPick: (Long) -> Unit,
    onOpenCustom: () -> Unit,
    onReset: (() -> Unit)?,
) {
    ThemeControlLabel(text = title, description = description, onReset = onReset)
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
    ) {
        control.presetArgb.forEach { argb ->
            val selected = currentArgb == argb
            Box(
                modifier =
                    Modifier
                        .size(32.dp)
                        .background(Color(argb.toInt()), CircleShape)
                        .border(
                            width = if (selected) 3.dp else 1.dp,
                            color = if (selected) LocalContentColor.current else MaterialTheme.colorScheme.outlineVariant,
                            shape = CircleShape,
                        )
                        .clickable { onPick(argb) },
            )
        }
        if (control.allowCustom) {
            IconButton(onClick = onOpenCustom) {
                Icon(
                    imageVector = Icons.Default.Colorize,
                    contentDescription = stringResource(R.string.theme_packages_customize_color),
                )
            }
        }
    }
}

@Composable
private fun ThemeResourceControl(
    title: String,
    description: String?,
    uri: String?,
    showPreview: Boolean,
    actionLabel: String,
    onPick: () -> Unit,
    onClear: (() -> Unit)?,
) {
    ThemeControlLabel(text = title, description = description, onReset = onClear)
    Row(
        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (uri != null && showPreview) {
            AsyncImage(
                model = uri,
                contentDescription = null,
                modifier = Modifier.size(40.dp),
            )
            Spacer(modifier = Modifier.width(8.dp))
        }
        TextButton(onClick = onPick) {
            Icon(imageVector = Icons.Default.Image, contentDescription = null)
            Spacer(modifier = Modifier.width(6.dp))
            Text(actionLabel)
        }
    }
}

@Composable
private fun ThemeControlLabel(
    text: String,
    description: String? = null,
    value: String? = null,
    onReset: (() -> Unit)? = null,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = text, style = MaterialTheme.typography.bodyMedium)
            description?.let { detail ->
                Text(
                    text = detail,
                    style = MaterialTheme.typography.bodySmall,
                    color = LocalContentColor.current.copy(alpha = 0.68f),
                )
            }
        }
        value?.let { rendered ->
            Text(
                text = rendered,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        onReset?.let { reset ->
            IconButton(onClick = reset) {
                Icon(
                    imageVector = Icons.Default.Refresh,
                    contentDescription = stringResource(R.string.theme_packages_reset),
                )
            }
        }
    }
}

@Composable
private fun ThemePickerDialog(
    installed: List<PublishedThemeInstallationV2>,
    activeInstance: ThemeInstanceV2,
    isLinked: (ThemePackageCoordinateV2) -> Boolean,
    onDismiss: () -> Unit,
    onActivate: (PublishedThemeInstallationV2) -> Unit,
    onUninstall: (PublishedThemeInstallationV2) -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.theme_packages_picker_title)) },
        text = {
            Column(
                modifier = Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState()).selectableGroup(),
            ) {
                installed.forEach { installation ->
                    val coordinate = installation.coordinate
                    val selected = activeInstance.reference.coordinate == coordinate
                    val isDefault = ThemePackageDefaultV2.isDefault(coordinate)
                    val linked = isLinked(coordinate)
                    val unavailableLabel = stringResource(R.string.theme_packages_activation_unavailable)
                    ThemeComponentSurfaceV2(
                        component = ThemeComponentCatalogV2.LIST_ITEM,
                        state = themePickerEntryState(selected = selected, linked = linked),
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .padding(vertical = 3.dp)
                                .selectable(
                                    selected = selected,
                                    enabled = linked,
                                    role = Role.RadioButton,
                                    onClick = { onActivate(installation) },
                                ),
                        applyContentPadding = false,
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = installation.manifest.displayName.resolve(Locale.getDefault().language),
                                    style = MaterialTheme.typography.titleSmall,
                                )
                                Text(
                                    text =
                                        if (isDefault) {
                                            stringResource(R.string.theme_packages_builtin)
                                        } else {
                                            "${coordinate.packageId.value} · v${coordinate.version.value}" +
                                                if (linked) "" else " · $unavailableLabel"
                                        },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = LocalContentColor.current.copy(alpha = 0.72f),
                                )
                            }
                            if (selected) {
                                Icon(imageVector = Icons.Default.Check, contentDescription = null)
                            }
                            if (!isDefault) {
                                IconButton(onClick = { onUninstall(installation) }) {
                                    Icon(
                                        imageVector = Icons.Default.Delete,
                                        contentDescription = stringResource(R.string.theme_packages_uninstall),
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.theme_packages_close))
            }
        },
    )
}

internal fun themePickerEntryState(
    selected: Boolean,
    linked: Boolean,
): ThemeComponentStateV2 =
    when {
        !linked -> ThemeComponentStateV2.DISABLED
        selected -> ThemeComponentStateV2.SELECTED
        else -> ThemeComponentStateV2.NORMAL
    }

@Composable
private fun AccentColorDialog(
    title: String,
    initialArgb: Long,
    onDismiss: () -> Unit,
    onConfirm: (Long) -> Unit,
) {
    var hex by remember(initialArgb) { mutableStateOf("#%06X".format(Locale.US, initialArgb and 0xFFFFFFL)) }
    val parsed = parseOpaqueArgb(hex)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .height(36.dp)
                            .background(parsed?.let { value -> Color(value.toInt()) } ?: MaterialTheme.colorScheme.surfaceVariant),
                )
                OutlinedTextField(
                    value = hex,
                    onValueChange = { value -> hex = value },
                    label = { Text(stringResource(R.string.theme_packages_color_hex)) },
                    singleLine = true,
                    isError = hex.isNotBlank() && parsed == null,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.theme_packages_cancel))
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(requireNotNull(parsed)) }, enabled = parsed != null) {
                Text(stringResource(R.string.theme_packages_apply))
            }
        },
    )
}

private suspend fun replaceActiveParameter(
    context: Context,
    expected: ThemeInstanceV2,
    parameterId: String,
    value: ThemeParameterValueV2,
) {
    val repository = ThemePackageSelectionRepositoryV2.getInstance(context)
    repository.replaceParameter(
        expectedCoordinate = expected.reference.coordinate,
        parameterId = parameterId,
        value = value,
    )
}

private suspend fun clearActiveParameter(
    context: Context,
    expected: ThemeInstanceV2,
    parameterId: String,
) {
    val repository = ThemePackageSelectionRepositoryV2.getInstance(context)
    repository.clearParameter(
        expectedCoordinate = expected.reference.coordinate,
        parameterId = parameterId,
    )
}

private fun ThemeParameterControlV2.resourceMimeTypes(): List<String>? =
    when (this) {
        is ThemeParameterControlV2.ImagePicker -> mimeTypes
        is ThemeParameterControlV2.VideoPicker -> mimeTypes
        is ThemeParameterControlV2.FontPicker -> mimeTypes
        else -> null
    }

private fun ThemeParameterDefinitionV2.resourceValue(uri: String): ThemeParameterValueV2 =
    when (control) {
        is ThemeParameterControlV2.ImagePicker -> ThemeParameterValueV2.ImageUriValue(uri)
        is ThemeParameterControlV2.VideoPicker -> ThemeParameterValueV2.VideoUriValue(uri)
        is ThemeParameterControlV2.FontPicker -> ThemeParameterValueV2.FontUriValue(uri)
        else -> error("Theme parameter $id is not a resource parameter.")
    }

private fun validateThemeResourceUri(
    context: Context,
    uri: Uri,
    control: ThemeParameterControlV2,
) {
    val mimeType = context.contentResolver.getType(uri)?.lowercase(Locale.ROOT)
    require(mimeType in requireNotNull(control.resourceMimeTypes())) {
        context.getString(R.string.theme_packages_resource_invalid)
    }
    val bytes =
        context.contentResolver.openInputStream(uri)?.use(::readBoundedThemeResource)
            ?: throw IllegalArgumentException(context.getString(R.string.theme_packages_resource_invalid))
    when (control) {
        is ThemeParameterControlV2.ImagePicker -> {
            val bitmap =
                ImageBitmapLimiter.decodeDownsampledBitmap(bytes)
                    ?: throw IllegalArgumentException(context.getString(R.string.theme_packages_resource_invalid))
            bitmap.recycle()
        }

        is ThemeParameterControlV2.VideoPicker ->
            require(bytes.isThemeVideo()) { context.getString(R.string.theme_packages_resource_invalid) }

        is ThemeParameterControlV2.FontPicker ->
            require(bytes.isThemeFont()) { context.getString(R.string.theme_packages_resource_invalid) }

        else -> error("Theme resource validation requires an image, video, or font picker.")
    }
}

private fun ThemePackageCoordinateV2.selectionKey(): String =
    "${packageId.value}|${version.value}|${archiveSha256.value}"

private fun readBoundedThemeResource(input: java.io.InputStream): ByteArray {
    val output = ByteArrayOutputStream()
    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
    while (true) {
        val read = input.read(buffer)
        if (read < 0) break
        require(output.size() + read <= THEME_RESOURCE_MAX_BYTES) {
            "Theme resource exceeds the ${THEME_RESOURCE_MAX_BYTES / 1024 / 1024} MB limit."
        }
        output.write(buffer, 0, read)
    }
    return output.toByteArray()
}

private fun ByteArray.isThemeVideo(): Boolean =
    (size >= 12 && this[4] == 'f'.code.toByte() && this[5] == 't'.code.toByte() && this[6] == 'y'.code.toByte() && this[7] == 'p'.code.toByte()) ||
        (size >= 4 && this[0] == 0x1A.toByte() && this[1] == 0x45.toByte() && this[2] == 0xDF.toByte() && this[3] == 0xA3.toByte())

private fun ByteArray.isThemeFont(): Boolean =
    (size >= 4 && this[0] == 0x00.toByte() && this[1] == 0x01.toByte() && this[2] == 0x00.toByte() && this[3] == 0x00.toByte()) ||
        (size >= 4 && this[0] == 'O'.code.toByte() && this[1] == 'T'.code.toByte() && this[2] == 'T'.code.toByte() && this[3] == 'O'.code.toByte())

private fun parseOpaqueArgb(raw: String): Long? {
    val normalized = raw.trim()
    if (!Regex("^#[0-9A-Fa-f]{6}$").matches(normalized)) return null
    return AndroidColor.parseColor(normalized).toLong() and 0xFFFFFFFFL
}

private fun stageImport(
    context: Context,
    uri: Uri,
): java.io.File {
    val name =
        queryDisplayName(context, uri)
            ?: uri.lastPathSegment
            ?: "theme.otheme"
    if (!ThemePackageInstallerV2.isThemePackageFileName(name)) {
        error(context.getString(R.string.theme_packages_not_theme_file))
    }
    val staged = java.io.File(context.cacheDir, "theme-import-${System.currentTimeMillis()}.otheme")
    context.contentResolver.openInputStream(uri)?.use { input ->
        staged.outputStream().use { output -> input.copyTo(output) }
    } ?: error(context.getString(R.string.theme_packages_import_failed))
    return staged
}

private fun queryDisplayName(
    context: Context,
    uri: Uri,
): String? =
    context.contentResolver.query(
        uri,
        arrayOf(android.provider.OpenableColumns.DISPLAY_NAME),
        null,
        null,
        null,
    )?.use { cursor ->
        if (cursor.moveToFirst()) {
            val index = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
            if (index >= 0) cursor.getString(index) else null
        } else {
            null
        }
    }
