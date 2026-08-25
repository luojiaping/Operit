package com.ai.assistance.operit.plugins.toolpkg

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.ai.assistance.operit.data.model.ApiProviderType
import com.ai.assistance.operit.data.model.TokenUsageRecordEntity
import com.ai.assistance.operit.data.preferences.ModelConfigManager
import com.ai.assistance.operit.data.stats.TokenStatsQueryParams
import com.ai.assistance.operit.data.stats.TokenStatsQueryService
import com.ai.assistance.operit.data.stats.TokenStatsTimeRange
import com.ai.assistance.operit.data.stats.TokenStatsTotals
import com.ai.assistance.operit.data.stats.TokenCostCalculator
import com.ai.assistance.operit.data.stats.TokenCostCurrency
import com.ai.assistance.operit.data.stats.TokenPriceResolver
import com.ai.assistance.operit.data.dao.TokenUsageModelAggregateRow
import com.ai.assistance.operit.data.collects.PricingCurrency
import com.ai.assistance.operit.ui.features.token.network.DeepseekApiConstants
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.json.JSONArray
import org.json.JSONObject

internal object DeepSeekWhaleHostService {
    const val ACCOUNTS_CAPABILITY = "deepseek.accounts.v2"
    const val BALANCE_CAPABILITY = "deepseek.balance.v2"
    const val CACHED_SNAPSHOT_CAPABILITY = "deepseek.cached_snapshot.v2"
    const val PLATFORM_STATUS_CAPABILITY = "deepseek.platform_status.v2"
    const val PLATFORM_SET_CAPABILITY = "deepseek.platform_set.v2"
    const val PLATFORM_USAGE_CAPABILITY = "deepseek.platform_usage.v2"
    const val STATS_CAPABILITY = "deepseek.stats.v2"

    private const val BALANCE_PREFS = "deepseek_whale_balance"
    private const val PLATFORM_PREFS = "deepseek_whale_platform_credentials"
    private const val PLATFORM_TOKEN_KEY = "platform_token"
    private const val CURRENCY_CNY = "CNY"
    private const val MILLION = 1_000_000L
    private val WEEKEND_VALLEY_FROM_SEC = Instant.parse("2026-08-22T16:00:00Z").epochSecond

    private val registered = AtomicBoolean(false)
    private val httpClient = OkHttpClient()
    private val ledgerLock = Any()

    fun register() {
        if (!registered.compareAndSet(false, true)) {
            return
        }
        ToolPkgHostBridge.register(ACCOUNTS_CAPABILITY, Handler(::handleAccounts))
        ToolPkgHostBridge.register(BALANCE_CAPABILITY, Handler(::handleBalance))
        ToolPkgHostBridge.register(CACHED_SNAPSHOT_CAPABILITY, Handler(::handleCachedSnapshot))
        ToolPkgHostBridge.register(PLATFORM_STATUS_CAPABILITY, Handler(::handlePlatformStatus))
        ToolPkgHostBridge.register(PLATFORM_SET_CAPABILITY, Handler(::handlePlatformSet))
        ToolPkgHostBridge.register(PLATFORM_USAGE_CAPABILITY, Handler(::handlePlatformUsage))
        ToolPkgHostBridge.register(STATS_CAPABILITY, Handler(::handleStats))
    }

    private fun handleAccounts(request: ToolPkgHostBridge.Request): JSONObject {
        val manager = ModelConfigManager(request.context)
        val summaries = runBlocking(Dispatchers.IO) { manager.getAllConfigSummaries() }
        val accounts = JSONArray()
        summaries
            .filter { it.apiProviderType == ApiProviderType.DEEPSEEK }
            .forEach { summary ->
                val config =
                    runBlocking(Dispatchers.IO) { manager.getModelConfig(summary.id) }
                        ?: error("DeepSeek model config not found: ${summary.id}")
                val keys = JSONArray()
                if (config.useMultipleApiKeys) {
                    config.apiKeyPool.forEach { key ->
                        keys.put(
                            credentialView(
                                id = key.id,
                                name = key.name,
                                enabled = key.isEnabled,
                                status = key.availabilityStatus.name
                            )
                        )
                    }
                } else {
                    keys.put(
                        credentialView(
                            id = "primary",
                            name = "Primary",
                            enabled = config.apiKey.isNotBlank(),
                            status = if (config.apiKey.isBlank()) "UNCONFIGURED" else "AVAILABLE"
                        )
                    )
                }
                accounts.put(
                    JSONObject()
                        .put("configId", config.id)
                        .put("name", config.name)
                        .put("modelName", config.modelName)
                        .put("keys", keys)
                )
            }
        return state("ready").put("accounts", accounts)
    }

