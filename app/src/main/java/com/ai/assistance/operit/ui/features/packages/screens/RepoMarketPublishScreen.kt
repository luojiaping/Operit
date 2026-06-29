package com.ai.assistance.operit.ui.features.packages.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ai.assistance.operit.R
import com.ai.assistance.operit.data.api.MarketStatsApiService
import com.ai.assistance.operit.data.api.MarketV2Entry
import com.ai.assistance.operit.data.api.MarketV2ManifestCategory
import com.ai.assistance.operit.ui.features.packages.market.MarketStatsType
import com.ai.assistance.operit.ui.features.packages.screens.market.viewmodel.RepoMarketPublishViewModel
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RepoMarketPublishScreen(
    type: MarketStatsType,
    onNavigateBack: () -> Unit,
    editingEntry: MarketV2Entry? = null,
    publishVersionOnly: Boolean = false
) {
    require(type == MarketStatsType.SKILL || type == MarketStatsType.MCP) {
        "Repo market publish only supports skill and mcp"
    }

    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val scrollState = rememberScrollState()
    val viewModel: RepoMarketPublishViewModel =
        viewModel(
            key = "repo-publish-${type.wireValue}-${editingEntry?.id.orEmpty()}",
            factory = RepoMarketPublishViewModel.Factory(context.applicationContext, type)
        )

    val isEditMode = editingEntry != null
    val isVersionMode = isEditMode && publishVersionOnly
    val initialDraft =
        remember(editingEntry?.id, type) {
            editingEntry?.let(viewModel::parseEntry) ?: viewModel.publishDraft
        }

    var title by remember(initialDraft) { mutableStateOf(initialDraft.title) }
    var description by remember(initialDraft) { mutableStateOf(initialDraft.description) }
    var detail by remember(initialDraft) { mutableStateOf(initialDraft.detail) }
    var repositoryUrl by remember(initialDraft) { mutableStateOf(initialDraft.repositoryUrl) }
    var installConfig by remember(initialDraft) { mutableStateOf(initialDraft.installConfig) }
    var category by remember(initialDraft) { mutableStateOf(initialDraft.category) }
    var refType by remember(initialDraft) { mutableStateOf(initialDraft.refType) }
    var refName by remember(initialDraft) { mutableStateOf(initialDraft.refName) }
    var manifestPath by remember(initialDraft) { mutableStateOf(initialDraft.manifestPath) }
    var subdir by remember(initialDraft) { mutableStateOf(initialDraft.subdir) }
    var version by remember(editingEntry?.id) {
        mutableStateOf(if (isEditMode) "" else "1.0.0")
    }

    var isPublishing by remember { mutableStateOf(false) }
    var successAction by remember { mutableStateOf<RepoPublishSuccessAction?>(null) }
    var showConfirmationDialog by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var categories by remember { mutableStateOf<List<MarketV2ManifestCategory>>(emptyList()) }

    if (!isEditMode) {
        LaunchedEffect(title, description, detail, repositoryUrl, installConfig, category, refType, refName, manifestPath, subdir) {
            viewModel.saveDraft(
                title = title,
                description = description,
                detail = detail,
                repositoryUrl = repositoryUrl,
                installConfig = installConfig,
                category = category,
                refType = refType,
                refName = refName,
                manifestPath = manifestPath,
                subdir = subdir
            )
        }
    }

    LaunchedEffect(Unit) {
        MarketStatsApiService().getManifest().fold(
            onSuccess = { manifest ->
                categories = manifest.categories.filter { it.id.isNotBlank() }
            },
            onFailure = { error ->
                errorMessage = error.message ?: context.getString(R.string.publish_failed_check_network_repo)
            }
        )
    }

    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .padding(16.dp)
                .verticalScroll(scrollState)
    ) {
        RepoPublishInfoCard(type = type, isEditMode = isEditMode, isVersionMode = isVersionMode)

        OutlinedTextField(
            value = title,
            onValueChange = {
                if (!isVersionMode) {
                    title = it
                }
            },
            label = { Text(repoNameLabel(type)) },
            modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
            singleLine = true,
            readOnly = isVersionMode,
            isError = title.isBlank()
        )

        OutlinedTextField(
            value = description,
            onValueChange = {
                if (!isVersionMode) {
                    description = it
                }
            },
            label = { Text(repoDescriptionLabel(type)) },
            modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
            minLines = 3,
            maxLines = 6,
            readOnly = isVersionMode,
            isError = description.isBlank()
        )

        OutlinedTextField(
            value = detail,
            onValueChange = {
                if (!isVersionMode) {
                    detail = it
                }
            },
            label = { Text(stringResource(R.string.market_detail_section_details)) },
            modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
            minLines = 4,
            maxLines = 10,
            readOnly = isVersionMode
        )

        OutlinedTextField(
            value = repositoryUrl,
            onValueChange = {
                if (!isEditMode) {
                    repositoryUrl = it
                }
            },
            label = { Text(stringResource(R.string.github_repo_address_required)) },
            modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
            singleLine = true,
            readOnly = isEditMode,
            placeholder = { Text("https://github.com/username/repo") },
            isError = repositoryUrl.isBlank()
        )

        RepoCategoryDropdown(
            selectedCategory = category,
            categories = categories,
            onCategorySelected = {
                if (!isVersionMode) {
                    category = it
                }
            },
            enabled = !isVersionMode,
            modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)
        )

        if (!isEditMode || isVersionMode) {
            OutlinedTextField(
                value = refType,
                onValueChange = { refType = it },
                label = { Text(stringResource(R.string.repo_publish_ref_type)) },
                modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                singleLine = true,
                placeholder = { Text("branch / tag") },
                isError = refType.isBlank()
            )

            OutlinedTextField(
                value = refName,
                onValueChange = { refName = it },
                label = { Text(stringResource(R.string.repo_publish_ref_name)) },
                modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                singleLine = true,
                placeholder = { Text("main / v1.0.0") },
                isError = refName.isBlank()
            )

            OutlinedTextField(
                value = manifestPath,
                onValueChange = { manifestPath = it },
                label = { Text(stringResource(R.string.repo_publish_manifest_path)) },
                modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                singleLine = true,
                placeholder = { Text(if (type == MarketStatsType.MCP) "mcp.json" else "skill.json") }
            )

            OutlinedTextField(
                value = subdir,
                onValueChange = { subdir = it },
                label = { Text(stringResource(R.string.repo_publish_subdir)) },
                modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                singleLine = true
            )

            if (type == MarketStatsType.MCP) {
                OutlinedTextField(
                    value = installConfig,
                    onValueChange = { installConfig = it },
                    label = { Text(stringResource(R.string.install_config)) },
                    modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                    placeholder = { Text(stringResource(R.string.install_config_example)) },
                    minLines = 3,
                    maxLines = 8,
                    leadingIcon = {
                        Icon(Icons.Default.Terminal, contentDescription = stringResource(R.string.install_config))
                    },
                    supportingText = { Text(stringResource(R.string.install_config_optional_description)) }
                )
            }

            OutlinedTextField(
                value = version,
                onValueChange = { version = it },
                label = { Text(stringResource(if (isVersionMode) R.string.repo_publish_new_version_label else R.string.version_label)) },
                modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp),
                singleLine = true,
                isError = version.isBlank()
            )
        }

        errorMessage?.let { error ->
            Card(
                modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
            ) {
                Text(
                    text = error,
                    modifier = Modifier.padding(16.dp),
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }

        if (isEditMode && !isVersionMode && editingEntry != null) {
            Button(
                onClick = {
                    if (title.isBlank() || description.isBlank() || repositoryUrl.isBlank() || category.isBlank()) {
                        errorMessage = context.getString(R.string.please_fill_all_required_fields)
                        return@Button
                    }
                    scope.launch {
                        isPublishing = true
                        errorMessage = null
                        try {
                            viewModel.updateEntryMetadata(
                                entry = editingEntry,
                                title = title,
                                description = description,
                                detail = detail,
                                category = category
                            ).fold(
                                onSuccess = { successAction = RepoPublishSuccessAction.METADATA },
                                onFailure = { error ->
                                    errorMessage = error.message ?: context.getString(R.string.publish_failed_check_network_repo)
                                }
                            )
                        } catch (error: Exception) {
                            errorMessage = context.getString(R.string.publish_failed_with_error, error.message ?: "")
                        } finally {
                            isPublishing = false
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth().height(48.dp).padding(bottom = 8.dp),
                enabled = !isPublishing && title.isNotBlank() && description.isNotBlank() && repositoryUrl.isNotBlank() && category.isNotBlank()
            ) {
                if (isPublishing) {
                    CircularProgressIndicator(
                        modifier = Modifier.padding(end = 8.dp),
                        color = MaterialTheme.colorScheme.onPrimary,
                        strokeWidth = 2.dp
                    )
                    Text(stringResource(R.string.updating_progress))
                } else {
                    Text(stringResource(R.string.update_plugin))
                }
            }
        }

        if (!isEditMode || isVersionMode) {
            Button(
                onClick = {
                    if (title.isBlank() || description.isBlank() || repositoryUrl.isBlank() || version.isBlank() || category.isBlank() || refType.isBlank() || refName.isBlank()) {
                        errorMessage = context.getString(R.string.please_fill_all_required_fields)
                        return@Button
                    }
                    showConfirmationDialog = true
                },
                modifier = Modifier.fillMaxWidth().height(48.dp),
                enabled = !isPublishing && title.isNotBlank() && description.isNotBlank() && repositoryUrl.isNotBlank() && version.isNotBlank() && category.isNotBlank() && refType.isNotBlank() && refName.isNotBlank()
            ) {
                if (isPublishing) {
                    CircularProgressIndicator(
                        modifier = Modifier.padding(end = 8.dp),
                        color = MaterialTheme.colorScheme.onPrimary,
                        strokeWidth = 2.dp
                    )
                    Text(stringResource(if (isVersionMode) R.string.updating_progress else R.string.publishing_progress))
                } else {
                    Text(stringResource(if (isVersionMode) R.string.artifact_publish_publish_update_version else R.string.publish_to_market))
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        OutlinedButton(
            onClick = onNavigateBack,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(stringResource(R.string.cancel))
        }
    }

    if (showConfirmationDialog) {
        RepoPublishConfirmDialog(
            type = type,
            isEditMode = isEditMode,
            isVersionMode = isVersionMode,
            title = title,
            description = description,
            detail = detail,
            repositoryUrl = repositoryUrl,
            category = category,
            version = version,
            refType = refType,
            refName = refName,
            manifestPath = manifestPath,
            subdir = subdir,
            installConfig = installConfig,
            onDismiss = { showConfirmationDialog = false },
            onConfirm = {
                showConfirmationDialog = false
                scope.launch {
                    isPublishing = true
                    errorMessage = null
                    try {
                        val result =
                            if (isVersionMode && editingEntry != null) {
                                viewModel.publishNewVersion(
                                    entry = editingEntry,
                                    version = version,
                                    installConfig = installConfig,
                                    refType = refType,
                                    refName = refName,
                                    manifestPath = manifestPath,
                                    subdir = subdir
                                )
                            } else {
                                viewModel.publish(
                                    title = title,
                                    description = description,
                                    detail = detail,
                                    repositoryUrl = repositoryUrl,
                                    version = version,
                                    installConfig = installConfig,
                                    category = category,
                                    refType = refType,
                                    refName = refName,
                                    manifestPath = manifestPath,
                                    subdir = subdir
                                )
                            }

                        result.fold(
                            onSuccess = {
                                if (!isEditMode) viewModel.clearDraft()
                                successAction = if (isVersionMode) RepoPublishSuccessAction.VERSION else RepoPublishSuccessAction.PUBLISH
                            },
                            onFailure = { error ->
                                errorMessage =
                                    error.message
                                        ?: context.getString(R.string.publish_failed_check_network_repo)
                            }
                        )
                    } catch (error: Exception) {
                        errorMessage =
                            context.getString(R.string.publish_failed_with_error, error.message ?: "")
                    } finally {
                        isPublishing = false
                    }
                }
            }
        )
    }

    successAction?.let { action ->
        AlertDialog(
            onDismissRequest = { },
            title = { Text(stringResource(repoSuccessTitle(action))) },
            text = { Text(repoSuccessMessage(type = type, action = action)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        successAction = null
                        onNavigateBack()
                    }
                ) {
                    Text(stringResource(R.string.confirm))
                }
            }
        )
    }
}

@Composable
private fun RepoCategoryDropdown(
    selectedCategory: String,
    categories: List<MarketV2ManifestCategory>,
    onCategorySelected: (String) -> Unit,
    enabled: Boolean,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }
    val selectedLabel =
        selectedCategory
            .takeIf { it.isNotBlank() }
            ?.let { selected -> categories.firstOrNull { it.id == selected }?.name?.ifBlank { selected } ?: selected }
            .orEmpty()

    Box(modifier = modifier) {
        OutlinedTextField(
            value = selectedLabel,
            onValueChange = {},
            label = { Text(stringResource(R.string.market_detail_category_label)) },
            modifier = Modifier.fillMaxWidth(),
            enabled = enabled,
            readOnly = true,
            placeholder = { Text(stringResource(R.string.select_category)) },
            trailingIcon = {
                IconButton(
                    onClick = { if (enabled) expanded = true },
                    enabled = enabled
                ) {
                    Icon(Icons.Default.ArrowDropDown, contentDescription = null)
                }
            },
            isError = enabled && selectedCategory.isBlank()
        )
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            categories.forEach { category ->
                DropdownMenuItem(
                    text = { Text(category.name.ifBlank { category.id }) },
                    onClick = {
                        onCategorySelected(category.id)
                        expanded = false
                    }
                )
            }
        }
    }
}

@Composable
private fun RepoPublishInfoCard(type: MarketStatsType, isEditMode: Boolean, isVersionMode: Boolean) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f))
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.Top
        ) {
            Icon(
                Icons.Default.Info,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(end = 12.dp, top = 2.dp)
            )
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text =
                        stringResource(
                            when {
                                isVersionMode -> R.string.market_publish_new_version
                                isEditMode -> R.string.edit_description
                                else -> R.string.publish_description
                            }
                        ),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text =
                        if (isVersionMode) {
                            stringResource(R.string.market_publish_new_version_locked_fields_description)
                        } else if (type == MarketStatsType.SKILL) {
                            stringResource(if (isEditMode) R.string.skill_edit_info_description else R.string.skill_publish_info_description)
                        } else {
                            stringResource(if (isEditMode) R.string.edit_plugin_info_description else R.string.publish_plugin_info_description)
                        },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }
}

