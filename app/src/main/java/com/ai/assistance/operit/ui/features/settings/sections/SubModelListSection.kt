package com.ai.assistance.operit.ui.features.settings.sections

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ai.assistance.operit.R
import com.ai.assistance.operit.data.model.ModelConfigData
import com.ai.assistance.operit.data.model.ModelConfigDefaults
import com.ai.assistance.operit.data.model.ModelOverrideConfig
import com.ai.assistance.operit.data.model.getModelList
import com.ai.assistance.operit.data.preferences.ModelConfigManager
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * 子模型列表组件
 * 当配置中包含多个模型（逗号分隔）时，显示子模型列表，允许为每个模型设置独立参数
 */
@Composable
fun SubModelListSection(
    config: ModelConfigData,
    configManager: ModelConfigManager,
    showNotification: (String) -> Unit
) {
    val scope = rememberCoroutineScope()
    val modelList = remember(config.modelName) {
        getModelList(config.modelName)
    }

    // 只有多个模型时才显示
    if (modelList.size <= 1) return

    // 当前展开的模型
    var expandedModel by remember { mutableStateOf<String?>(null) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // 标题
            Text(
                text = stringResource(R.string.sub_model_settings),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = stringResource(R.string.sub_model_settings_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(4.dp))

            // 子模型列表
            modelList.forEach { modelName ->
                val hasOverride = config.modelOverrides.containsKey(modelName)
                val isExpanded = expandedModel == modelName

                SubModelItem(
                    modelName = modelName,
                    hasOverride = hasOverride,
                    isExpanded = isExpanded,
                    onClick = {
                        expandedModel = if (isExpanded) null else modelName
                    },
                    onDeleteOverride = if (hasOverride) {
                        {
                            scope.launch {
                                configManager.deleteModelOverride(config.id, modelName)
                                showNotification(
                                    stringResource(R.string.sub_model_override_reset, modelName)
                                )
                            }
                        }
                    } else null
                )

                // 展开的参数编辑面板
                AnimatedVisibility(
                    visible = isExpanded,
                    enter = expandVertically() + fadeIn(),
                    exit = shrinkVertically() + fadeOut()
                ) {
                    SubModelParameterPanel(
                        modelName = modelName,
                        config = config,
                        configManager = configManager,
                        showNotification = showNotification
                    )
                }
            }
        }
    }
}

/**
 * 子模型列表项
 */