    private fun handleBalance(request: ToolPkgHostBridge.Request): JSONObject {
        val credential = resolveCredential(request)
        val currency = request.payload.optString("currency").trim().ifBlank { CURRENCY_CNY }
        val body = executeJsonGet(
            url = DeepseekApiConstants.DEEPSEEK_BALANCE_URL,
            headers = mapOf("Authorization" to "Bearer ${credential.apiKey}")
        )
        val balanceInfos = body.optJSONArray("balance_infos")
            ?: error("DeepSeek balance response has no balance_infos")
        val balances = JSONArray()
        var selected: JSONObject? = null
        for (index in 0 until balanceInfos.length()) {
            val info = balanceInfos.optJSONObject(index)
                ?: error("DeepSeek balance response contains an invalid balance item")
            val itemCurrency = info.optString("currency").trim()
            val amount = info.optString("total_balance").trim()
            if (itemCurrency.isBlank() || amount.isBlank()) {
                error("DeepSeek balance response contains an incomplete balance item")
            }
            BigDecimal(amount)
            balances.put(
                JSONObject()
                    .put("currency", itemCurrency)
                    .put("totalBalance", amount)
            )
            if (itemCurrency == currency) {
                selected = info
            }
        }
        val selectedInfo = selected ?: error("DeepSeek balance currency is unavailable: $currency")
        val totalBalance = selectedInfo.optString("total_balance").trim()
        val zone = requestZone(request.payload)
        val date = requestDate(request.payload, zone)
        val ledger = recordLedgerUsage(
            context = request.context,
            accountId = credential.accountId,
            currency = currency,
            date = date,
            currentBalance = BigDecimal(totalBalance)
        )
        val latest = latestDeepSeekRecord(request.context)
        val requestedUsageMode = request.payload.optString("usageMode").trim().lowercase()
        var platformUsage: PlatformUsage? = null
        var platformUsageState = "disabled"
        if (requestedUsageMode == "platform") {
            val platformToken = platformPreferences(request.context)
                .getString(PLATFORM_TOKEN_KEY, null)
            if (platformToken == null) {
                platformUsageState = "credential_required"
            } else {
                try {
                    platformUsage = queryPlatformUsage(request.payload, platformToken)
                    platformUsageState = "ready"
                } catch (error: Exception) {
                    AppLogger.e("DeepSeekWhaleHostService", "Platform usage refresh failed", error)
                    platformUsageState = "error"
                }
            }
        }
        val displayedUsage =
            if (requestedUsageMode == "platform") {
                platformUsage?.amount ?: JSONObject.NULL
            } else {
                ledger.optString("todayUsage")
            }
        val result = state(ledger.optString("state"))
            .put("accountId", credential.accountId)
            .put("configId", credential.configId)
            .put("keyId", credential.keyId)
            .put("currency", currency)
            .put("totalBalance", totalBalance)
            .put("balances", balances)
            .put("todayUsage", displayedUsage)
            .put("baselineCaptured", ledger.optBoolean("baselineCaptured"))
            .put("date", date.toString())
            .put("updatedAtMs", System.currentTimeMillis().toString())
            .put("latestTurn", serializeLatestRecord(latest))
            .put("usageMode", if (requestedUsageMode == "platform") "platform" else "ledger")
            .put("usageState", platformUsageState)
        balancePreferences(request.context)
            .edit()
            .putString("cache:${credential.accountId}", result.toString())
            .apply()
        return result
    }

    private fun handleCachedSnapshot(request: ToolPkgHostBridge.Request): JSONObject {
        val configId = request.payload.optString("configId").trim()
        val keyId = request.payload.optString("keyId").trim()
        require(configId.isNotBlank()) { "configId is required" }
        require(keyId.isNotBlank()) { "keyId is required" }
        val cached =
            balancePreferences(request.context)
                .getString("cache:$configId:$keyId", null)
                ?: return state("empty")
        return try {
            JSONObject(cached)
        } catch (error: Exception) {
            throw IllegalStateException("Stored DeepSeek balance snapshot is invalid", error)
        }
    }

