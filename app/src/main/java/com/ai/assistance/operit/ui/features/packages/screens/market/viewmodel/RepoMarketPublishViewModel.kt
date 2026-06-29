package com.ai.assistance.operit.ui.features.packages.screens.market.viewmodel

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import android.widget.Toast
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.ai.assistance.operit.R
import com.ai.assistance.operit.data.api.MarketStatsApiService
import com.ai.assistance.operit.data.api.MarketV2EntryUpdateRequest
import com.ai.assistance.operit.data.api.MarketV2Entry
import com.ai.assistance.operit.data.api.MarketV2PublishRepoVersion
import com.ai.assistance.operit.data.api.MarketV2PublishRequest
import com.ai.assistance.operit.data.api.MarketV2PublishSource
import com.ai.assistance.operit.data.api.MarketV2PublishVersion
import com.ai.assistance.operit.data.preferences.GitHubAuthPreferences
import com.ai.assistance.operit.ui.features.github.GitHubOAuthCoordinator
import com.ai.assistance.operit.ui.features.packages.market.MarketStatsType
import com.ai.assistance.operit.util.AppLogger
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class RepoPublishDraft(
    val title: String = "",
    val description: String = "",
    val detail: String = "",
    val repositoryUrl: String = "",
    val installConfig: String = "",
    val category: String = "",
    val refType: String = "branch",
    val refName: String = "main",
    val manifestPath: String = "",
    val subdir: String = ""
)