@Composable
private fun SubModelItem(
    modelName: String,
    hasOverride: Boolean,
    isExpanded: Boolean,
    onClick: () -> Unit,
    onDeleteOverride: (() -> Unit)?
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(8.dp),
        color = if (isExpanded) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 展开/收起图标
            Icon(
                imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp)
            )

            Spacer(modifier = Modifier.width(8.dp))

            // 模型名称
            Text(
                text = modelName,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f)
            )

            // 状态标识
            if (hasOverride) {
                Icon(
                    imageVector = Icons.Default.Settings,
                    contentDescription = stringResource(R.string.sub_model_customized),
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = stringResource(R.string.sub_model_customized),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary
                )

                // 删除按钮
                if (onDeleteOverride != null) {
                    Spacer(modifier = Modifier.width(8.dp))
                    IconButton(
                        onClick = onDeleteOverride,
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = stringResource(R.string.sub_model_reset),
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            } else {
                Text(
                    text = stringResource(R.string.sub_model_inherit),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

/**
 * 可添加的参数定义
 */
private data class AddableParameter(
    val id: String,
    val nameResId: Int,
    val descriptionResId: Int
)

/** 可选参数的完整定义列表 */
private val addableParameters = listOf(
    AddableParameter("top_p", R.string.top_p_name, R.string.top_p_description),
    AddableParameter("top_k", R.string.top_k_name, R.string.top_k_description),
    AddableParameter("max_tokens", R.string.max_tokens_name, R.string.max_tokens_description),
    AddableParameter("presence_penalty", R.string.presence_penalty_name, R.string.presence_penalty_description),
    AddableParameter("frequency_penalty", R.string.frequency_penalty_name, R.string.frequency_penalty_description),
    AddableParameter("repetition_penalty", R.string.repetition_penalty_name, R.string.repetition_penalty_description)
)

/**
 * 子模型参数编辑面板
 */
@Composable
private fun SubModelParameterPanel(
    modelName: String,
    config: ModelConfigData,
    configManager: ModelConfigManager,
    showNotification: (String) -> Unit
) {
    val scope = rememberCoroutineScope()
    val existingOverride = config.modelOverrides[modelName]

    // 参数状态 - 使用覆盖值或配置默认值
    var temperatureEnabled by remember(modelName) {
        mutableStateOf(existingOverride?.temperatureEnabled ?: config.temperatureEnabled)
    }
    var temperature by remember(modelName) {
        mutableStateOf((existingOverride?.temperature ?: config.temperature).toString())
    }

    // 上下文长度参数
    var maxContextLengthEnabled by remember(modelName) {
        mutableStateOf(existingOverride?.maxContextLength != null)
    }
    var maxContextLength by remember(modelName) {
        mutableStateOf((existingOverride?.maxContextLength ?: ModelConfigDefaults.DEFAULT_MAX_CONTEXT_LENGTH).toString())
    }

    // 可选参数状态
    var topPEnabled by remember(modelName) {
        mutableStateOf(existingOverride?.topPEnabled ?: config.topPEnabled)
    }
    var topP by remember(modelName) {
        mutableStateOf((existingOverride?.topP ?: config.topP).toString())
    }
    var topKEnabled by remember(modelName) {
        mutableStateOf(existingOverride?.topKEnabled ?: config.topKEnabled)
    }
    var topK by remember(modelName) {
        mutableStateOf((existingOverride?.topK ?: config.topK).toString())
    }
    var maxTokensEnabled by remember(modelName) {
        mutableStateOf(existingOverride?.maxTokensEnabled ?: config.maxTokensEnabled)
    }
    var maxTokens by remember(modelName) {
        mutableStateOf((existingOverride?.maxTokens ?: config.maxTokens).toString())
    }
    var presencePenaltyEnabled by remember(modelName) {
        mutableStateOf(existingOverride?.presencePenaltyEnabled ?: config.presencePenaltyEnabled)
    }
    var presencePenalty by remember(modelName) {
        mutableStateOf((existingOverride?.presencePenalty ?: config.presencePenalty).toString())
    }
    var frequencyPenaltyEnabled by remember(modelName) {
        mutableStateOf(existingOverride?.frequencyPenaltyEnabled ?: config.frequencyPenaltyEnabled)
    }
    var frequencyPenalty by remember(modelName) {
        mutableStateOf((existingOverride?.frequencyPenalty ?: config.frequencyPenalty).toString())
    }
    var repetitionPenaltyEnabled by remember(modelName) {
        mutableStateOf(existingOverride?.repetitionPenaltyEnabled ?: config.repetitionPenaltyEnabled)
    }
    var repetitionPenalty by remember(modelName) {
        mutableStateOf((existingOverride?.repetitionPenalty ?: config.repetitionPenalty).toString())
    }

    // 已添加的可选参数集合
    var addedParameters by remember(modelName) {
        mutableStateOf(
            buildSet {
                if (existingOverride?.topPEnabled != null || existingOverride?.topP != null) add("top_p")
                if (existingOverride?.topKEnabled != null || existingOverride?.topK != null) add("top_k")
                if (existingOverride?.maxTokensEnabled != null || existingOverride?.maxTokens != null) add("max_tokens")
                if (existingOverride?.presencePenaltyEnabled != null || existingOverride?.presencePenalty != null) add("presence_penalty")
                if (existingOverride?.frequencyPenaltyEnabled != null || existingOverride?.frequencyPenalty != null) add("frequency_penalty")
                if (existingOverride?.repetitionPenaltyEnabled != null || existingOverride?.repetitionPenalty != null) add("repetition_penalty")
            }.toMutableStateList()
        )
    }

    // 下拉菜单状态
    var showAddMenu by remember { mutableStateOf(false) }

    // 防抖：延迟保存，避免快速切换时频繁写入
    var saveJob by remember { mutableStateOf<Job?>(null) }

    // 保存函数
    fun saveOverride() {
        saveJob?.cancel()
        saveJob = scope.launch {
            delay(300) // 300ms 防抖
            try {
                val override = ModelOverrideConfig(
                    temperatureEnabled = temperatureEnabled,
                    topPEnabled = if ("top_p" in addedParameters) topPEnabled else null,
                    topKEnabled = if ("top_k" in addedParameters) topKEnabled else null,
                    maxTokensEnabled = if ("max_tokens" in addedParameters) maxTokensEnabled else null,
                    presencePenaltyEnabled = if ("presence_penalty" in addedParameters) presencePenaltyEnabled else null,
                    frequencyPenaltyEnabled = if ("frequency_penalty" in addedParameters) frequencyPenaltyEnabled else null,
                    repetitionPenaltyEnabled = if ("repetition_penalty" in addedParameters) repetitionPenaltyEnabled else null,
                    temperature = temperature.toFloatOrNull(),
                    topP = if ("top_p" in addedParameters) topP.toFloatOrNull() else null,
                    topK = if ("top_k" in addedParameters) topK.toIntOrNull() else null,
                    maxTokens = if ("max_tokens" in addedParameters) maxTokens.toIntOrNull() else null,
                    presencePenalty = if ("presence_penalty" in addedParameters) presencePenalty.toFloatOrNull() else null,
                    frequencyPenalty = if ("frequency_penalty" in addedParameters) frequencyPenalty.toFloatOrNull() else null,
                    repetitionPenalty = if ("repetition_penalty" in addedParameters) repetitionPenalty.toFloatOrNull() else null,
                    maxContextLength = if (maxContextLengthEnabled) maxContextLength.toFloatOrNull() else null,
                    customParameters = existingOverride?.customParameters,
                    customHeaders = existingOverride?.customHeaders
                )
                configManager.updateModelOverride(config.id, modelName, override)
                showNotification(stringResource(R.string.sub_model_override_saved, modelName))
            } catch (e: Exception) {
                showNotification(stringResource(R.string.sub_model_override_save_failed, e.message ?: ""))
            }
        }
    }

    // 构建已添加参数的运行时状态映射，用于数据驱动渲染
    data class OptionalParamState(
        val id: String,
        val nameResId: Int,
        val descriptionResId: Int,
        val enabled: Boolean,
        val onEnabledChange: (Boolean) -> Unit,
        val value: String,
        val onValueChange: (String) -> Unit,
        val onValueSave: () -> Unit,
        val onRemove: () -> Unit
    )

    val optionalParamStates = remember(
        addedParameters.toList(),
        topPEnabled, topP, topKEnabled, topK,
        maxTokensEnabled, maxTokens, presencePenaltyEnabled, presencePenalty,
        frequencyPenaltyEnabled, frequencyPenalty, repetitionPenaltyEnabled, repetitionPenalty
    ) {
        addableParameters.filter { it.id in addedParameters }.map { param ->
            when (param.id) {
                "top_p" -> OptionalParamState(
                    param.id, param.nameResId, param.descriptionResId,
                    topPEnabled, { topPEnabled = it; saveOverride() },
                    topP, { topP = it }, { saveOverride() },
                    { addedParameters.remove("top_p"); topPEnabled = false; saveOverride() }
                )
                "top_k" -> OptionalParamState(
                    param.id, param.nameResId, param.descriptionResId,
                    topKEnabled, { topKEnabled = it; saveOverride() },
                    topK, { topK = it }, { saveOverride() },
                    { addedParameters.remove("top_k"); topKEnabled = false; saveOverride() }
                )
                "max_tokens" -> OptionalParamState(
                    param.id, param.nameResId, param.descriptionResId,
                    maxTokensEnabled, { maxTokensEnabled = it; saveOverride() },
                    maxTokens, { maxTokens = it }, { saveOverride() },
                    { addedParameters.remove("max_tokens"); maxTokensEnabled = false; saveOverride() }
                )
                "presence_penalty" -> OptionalParamState(
                    param.id, param.nameResId, param.descriptionResId,
                    presencePenaltyEnabled, { presencePenaltyEnabled = it; saveOverride() },
                    presencePenalty, { presencePenalty = it }, { saveOverride() },
                    { addedParameters.remove("presence_penalty"); presencePenaltyEnabled = false; saveOverride() }
                )
                "frequency_penalty" -> OptionalParamState(
                    param.id, param.nameResId, param.descriptionResId,
                    frequencyPenaltyEnabled, { frequencyPenaltyEnabled = it; saveOverride() },
                    frequencyPenalty, { frequencyPenalty = it }, { saveOverride() },
                    { addedParameters.remove("frequency_penalty"); frequencyPenaltyEnabled = false; saveOverride() }
                )
                "repetition_penalty" -> OptionalParamState(
                    param.id, param.nameResId, param.descriptionResId,
                    repetitionPenaltyEnabled, { repetitionPenaltyEnabled = it; saveOverride() },
                    repetitionPenalty, { repetitionPenalty = it }, { saveOverride() },
                    { addedParameters.remove("repetition_penalty"); repetitionPenaltyEnabled = false; saveOverride() }
                )
                else -> throw IllegalArgumentException("Unknown parameter: ${param.id}")
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

        Text(
            text = stringResource(R.string.sub_model_parameter_settings, modelName),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Medium
        )

        // Temperature（默认显示，不可移除）
        SubModelParameterRow(
            name = stringResource(R.string.temperature_name),
            enabled = temperatureEnabled,
            onEnabledChange = { temperatureEnabled = it; saveOverride() },
            value = temperature,
            onValueChange = { temperature = it },
            onValueSave = { saveOverride() },
            description = stringResource(R.string.temperature_description),
            onRemove = null
        )

        // Max Context Length（默认显示，不可移除）
        SubModelParameterRow(
            name = stringResource(R.string.sub_model_max_context_length),
            enabled = maxContextLengthEnabled,
            onEnabledChange = { maxContextLengthEnabled = it; saveOverride() },
            value = maxContextLength,
            onValueChange = { maxContextLength = it },
            onValueSave = { saveOverride() },
            description = stringResource(R.string.sub_model_max_context_length_desc),
            onRemove = null
        )

        // 已添加的可选参数（数据驱动渲染）
        optionalParamStates.forEach { state ->
            SubModelParameterRow(
                name = stringResource(state.nameResId),
                enabled = state.enabled,
                onEnabledChange = state.onEnabledChange,
                value = state.value,
                onValueChange = state.onValueChange,
                onValueSave = state.onValueSave,
                description = stringResource(state.descriptionResId),
                onRemove = state.onRemove
            )
        }

        // 添加参数按钮和下拉菜单
        Box {
            TextButton(
                onClick = { showAddMenu = true },
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(stringResource(R.string.sub_model_add_parameter))
            }

            DropdownMenu(
                expanded = showAddMenu,
                onDismissRequest = { showAddMenu = false }
            ) {
                addableParameters.forEach { param ->
                    if (param.id !in addedParameters) {
                        DropdownMenuItem(
                            text = {
                                Column {
                                    Text(
                                        text = stringResource(param.nameResId),
                                        style = MaterialTheme.typography.bodyMedium
                                    )
                                    Text(
                                        text = stringResource(param.descriptionResId),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            },
                            onClick = {
                                addedParameters.add(param.id)
                                // 默认启用该参数
                                when (param.id) {
                                    "top_p" -> { topPEnabled = true }
                                    "top_k" -> { topKEnabled = true }
                                    "max_tokens" -> { maxTokensEnabled = true }
                                    "presence_penalty" -> { presencePenaltyEnabled = true }
                                    "frequency_penalty" -> { frequencyPenaltyEnabled = true }
                                    "repetition_penalty" -> { repetitionPenaltyEnabled = true }
                                }
                                saveOverride()
                                showAddMenu = false
                            }
                        )
                    }
                }
                // 如果没有可添加的参数
                if (addableParameters.all { it.id in addedParameters }) {
                    DropdownMenuItem(
                        text = {
                            Text(
                                text = stringResource(R.string.sub_model_no_more_params),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        },
                        onClick = { showAddMenu = false }
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))
    }
}

/**
 * 子模型参数行 - 改进的开关按钮样式
 */
@Composable
private fun SubModelParameterRow(
    name: String,
    enabled: Boolean,
    onEnabledChange: (Boolean) -> Unit,
    value: String,
    onValueChange: (String) -> Unit,
    onValueSave: () -> Unit,
    description: String,
    onRemove: (() -> Unit)?
) {
    // 动画效果
    val backgroundColor by animateColorAsState(
        targetValue = if (enabled)
            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
        else
            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
        label = "backgroundColor"
    )

    val buttonScale by animateFloatAsState(
        targetValue = if (enabled) 1.1f else 1.0f,
        label = "buttonScale"
    )

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        shape = RoundedCornerShape(8.dp),
        color = backgroundColor
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = name,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Medium
                    )
                    if (description.isNotEmpty()) {
                        Text(
                            text = description,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    // 改进的开关按钮
                    IconButton(
                        onClick = { onEnabledChange(!enabled) },
                        modifier = Modifier
                            .size(36.dp)
                            .scale(buttonScale)
                            .clip(CircleShape)
                            .background(
                                if (enabled) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
                            )
                    ) {
                        Icon(
                            imageVector = if (enabled) Icons.Default.Check else Icons.Default.Close,
                            contentDescription = if (enabled) stringResource(R.string.enabled) else stringResource(R.string.disabled),
                            tint = if (enabled) MaterialTheme.colorScheme.onPrimary
                                   else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    // 移除按钮
                    if (onRemove != null) {
                        IconButton(
                            onClick = onRemove,
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = stringResource(R.string.sub_model_remove_parameter),
                                tint = MaterialTheme.colorScheme.error.copy(alpha = 0.7f),
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }

            AnimatedVisibility(visible = enabled) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = value,
                        onValueChange = onValueChange,
                        label = { Text(stringResource(R.string.value)) },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                            keyboardType = androidx.compose.ui.text.input.KeyboardType.Decimal
                        )
                    )
                    IconButton(
                        onClick = onValueSave,
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primaryContainer)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = stringResource(R.string.save),
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
        }
    }
}