@Composable
private fun RepoPublishConfirmDialog(
    type: MarketStatsType,
    isEditMode: Boolean,
    isVersionMode: Boolean,
    title: String,
    description: String,
    detail: String,
    repositoryUrl: String,
    category: String,
    version: String,
    refType: String,
    refName: String,
    manifestPath: String,
    subdir: String,
    installConfig: String,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                stringResource(
                    when {
                        isVersionMode -> R.string.artifact_publish_confirm_publish_update_version
                        isEditMode -> R.string.confirm_update
                        else -> R.string.confirm_publish
                    }
                )
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.please_check_submitted_info))
                Spacer(modifier = Modifier.height(8.dp))
                Text(stringResource(R.string.name_colon, title), style = MaterialTheme.typography.bodyMedium)
                Text(stringResource(R.string.description_colon, description), style = MaterialTheme.typography.bodyMedium)
                if (detail.isNotBlank()) {
                    Text(stringResource(R.string.detail_colon, detail), style = MaterialTheme.typography.bodyMedium)
                }
                if (category.isNotBlank()) {
                    Text(
                        stringResource(R.string.market_detail_category_label) + ": " + category,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
                Text(stringResource(R.string.repository_colon, repositoryUrl), style = MaterialTheme.typography.bodyMedium)
                Text(stringResource(R.string.version_colon, version), style = MaterialTheme.typography.bodyMedium)
                Text(stringResource(R.string.repo_publish_ref_colon, refType, refName), style = MaterialTheme.typography.bodyMedium)
                if (manifestPath.isNotBlank()) {
                    Text(stringResource(R.string.repo_publish_manifest_path_colon, manifestPath), style = MaterialTheme.typography.bodyMedium)
                }
                if (subdir.isNotBlank()) {
                    Text(stringResource(R.string.repo_publish_subdir_colon, subdir), style = MaterialTheme.typography.bodyMedium)
                }
                if (type == MarketStatsType.MCP && installConfig.isNotBlank()) {
                    Text(stringResource(R.string.install_config_colon, installConfig), style = MaterialTheme.typography.bodyMedium)
                }
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text =
                        stringResource(
                            if (type == MarketStatsType.SKILL) {
                                R.string.confirm_skill_git_import_deployment
                            } else {
                                R.string.confirm_mcp_git_import_deployment
                            }
                        ),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(stringResource(if (isVersionMode) R.string.artifact_publish_publish_update_version else if (isEditMode) R.string.confirm_update else R.string.confirm_publish))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        }
    )
}