class RepoMarketPublishViewModel(
    private val context: Context,
    private val type: MarketStatsType
) : ViewModel() {
    private val marketStatsApiService = MarketStatsApiService()
    val githubAuth: GitHubAuthPreferences = GitHubAuthPreferences.getInstance(context)

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    private val sharedPrefs: SharedPreferences =
        context.getSharedPreferences("${type.wireValue}_publish_draft", Context.MODE_PRIVATE)

    val publishDraft: RepoPublishDraft
        get() = RepoPublishDraft(
            title = sharedPrefs.getString("title", "") ?: "",
            description = sharedPrefs.getString("description", "") ?: "",
            detail = sharedPrefs.getString("detail", "") ?: "",
            repositoryUrl = sharedPrefs.getString("repositoryUrl", "") ?: "",
            installConfig = sharedPrefs.getString("installConfig", "") ?: "",
            category = sharedPrefs.getString("category", "") ?: "",
            refType = sharedPrefs.getString("refType", "branch") ?: "branch",
            refName = sharedPrefs.getString("refName", "main") ?: "main",
            manifestPath = sharedPrefs.getString("manifestPath", "") ?: "",
            subdir = sharedPrefs.getString("subdir", "") ?: ""
        )

    fun parseEntry(entry: MarketV2Entry): RepoPublishDraft {
        return RepoPublishDraft(
            title = entry.title,
            description = entry.description,
            detail = entry.detail,
            repositoryUrl = entry.source?.url.orEmpty(),
            installConfig = entry.latestVersion?.installConfig.orEmpty(),
            category = entry.categoryId,
            refType = "branch",
            refName = "main"
        )
    }

    fun saveDraft(
        title: String,
        description: String,
        detail: String,
        repositoryUrl: String,
        installConfig: String = "",
        category: String = "",
        refType: String = "branch",
        refName: String = "main",
        manifestPath: String = "",
        subdir: String = ""
    ) {
        sharedPrefs.edit().apply {
            putString("title", title)
            putString("description", description)
            putString("detail", detail)
            putString("repositoryUrl", repositoryUrl)
            putString("installConfig", installConfig)
            putString("category", category)
            putString("refType", refType)
            putString("refName", refName)
            putString("manifestPath", manifestPath)
            putString("subdir", subdir)
            apply()
        }
    }

    fun clearDraft() {
        sharedPrefs.edit().clear().apply()
    }

    fun clearError() {
        _errorMessage.value = null
    }

    fun initiateGitHubLogin(context: Context) {
        viewModelScope.launch {
            try {
                val authUrl = GitHubOAuthCoordinator(context).createExternalAuthorizationUrl()
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(authUrl))
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(intent)
            } catch (e: Exception) {
                _errorMessage.value = context.getString(R.string.skillmarket_login_failed, e.message ?: "")
                AppLogger.e(TAG, "Failed to initiate GitHub login", e)
            }
        }
    }

    fun logoutFromGitHub() {
        viewModelScope.launch {
            try {
                githubAuth.logout()
                Toast.makeText(context, context.getString(R.string.skillmarket_logged_out), Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                _errorMessage.value = context.getString(R.string.skillmarket_logout_failed, e.message ?: "")
                AppLogger.e(TAG, "Failed to logout from GitHub", e)
            }
        }
    }

    suspend fun publish(
        title: String,
        description: String,
        detail: String,
        repositoryUrl: String,
        version: String,
        installConfig: String = "",
        category: String = "",
        refType: String = "branch",
        refName: String = "main",
        manifestPath: String = "",
        subdir: String = ""
    ): Result<Unit> {
        return submit(
            entryId = null,
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

    suspend fun publishNewVersion(
        entry: MarketV2Entry,
        version: String,
        installConfig: String = "",
        refType: String = "branch",
        refName: String = "main",
        manifestPath: String = "",
        subdir: String = ""
    ): Result<Unit> {
        validateNewVersion(entry, version)
        return submit(
            entryId = entry.id,
            title = entry.title,
            description = entry.description,
            detail = entry.detail,
            repositoryUrl = entry.source?.url.orEmpty(),
            version = version,
            installConfig = installConfig,
            category = entry.categoryId,
            refType = refType,
            refName = refName,
            manifestPath = manifestPath,
            subdir = subdir
        )
    }

    suspend fun updateEntryMetadata(
        entry: MarketV2Entry,
        title: String,
        description: String,
        detail: String,
        category: String
    ): Result<Unit> {
        if (!githubAuth.isLoggedIn()) {
            return Result.failure(IllegalStateException(loginRequiredMessage()))
        }

        _isLoading.value = true
        _errorMessage.value = null

        return try {
            val request =
                MarketV2EntryUpdateRequest(
                    title = title,
                    description = description,
                    detail = detail,
                    categoryId = category
                )
            marketStatsApiService.updateEntry(entry.id, request).map { Unit }
        } catch (e: Exception) {
            AppLogger.e(TAG, "Failed to update ${type.wireValue} market entry metadata", e)
            Result.failure(e)
        } finally {
            _isLoading.value = false
        }
    }

    private suspend fun submit(
        entryId: String?,
        title: String,
        description: String,
        detail: String,
        repositoryUrl: String,
        version: String,
        installConfig: String,
        category: String,
        refType: String,
        refName: String,
        manifestPath: String,
        subdir: String
    ): Result<Unit> {
        if (!githubAuth.isLoggedIn()) {
            return Result.failure(IllegalStateException(loginRequiredMessage()))
        }

        _isLoading.value = true
        _errorMessage.value = null

        return try {
            val request =
                buildV2PublishRequest(
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
            val result =
                if (entryId == null) {
                    marketStatsApiService.publish(request).map { Unit }
                } else {
                    marketStatsApiService.publishNewVersion(entryId = entryId, request = request).map { Unit }
                }
            result
        } catch (e: Exception) {
            AppLogger.e(TAG, "Failed to submit ${type.wireValue} market entry", e)
            Result.failure(e)
        } finally {
            _isLoading.value = false
        }
    }

    private fun buildV2PublishRequest(
        title: String,
        description: String,
        detail: String,
        repositoryUrl: String,
        version: String,
        installConfig: String,
        category: String,
        refType: String,
        refName: String,
        manifestPath: String,
        subdir: String
    ): MarketV2PublishRequest {
        return MarketV2PublishRequest(
            type = type.wireValue,
            title = title,
            description = description,
            detail = detail,
            categoryId = category,
            version = MarketV2PublishVersion(
                version = version.ifBlank { "1.0.0" },
                formatVersion = "${type.wireValue}_v2",
                minAppVersion = CURRENT_APP_VERSION
            ),
            source = MarketV2PublishSource(url = repositoryUrl),
            repoVersion = MarketV2PublishRepoVersion(
                refType = refType.ifBlank { "branch" },
                refName = refName.ifBlank { "main" },
                manifestPath = manifestPath,
                subdir = subdir,
                installConfig = if (type == MarketStatsType.MCP) installConfig.ifBlank { "{}" } else "{}"
            )
        )
    }

    private fun validateNewVersion(entry: MarketV2Entry, version: String) {
        val requestedVersion = version.trim().removePrefix("v").removePrefix("V").ifBlank { "1.0.0" }
        val currentHighestVersion =
            entry.versions
                .map { it.version }
                .filter(String::isNotBlank)
                .maxWithOrNull(::comparePublishVersions)
                ?: entry.latestVersion?.version
                ?: return
        if (comparePublishVersions(requestedVersion, currentHighestVersion) <= 0) {
            throw IllegalStateException(
                "Version $requestedVersion must be greater than existing version $currentHighestVersion"
            )
        }
    }

    private fun comparePublishVersions(left: String, right: String): Int {
        val leftVersion = parsePublishVersion(left)
        val rightVersion = parsePublishVersion(right)
        val maxSize = maxOf(leftVersion.parts.size, rightVersion.parts.size)
        for (index in 0 until maxSize) {
            val diff = (leftVersion.parts.getOrNull(index) ?: 0) -
                (rightVersion.parts.getOrNull(index) ?: 0)
            if (diff != 0) return diff
        }
        if (leftVersion.suffix == rightVersion.suffix) return 0
        if (leftVersion.suffix.isBlank()) return 1
        if (rightVersion.suffix.isBlank()) return -1
        return leftVersion.suffix.compareTo(rightVersion.suffix)
    }

    private fun parsePublishVersion(value: String): PublishVersionParts {
        val normalized = value.trim().removePrefix("v").removePrefix("V")
        val core = normalized.substringBefore("-").substringBefore("+")
        val suffix =
            normalized
                .substringAfter("-", "")
                .substringBefore("+")
        val parts =
            core.split(".")
                .filter(String::isNotBlank)
                .map { it.toIntOrNull() ?: 0 }
        return PublishVersionParts(parts = parts, suffix = suffix)
    }

    private data class PublishVersionParts(
        val parts: List<Int>,
        val suffix: String
    )

    private fun loginRequiredMessage(): String =
        when (type) {
            MarketStatsType.SKILL -> context.getString(R.string.skill_publish_login_required)
            MarketStatsType.MCP -> "GitHub 登录后才能发布 MCP。"
            else -> context.getString(R.string.skillmarket_github_login_required)
        }

    class Factory(
        private val context: Context,
        private val type: MarketStatsType
    ) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(RepoMarketPublishViewModel::class.java)) {
                @Suppress("UNCHECKED_CAST")
                return RepoMarketPublishViewModel(context, type) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class")
        }
    }

    companion object {
        private const val TAG = "RepoMarketPublishViewModel"
        private const val CURRENT_APP_VERSION = "1.11.0+5"
    }
}