    private fun handlePlatformStatus(request: ToolPkgHostBridge.Request): JSONObject {
        val preferences = platformPreferences(request.context)
        return state(if (preferences.contains(PLATFORM_TOKEN_KEY)) "ready" else "credential_required")
            .put("configured", preferences.contains(PLATFORM_TOKEN_KEY))
    }

    private fun handlePlatformSet(request: ToolPkgHostBridge.Request): JSONObject {
        val token = request.payload.optString("token").trim()
        require(token.isNotBlank()) { "DeepSeek platform token is required" }
        platformPreferences(request.context)
            .edit()
            .putString(PLATFORM_TOKEN_KEY, token.removePrefix("Bearer ").trim())
            .apply()
        return state("ready").put("configured", true)
    }

    private fun handlePlatformUsage(request: ToolPkgHostBridge.Request): JSONObject {
        val preferences = platformPreferences(request.context)
        val token = preferences.getString(PLATFORM_TOKEN_KEY, null)
            ?: return state("credential_required").put("configured", false)
        val usage = queryPlatformUsage(request.payload, token)
        return state("ready")
            .put("amount", usage.amount)
            .put("tokens", usage.tokens.toString())
            .put("bucketCount", usage.bucketCount)
            .put("updatedAtMs", System.currentTimeMillis().toString())
    }

    private fun queryPlatformUsage(payload: JSONObject, token: String): PlatformUsage {
        val startSeconds = payload.optString("startSeconds").trim()
        val endSeconds = payload.optString("endSeconds").trim()
        val timezoneOffsetSeconds = payload.optString("timezoneOffsetSeconds").trim()
        require(startSeconds.isNotBlank()) { "startSeconds is required" }
        require(endSeconds.isNotBlank()) { "endSeconds is required" }
        require(timezoneOffsetSeconds.isNotBlank()) { "timezoneOffsetSeconds is required" }
        val url =
            DeepseekApiConstants.DEEPSEEK_PLATFORM_USAGE_URL.toHttpUrl()
                .newBuilder()
                .addQueryParameter("start", startSeconds)
                .addQueryParameter("end", endSeconds)
                .addQueryParameter("tz", timezoneOffsetSeconds)
                .build()
                .toString()
        return parsePlatformUsage(
            executeJsonGet(url, mapOf("Authorization" to "Bearer $token"))
        )
    }

    private fun handleStats(request: ToolPkgHostBridge.Request): JSONObject {
        val zone = requestZone(request.payload)
        val date = requestDate(request.payload, zone)
        val startMs = date.atStartOfDay(zone).toInstant().toEpochMilli()
        val endMs = date.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
        val models = deepSeekProviderModels(request.context)
        val rangeData =
            runBlocking(Dispatchers.IO) {
                TokenStatsQueryService.rangeData(
                    context = request.context,
                    range = TokenStatsTimeRange(startMs, endMs),
                    params = TokenStatsQueryParams(providerModels = models),
                    zone = zone
                )
            }
        val latest =
            runBlocking(Dispatchers.IO) {
                com.ai.assistance.operit.data.stats.TokenUsageRepository
                    .getInstance(request.context)
                    .withDao { dao -> dao.getLatestRecordForProvider("DEEPSEEK") }
            }
        return state("ready")
            .put("date", date.toString())
            .put("summary", serializeTotals(rangeData.summary))
            .put("latestTurn", serializeLatestRecord(latest))
    }

    private fun latestDeepSeekRecord(context: Context): TokenUsageRecordEntity? {
        return runBlocking(Dispatchers.IO) {
            com.ai.assistance.operit.data.stats.TokenUsageRepository
                .getInstance(context)
                .withDao { dao -> dao.getLatestRecordForProvider("DEEPSEEK") }
        }
    }

