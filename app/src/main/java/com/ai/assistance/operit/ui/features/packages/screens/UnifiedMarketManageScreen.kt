package com.ai.assistance.operit.ui.features.packages.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Store
import androidx.compose.material.icons.filled.Update
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ai.assistance.operit.R
import com.ai.assistance.operit.data.api.MarketV2Entry
import com.ai.assistance.operit.data.api.MarketV2PublisherEntrySummary
import com.ai.assistance.operit.ui.features.github.GitHubLoginWebViewDialog
import com.ai.assistance.operit.ui.features.packages.components.MarketManageDangerActionButton
import com.ai.assistance.operit.ui.features.packages.components.MarketManageDeleteDialog
import com.ai.assistance.operit.ui.features.packages.components.MarketManageItemCard
import com.ai.assistance.operit.ui.features.packages.components.MarketManageLabelChip
import com.ai.assistance.operit.ui.features.packages.components.MarketManagePrimaryActionButton
import com.ai.assistance.operit.ui.features.packages.components.MarketManageReviewReasonChip
import com.ai.assistance.operit.ui.features.packages.components.MarketManageReviewStatusChip
import com.ai.assistance.operit.ui.features.packages.components.MarketManageScaffold
import com.ai.assistance.operit.ui.features.packages.components.MarketManageSecondaryActionButton
import com.ai.assistance.operit.ui.features.packages.market.MarketStatsType
import com.ai.assistance.operit.ui.features.packages.market.PublishArtifactType
import com.ai.assistance.operit.ui.features.packages.market.resolveMarketReviewSnapshot
import com.ai.assistance.operit.ui.features.packages.screens.market.viewmodel.UnifiedMarketManageKind
import com.ai.assistance.operit.ui.features.packages.screens.market.viewmodel.UnifiedMarketManageViewModel