@Composable
private fun repoNameLabel(type: MarketStatsType): String =
    stringResource(if (type == MarketStatsType.SKILL) R.string.skill_name_required else R.string.plugin_name_required)

@Composable
private fun repoDescriptionLabel(type: MarketStatsType): String =
    stringResource(
        if (type == MarketStatsType.SKILL) {
            R.string.skill_description_required
        } else {
            R.string.plugin_description_required
        }
    )

private enum class RepoPublishSuccessAction {
    PUBLISH,
    METADATA,
    VERSION
}

private fun repoSuccessTitle(action: RepoPublishSuccessAction): Int =
    when (action) {
        RepoPublishSuccessAction.PUBLISH -> R.string.publish_success
        RepoPublishSuccessAction.METADATA -> R.string.update_success
        RepoPublishSuccessAction.VERSION -> R.string.update_success
    }

@Composable
private fun repoSuccessMessage(type: MarketStatsType, action: RepoPublishSuccessAction): String =
    stringResource(
        when (action) {
            RepoPublishSuccessAction.METADATA -> R.string.repo_publish_metadata_update_success_message
            RepoPublishSuccessAction.VERSION -> R.string.repo_publish_new_version_success_message
            RepoPublishSuccessAction.PUBLISH ->
                when (type) {
                    MarketStatsType.SKILL -> R.string.skill_publish_success_message
                    MarketStatsType.MCP -> R.string.mcp_plugin_publish_success_message
                    else -> R.string.publish_success
                }
        }
    )