    private fun resolveCredential(request: ToolPkgHostBridge.Request): ResolvedCredential {
        val configId = request.payload.optString("configId").trim()
        val keyId = request.payload.optString("keyId").trim()
        require(configId.isNotBlank()) { "configId is required" }
        require(keyId.isNotBlank()) { "keyId is required" }
        val manager = ModelConfigManager(request.context)
        val config =
            runBlocking(Dispatchers.IO) { manager.getModelConfig(configId) }
                ?: error("DeepSeek model config not found: $configId")
        check(config.apiProviderType == ApiProviderType.DEEPSEEK) {
            "Model config is not a DeepSeek config: $configId"
        }
        val apiKey =
            if (config.useMultipleApiKeys) {
                val key = config.apiKeyPool.firstOrNull { it.id == keyId && it.isEnabled }
                    ?: error("DeepSeek API key is unavailable: $keyId")
                key.key
            } else {
                require(keyId == "primary") { "Single-key DeepSeek config requires keyId=primary" }
                config.apiKey
            }
        require(apiKey.isNotBlank()) { "DeepSeek API key is not configured" }
        return ResolvedCredential(
            configId = config.id,
            keyId = keyId,
            accountId = "${config.id}:$keyId",
            apiKey = apiKey
        )
    }

    private fun deepSeekProviderModels(context: Context): Set<String> {
        val manager = ModelConfigManager(context)
        val summaries = runBlocking(Dispatchers.IO) { manager.getAllConfigSummaries() }
        return summaries
            .filter { it.apiProviderType == ApiProviderType.DEEPSEEK }
            .flatMap { summary ->
                summary.modelName
                    .split(',')
                    .map(String::trim)
                    .filter(String::isNotBlank)
                    .map { model -> "DEEPSEEK:$model" }
            }
            .toSet()
    }

