package com.ai.assistance.operit.ui.features.settings.sections

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ai.assistance.operit.R
import com.ai.assistance.operit.data.model.ModelConfigData
import com.ai.assistance.operit.data.model.ModelOverrideConfig
import com.ai.assistance.operit.data.model.getModelList
import com.ai.assistance.operit.data.preferences.ModelConfigManager
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
                                showNotification("已重置 $modelName 的自定义参数")
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
                            imageVector = Icons.Default.Delete,
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
    var maxTokensEnabled by remember(modelName) {
        mutableStateOf(existingOverride?.maxTokensEnabled ?: config.maxTokensEnabled)
    }
    var maxTokens by remember(modelName) {
        mutableStateOf((existingOverride?.maxTokens ?: config.maxTokens).toString())
    }
    var temperatureEnabled by remember(modelName) {
        mutableStateOf(existingOverride?.temperatureEnabled ?: config.temperatureEnabled)
    }
    var temperature by remember(modelName) {
        mutableStateOf((existingOverride?.temperature ?: config.temperature).toString())
    }
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

    // 自定义参数和请求头
    var customParameters by remember(modelName) {
        mutableStateOf(existingOverride?.customParameters ?: config.customParameters)
    }
    var customHeaders by remember(modelName) {
        mutableStateOf(existingOverride?.customHeaders ?: config.customHeaders)
    }

    // 保存函数
    fun saveOverride() {
        scope.launch {
            try {
                val override = ModelOverrideConfig(
                    maxTokensEnabled = maxTokensEnabled,
                    temperatureEnabled = temperatureEnabled,
                    topPEnabled = topPEnabled,
                    topKEnabled = topKEnabled,
                    presencePenaltyEnabled = presencePenaltyEnabled,
                    frequencyPenaltyEnabled = frequencyPenaltyEnabled,
                    repetitionPenaltyEnabled = repetitionPenaltyEnabled,
                    maxTokens = maxTokens.toIntOrNull(),
                    temperature = temperature.toFloatOrNull(),
                    topP = topP.toFloatOrNull(),
                    topK = topK.toIntOrNull(),
                    presencePenalty = presencePenalty.toFloatOrNull(),
                    frequencyPenalty = frequencyPenalty.toFloatOrNull(),
                    repetitionPenalty = repetitionPenalty.toFloatOrNull(),
                    customParameters = customParameters,
                    customHeaders = customHeaders
                )
                configManager.updateModelOverride(config.id, modelName, override)
                showNotification("已保存 $modelName 的自定义参数")
            } catch (e: Exception) {
                showNotification("保存失败: ${e.message}")
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Divider(modifier = Modifier.padding(vertical = 4.dp))

        Text(
            text = stringResource(R.string.sub_model_parameter_settings, modelName),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Medium
        )

        // Temperature
        SubModelParameterRow(
            name = stringResource(R.string.temperature_name),
            enabled = temperatureEnabled,
            onEnabledChange = { temperatureEnabled = it; saveOverride() },
            value = temperature,
            onValueChange = { temperature = it },
            onValueSave = { saveOverride() },
            description = stringResource(R.string.temperature_description)
        )

        // Top P
        SubModelParameterRow(
            name = stringResource(R.string.top_p_name),
            enabled = topPEnabled,
            onEnabledChange = { topPEnabled = it; saveOverride() },
            value = topP,
            onValueChange = { topP = it },
            onValueSave = { saveOverride() },
            description = stringResource(R.string.top_p_description)
        )

        // Top K
        SubModelParameterRow(
            name = stringResource(R.string.top_k_name),
            enabled = topKEnabled,
            onEnabledChange = { topKEnabled = it; saveOverride() },
            value = topK,
            onValueChange = { topK = it },
            onValueSave = { saveOverride() },
            description = stringResource(R.string.top_k_description)
        )

        // Max Tokens
        SubModelParameterRow(
            name = stringResource(R.string.max_tokens_name),
            enabled = maxTokensEnabled,
            onEnabledChange = { maxTokensEnabled = it; saveOverride() },
            value = maxTokens,
            onValueChange = { maxTokens = it },
            onValueSave = { saveOverride() },
            description = stringResource(R.string.max_tokens_description)
        )

        // Presence Penalty
        SubModelParameterRow(
            name = stringResource(R.string.presence_penalty_name),
            enabled = presencePenaltyEnabled,
            onEnabledChange = { presencePenaltyEnabled = it; saveOverride() },
            value = presencePenalty,
            onValueChange = { presencePenalty = it },
            onValueSave = { saveOverride() },
            description = stringResource(R.string.presence_penalty_description)
        )

        // Frequency Penalty
        SubModelParameterRow(
            name = stringResource(R.string.frequency_penalty_name),
            enabled = frequencyPenaltyEnabled,
            onEnabledChange = { frequencyPenaltyEnabled = it; saveOverride() },
            value = frequencyPenalty,
            onValueChange = { frequencyPenalty = it },
            onValueSave = { saveOverride() },
            description = stringResource(R.string.frequency_penalty_description)
        )

        // Repetition Penalty
        SubModelParameterRow(
            name = stringResource(R.string.repetition_penalty_name),
            enabled = repetitionPenaltyEnabled,
            onEnabledChange = { repetitionPenaltyEnabled = it; saveOverride() },
            value = repetitionPenalty,
            onValueChange = { repetitionPenalty = it },
            onValueSave = { saveOverride() },
            description = stringResource(R.string.repetition_penalty_description)
        )

        Spacer(modifier = Modifier.height(8.dp))
    }
}

/**
 * 子模型参数行
 */
@Composable
private fun SubModelParameterRow(
    name: String,
    enabled: Boolean,
    onEnabledChange: (Boolean) -> Unit,
    value: String,
    onValueChange: (String) -> Unit,
    onValueSave: () -> Unit,
    description: String
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 启用开关
        Switch(
            checked = enabled,
            onCheckedChange = onEnabledChange,
            modifier = Modifier.size(32.dp)
        )

        Spacer(modifier = Modifier.width(8.dp))

        // 参数名称
        Text(
            text = name,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.width(80.dp)
        )

        // 参数值输入
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            enabled = enabled,
            modifier = Modifier
                .weight(1f)
                .height(40.dp),
            textStyle = MaterialTheme.typography.bodySmall,
            singleLine = true,
            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                keyboardType = androidx.compose.ui.text.input.KeyboardType.Decimal
            ),
            trailingIcon = {
                IconButton(
                    onClick = onValueSave,
                    modifier = Modifier.size(24.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = stringResource(R.string.save),
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        )
    }
}