private enum class ManageTypeTab(val kind: UnifiedMarketManageKind, val labelRes: Int) {
    SCRIPT(UnifiedMarketManageKind.SCRIPT, R.string.market_category_type_script),
    PACKAGE(UnifiedMarketManageKind.PACKAGE, R.string.market_category_type_package),
    SKILL(UnifiedMarketManageKind.SKILL, R.string.market_category_type_skill),
    MCP(UnifiedMarketManageKind.MCP, R.string.market_category_type_mcp)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UnifiedMarketManageScreen(
    onNavigateToEditArtifact: (MarketV2Entry) -> Unit,
    onNavigateToEditRepo: (MarketStatsType, MarketV2Entry) -> Unit,
    onNavigateToPublishArtifactVersion: (MarketV2Entry) -> Unit,
    onNavigateToPublishRepoVersion: (MarketStatsType, MarketV2Entry) -> Unit,
    onNavigateToPublishArtifact: () -> Unit,
    onNavigateToPublishRepo: (MarketStatsType) -> Unit,
    onNavigateToDetail: (MarketV2Entry) -> Unit
) {
    val context = LocalContext.current
    var selectedTab by rememberSaveable { mutableStateOf(ManageTypeTab.SCRIPT) }
    val viewModel: UnifiedMarketManageViewModel =
        viewModel(
            key = "market-manage-${selectedTab.name}",
            factory =
                UnifiedMarketManageViewModel.Factory(
                    context.applicationContext,
                    selectedTab.kind
                )
        )

    val isLoggedIn by viewModel.isLoggedIn.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val errorMessage by viewModel.errorMessage.collectAsState()
    val entries by viewModel.entries.collectAsState()
    val hasLoaded by viewModel.hasLoaded.collectAsState()

    var showDeleteDialog by remember { mutableStateOf<MarketV2PublisherEntrySummary?>(null) }
    var showGitHubLogin by remember { mutableStateOf(false) }
    var showPublishDialog by remember { mutableStateOf(false) }

    LaunchedEffect(isLoggedIn, selectedTab) {
        if (isLoggedIn) {
            viewModel.loadEntries()
        } else {
            viewModel.reset()
        }
    }

    val showManageLoading = isLoggedIn && !hasLoaded && entries.isEmpty()
    val showEmptyState = hasLoaded && errorMessage == null && entries.isEmpty()

    MarketManageScaffold(
        isLoggedIn = isLoggedIn,
        isLoading = showManageLoading,
        errorMessage = errorMessage,
        isEmpty = showEmptyState,
        onLogin = { showGitHubLogin = true },
        onPublish = { showPublishDialog = true },
        publishContentDescription = stringResource(R.string.publish_new_artifact),
        loginDescription = stringResource(R.string.need_login_github_manage_artifacts),
        loadingMessage = stringResource(R.string.loading_your_artifacts),
        emptyIcon = Icons.Default.Store,
        emptyTitle = stringResource(R.string.no_published_artifacts_yet),
        emptyDescription = stringResource(R.string.click_button_publish_first_artifact),
        emptyActionLabel = stringResource(R.string.publish_to_market),
        topContent = {
            ScrollableTabRow(
                selectedTabIndex = selectedTab.ordinal,
                edgePadding = 0.dp
            ) {
                ManageTypeTab.entries.forEach { tab ->
                    Tab(
                        selected = selectedTab == tab,
                        onClick = { selectedTab = tab },
                        text = {
                            androidx.compose.material3.Text(
                                text = stringResource(tab.labelRes),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    )
                }
            }
        }
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 120.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(entries, key = { it.id }) { entry ->
                    val review = remember(entry) { entry.resolveMarketReviewSnapshot() }
                    MarketManageItemCard(
                        title = entry.title,
                        description = entry.manageSummaryText(),
                        entryId = entry.id,
                        isOpen = entry.isOpen(),
                        onClick = {
                            viewModel.openEntryDetail(entry, onNavigateToDetail)
                        },
                        supportingContent = {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                MarketManageReviewStatusChip(reviewState = review.state)
                                MarketEntryTypeBadge(entry.type)
                            }
                            if (review.reasons.isNotEmpty()) {
                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    review.reasons.take(2).forEach { reason ->
                                        MarketManageReviewReasonChip(reason = reason)
                                    }
                                }
                            }
                        },
                        actions = {
                            MarketManageSecondaryActionButton(
                                label = stringResource(R.string.edit),
                                icon = Icons.Default.Edit,
                                onClick = {
                                    viewModel.openEntryDetail(entry) { fullEntry ->
                                        when (val type = fullEntry.marketStatsType()) {
                                            MarketStatsType.SCRIPT,
                                            MarketStatsType.PACKAGE -> onNavigateToEditArtifact(fullEntry)
                                            MarketStatsType.SKILL,
                                            MarketStatsType.MCP -> onNavigateToEditRepo(type, fullEntry)
                                            null -> Unit
                                        }
                                    }
                                }
                            )
                            if (entry.isOpen()) {
                                MarketManageSecondaryActionButton(
                                    label = stringResource(R.string.market_publish_new_version),
                                    icon = Icons.Default.Update,
                                    onClick = {
                                        viewModel.openEntryDetail(entry) { fullEntry ->
                                            when (val type = fullEntry.marketStatsType()) {
                                                MarketStatsType.SCRIPT,
                                                MarketStatsType.PACKAGE -> onNavigateToPublishArtifactVersion(fullEntry)
                                                MarketStatsType.SKILL,
                                                MarketStatsType.MCP -> onNavigateToPublishRepoVersion(type, fullEntry)
                                                null -> Unit
                                            }
                                        }
                                    }
                                )
                            }
                            if (entry.isOpen()) {
                                MarketManageDangerActionButton(
                                    label = stringResource(R.string.remove),
                                    icon = Icons.Default.Delete,
                                    onClick = { showDeleteDialog = entry }
                                )
                            } else {
                                MarketManagePrimaryActionButton(
                                    label = stringResource(R.string.republish),
                                    icon = Icons.Default.Refresh,
                                    onClick = { viewModel.resubmitEntry(entry) }
                                )
                            }
                        }
                    )
                }
            }
        }
    }

    showDeleteDialog?.let { entry ->
        MarketManageDeleteDialog(
            text = stringResource(R.string.confirm_remove_artifact_from_market, entry.title),
            onConfirm = {
                viewModel.withdrawEntry(entry)
                showDeleteDialog = null
            },
            onDismiss = { showDeleteDialog = null }
        )
    }

    if (showGitHubLogin) {
        GitHubLoginWebViewDialog(
            onDismissRequest = { showGitHubLogin = false }
        )
    }

    if (showPublishDialog) {
        ManagePublishChooserDialog(
            onDismiss = { showPublishDialog = false },
            onPublishArtifact = {
                showPublishDialog = false
                onNavigateToPublishArtifact()
            },
            onPublishRepo = { type ->
                showPublishDialog = false
                onNavigateToPublishRepo(type)
            }
        )
    }
}