    private fun executeJsonGet(url: String, headers: Map<String, String>): JSONObject {
        val requestBuilder = Request.Builder().url(url).get()
        headers.forEach { (name, value) -> requestBuilder.header(name, value) }
        httpClient.newCall(requestBuilder.build()).execute().use { response ->
            val body = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                error("DeepSeek HTTP ${response.code}: ${body.take(200)}")
            }
            return try {
                JSONObject(body)
            } catch (error: Exception) {
                throw IllegalStateException("DeepSeek response is not valid JSON", error)
            }
        }
    }

    private fun parsePlatformUsage(body: JSONObject): PlatformUsage {
        val data = body.optJSONObject("data") ?: error("Platform usage response has no data")
        val businessData = data.optJSONObject("biz_data")
        val series = (businessData ?: data).optJSONArray("series")
            ?: error("Platform usage response has no series")
        var amount = BigDecimal.ZERO
        var tokens = 0L
        var bucketCount = 0
        for (seriesIndex in 0 until series.length()) {
            val seriesItem = series.optJSONObject(seriesIndex)
                ?: error("Platform usage series item is invalid")
            val model = seriesItem.optString("model").trim()
            require(model.isNotBlank()) { "Platform usage series model is missing" }
            val prices = pricesForModel(model)
            val buckets = seriesItem.optJSONArray("buckets")
                ?: error("Platform usage series buckets are missing")
            for (bucketIndex in 0 until buckets.length()) {
                val bucket = buckets.optJSONObject(bucketIndex)
                    ?: error("Platform usage bucket is invalid")
                val time = bucket.optString("time").trim()
                val usage = bucket.optJSONObject("usage")
                    ?: error("Platform usage bucket usage is missing")
                val hit = usageLong(usage, "PROMPT_CACHE_HIT_TOKEN")
                val miss = usageLong(usage, "PROMPT_CACHE_MISS_TOKEN")
                val output = usageLong(usage, "RESPONSE_TOKEN")
                if (hit == 0L && miss == 0L && output == 0L) {
                    continue
                }
                val peakIndex = if (isPeakTime(time.toLong())) 1 else 0
                amount = amount
                    .add(pricePerMillion(hit, prices.hit[peakIndex]))
                    .add(pricePerMillion(miss, prices.miss[peakIndex]))
                    .add(pricePerMillion(output, prices.output[peakIndex]))
                tokens = saturatedAdd(tokens, saturatedAdd(hit, saturatedAdd(miss, output)))
                bucketCount += 1
            }
        }
        return PlatformUsage(
            amount = amount.stripTrailingZeros().toPlainString(),
            tokens = tokens,
            bucketCount = bucketCount
        )
    }

    private fun serializeTotals(totals: TokenStatsTotals): JSONObject {
        return JSONObject()
            .put("requests", totals.requests.toString())
            .put("uncachedInput", serializeTokenAggregate(totals.uncachedInput))
            .put("cachedInput", serializeTokenAggregate(totals.cachedInput))
            .put("totalInput", serializeTokenAggregate(totals.totalInput))
            .put("output", serializeTokenAggregate(totals.output))
            .put("totalTokens", serializeTokenAggregate(totals.totalTokens))
            .put(
                "cost",
                JSONObject()
                    .put("currency", totals.cost.currency.name)
                    .put("knownAmount", totals.cost.knownAmount.toString())
                    .put("unknownContributionCount", totals.cost.unknownContributionCount.toString())
                    .put("totalContributionCount", totals.cost.totalContributionCount.toString())
            )
    }

    private fun serializeTokenAggregate(
        aggregate: com.ai.assistance.operit.data.stats.TokenStatsTokenAggregate
    ): JSONObject {
        return JSONObject()
            .put("knownSum", aggregate.knownSum.toString())
            .put("knownEventCount", aggregate.knownEventCount.toString())
            .put("unknownEventCount", aggregate.unknownEventCount.toString())
            .put("totalEventCount", aggregate.totalEventCount.toString())
    }

    private fun serializeLatestRecord(record: TokenUsageRecordEntity?): JSONObject {
        if (record == null) {
            return JSONObject().put("state", "empty")
        }
        val row =
            TokenUsageModelAggregateRow(
                provider = record.provider,
                model = record.model,
                configId = record.configId,
                requests = record.requestCount,
                uncachedInputTokens = record.uncachedInputTokens ?: 0L,
                uncachedInputKnown = if (record.uncachedInputTokens == null) 0L else record.requestCount,
                cachedInputTokens = record.cachedInputTokens ?: 0L,
                cachedInputKnown = if (record.cachedInputTokens == null) 0L else record.requestCount,
                cacheWriteTokens = record.cacheWriteTokens ?: 0L,
                cacheWriteKnown = if (record.cacheWriteTokens == null) 0L else record.requestCount,
                totalInputTokens = record.totalInputTokens ?: 0L,
                totalInputKnown = if (record.totalInputTokens == null) 0L else record.requestCount,
                outputTokens = record.outputTokens ?: 0L,
                outputKnown = if (record.outputTokens == null) 0L else record.requestCount
            )
        val pricing = TokenPriceResolver.resolve(row.providerModel, null)
        val cost = TokenCostCalculator.currentCost(
            row = row,
            pricing = pricing,
            targetCurrency = PricingCurrency.CNY,
            usdToCnyRate = TokenCostCurrency.DEFAULT_USD_TO_CNY_RATE
        )
        return JSONObject()
            .put("state", "ready")
            .put("occurredAtMs", record.occurredAtMs?.toString() ?: JSONObject.NULL)
            .put("configId", record.configId)
            .put("provider", record.provider)
            .put("model", record.model)
            .put("requestCount", record.requestCount.toString())
            .put("uncachedInputTokens", record.uncachedInputTokens?.toString() ?: JSONObject.NULL)
            .put("cachedInputTokens", record.cachedInputTokens?.toString() ?: JSONObject.NULL)
            .put("totalInputTokens", record.totalInputTokens?.toString() ?: JSONObject.NULL)
            .put("outputTokens", record.outputTokens?.toString() ?: JSONObject.NULL)
            .put(
                "cost",
                JSONObject()
                    .put("currency", cost.currency.name)
                    .put("knownAmount", cost.knownAmount.toString())
                    .put("unknownContributionCount", cost.unknownContributionCount.toString())
            )
    }

    private fun recordLedgerUsage(
        context: Context,
        accountId: String,
        currency: String,
        date: LocalDate,
        currentBalance: BigDecimal
    ): JSONObject = synchronized(ledgerLock) {
        val preferences = balancePreferences(context)
        val dateKey = "date:$accountId"
        val currencyKey = "currency:$accountId"
        val balanceKey = "balance:$accountId"
        val usageKey = "usage:$accountId:$date"
        val previousDate = preferences.getString(dateKey, null)
        val previousCurrency = preferences.getString(currencyKey, null)
        val previousBalance = preferences.getString(balanceKey, null)
        var todayUsage = BigDecimal(preferences.getString(usageKey, "0") ?: "0")
        var baselineCaptured = false
        var state = "ready"
        if (previousDate == null || previousDate != date.toString() || previousCurrency != currency) {
            todayUsage = BigDecimal.ZERO
            baselineCaptured = true
            state = "baseline"
        } else if (previousBalance != null) {
            val previous = BigDecimal(previousBalance)
            if (currentBalance < previous) {
                todayUsage = todayUsage.add(previous.subtract(currentBalance))
            }
        }
        preferences.edit()
            .putString(dateKey, date.toString())
            .putString(currencyKey, currency)
            .putString(balanceKey, currentBalance.toPlainString())
            .putString(usageKey, todayUsage.toPlainString())
            .apply()
        JSONObject()
            .put("state", state)
            .put("todayUsage", todayUsage.stripTrailingZeros().toPlainString())
            .put("baselineCaptured", baselineCaptured)
    }

    private fun balancePreferences(context: Context): SharedPreferences {
        return context.getSharedPreferences(BALANCE_PREFS, Context.MODE_PRIVATE)
    }

    private fun platformPreferences(context: Context): SharedPreferences {
        val masterKey =
            MasterKey.Builder(context)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()
        return EncryptedSharedPreferences.create(
            context,
            PLATFORM_PREFS,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    private fun credentialView(
        id: String,
        name: String,
        enabled: Boolean,
        status: String
    ): JSONObject {
        return JSONObject()
            .put("id", id)
            .put("name", name)
            .put("enabled", enabled)
            .put("status", status)
    }

    private fun requestZone(payload: JSONObject): ZoneId {
        val zoneName = payload.optString("timezone").trim()
        return if (zoneName.isBlank()) ZoneId.systemDefault() else ZoneId.of(zoneName)
    }

    private fun requestDate(payload: JSONObject, zone: ZoneId): LocalDate {
        val dateText = payload.optString("date").trim()
        return if (dateText.isBlank()) LocalDate.now(zone) else LocalDate.parse(dateText)
    }

    private fun usageLong(usage: JSONObject, key: String): Long {
        val raw = usage.opt(key)
        return when (raw) {
            is Number -> raw.toLong()
            is String -> raw.trim().toLong()
            else -> error("Platform usage field is invalid: $key")
        }
    }

    private fun pricePerMillion(tokens: Long, price: BigDecimal): BigDecimal {
        return BigDecimal(tokens)
            .multiply(price)
            .divide(BigDecimal(MILLION), 12, RoundingMode.HALF_UP)
    }

    private fun isPeakTime(timeSeconds: Long): Boolean {
        val beijing = Instant.ofEpochSecond(timeSeconds).atZone(ZoneOffset.ofHours(8))
        if (timeSeconds >= WEEKEND_VALLEY_FROM_SEC) {
            val day = beijing.dayOfWeek.value
            if (day == 6 || day == 7) {
                return false
            }
        }
        val hour = beijing.hour
        return (hour >= 9 && hour < 12) || (hour >= 14 && hour < 18)
    }

    private fun pricesForModel(model: String): PriceSet {
        val normalized = model.lowercase(Locale.ROOT)
        return if (normalized.contains("deepseek-v4-pro")) {
            PriceSet(
                hit = listOf(BigDecimal("0.15"), BigDecimal("0.3")),
                miss = listOf(BigDecimal("4.5"), BigDecimal("9.0")),
                output = listOf(BigDecimal("13.5"), BigDecimal("27.0"))
            )
        } else {
            PriceSet(
                hit = listOf(BigDecimal("0.05"), BigDecimal("0.1")),
                miss = listOf(BigDecimal("1.5"), BigDecimal("3.0")),
                output = listOf(BigDecimal("4.5"), BigDecimal("9.0"))
            )
        }
    }

    private fun state(value: String): JSONObject {
        return JSONObject()
            .put("schemaVersion", 2)
            .put("state", value)
    }

    private fun saturatedAdd(left: Long, right: Long): Long {
        return if (right > 0L && left > Long.MAX_VALUE - right) {
            Long.MAX_VALUE
        } else if (right < 0L && left < Long.MIN_VALUE - right) {
            Long.MIN_VALUE
        } else {
            left + right
        }
    }

    private data class ResolvedCredential(
        val configId: String,
        val keyId: String,
        val accountId: String,
        val apiKey: String
    )

    private data class PriceSet(
        val hit: List<BigDecimal>,
        val miss: List<BigDecimal>,
        val output: List<BigDecimal>
    )

    private data class PlatformUsage(
        val amount: String,
        val tokens: Long,
        val bucketCount: Int
    )

    private class Handler(
        private val callback: (ToolPkgHostBridge.Request) -> JSONObject
    ) : ToolPkgHostBridge.Handler {
        override fun handle(request: ToolPkgHostBridge.Request): JSONObject = callback(request)
    }
}