@Composable
private fun ManagePublishChooserDialog(
    onDismiss: () -> Unit,
    onPublishArtifact: () -> Unit,
    onPublishRepo: (MarketStatsType) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.market_section_publish)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                ManagePublishAction(
                    title = stringResource(R.string.market_publish_artifact),
                    onClick = onPublishArtifact
                )
                ManagePublishAction(
                    title = stringResource(R.string.market_publish_skill),
                    onClick = { onPublishRepo(MarketStatsType.SKILL) }
                )
                ManagePublishAction(
                    title = stringResource(R.string.market_publish_mcp),
                    onClick = { onPublishRepo(MarketStatsType.MCP) }
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        }
    )
}

@Composable
private fun ManagePublishAction(
    title: String,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
        shape = MaterialTheme.shapes.medium
    ) {
        Text(
            text = title,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp),
            style = MaterialTheme.typography.bodyLarge
        )
    }
}

@Composable
private fun MarketEntryTypeBadge(type: String) {
    val label =
        when (type.lowercase()) {
            MarketStatsType.SCRIPT.wireValue -> stringResource(R.string.artifact_type_script)
            MarketStatsType.PACKAGE.wireValue -> stringResource(R.string.artifact_type_package)
            MarketStatsType.SKILL.wireValue -> stringResource(R.string.market_category_type_skill)
            MarketStatsType.MCP.wireValue -> stringResource(R.string.market_category_type_mcp)
            else -> type.ifBlank { "-" }
        }

    val isArtifact = PublishArtifactType.fromWireValue(type) != null
    MarketManageLabelChip(
        text = label,
        containerColor =
            if (isArtifact) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.secondaryContainer
            },
        contentColor =
            if (isArtifact) {
                MaterialTheme.colorScheme.onPrimaryContainer
            } else {
                MaterialTheme.colorScheme.onSecondaryContainer
            }
    )
}

private fun MarketV2PublisherEntrySummary.isOpen(): Boolean {
    return stateCode.equals("approved", ignoreCase = true)
}

@Composable
private fun MarketV2PublisherEntrySummary.manageSummaryText(): String {
    val typeText =
        when (type.lowercase()) {
            MarketStatsType.SCRIPT.wireValue -> stringResource(R.string.artifact_type_script)
            MarketStatsType.PACKAGE.wireValue -> stringResource(R.string.artifact_type_package)
            MarketStatsType.SKILL.wireValue -> stringResource(R.string.market_category_type_skill)
            MarketStatsType.MCP.wireValue -> stringResource(R.string.market_category_type_mcp)
            else -> type
        }
    val categoryText =
        categoryId
            .takeIf { it.isNotBlank() }
            ?.let { marketCategoryLabel(it) }
    val updatedText = updatedAt.take(10).takeIf { it.isNotBlank() }
    return listOfNotNull(typeText, categoryText, updatedText).joinToString(" · ")
}

private fun MarketV2Entry.marketStatsType(): MarketStatsType? {
    return MarketStatsType.entries.firstOrNull { it.wireValue == type.lowercase() }
}

private fun MarketV2PublisherEntrySummary.marketStatsType(): MarketStatsType? {
    return MarketStatsType.entries.firstOrNull { it.wireValue == type.lowercase() }
}
