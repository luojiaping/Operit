package com.ai.assistance.operit.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.ai.assistance.operit.data.dao.AgentExecutionDao
import com.ai.assistance.operit.data.dao.ChatContentDao
import com.ai.assistance.operit.data.dao.ChatDao
import com.ai.assistance.operit.data.dao.MessageDao
import com.ai.assistance.operit.data.dao.MessageVariantDao
import com.ai.assistance.operit.data.dao.TokenUsageDao
import com.ai.assistance.operit.data.model.AgentChatBindingEntity
import com.ai.assistance.operit.data.model.AgentMessageOwnerEntity
import com.ai.assistance.operit.data.model.AgentRunEntity
import com.ai.assistance.operit.data.model.AgentSessionEntity
import com.ai.assistance.operit.data.model.AgentToolCallEntity
import com.ai.assistance.operit.data.model.ChatEntity
import com.ai.assistance.operit.data.model.MessageEntity
import com.ai.assistance.operit.data.model.MessageVariantEntity
import com.ai.assistance.operit.data.model.TokenStatsModelEntity
import com.ai.assistance.operit.data.model.TokenUsageRecordEntity
/** 应用数据库，包含聊天表和消息表 */
@Database(
    entities = [
        ChatEntity::class,
        MessageEntity::class,
        MessageVariantEntity::class,
        AgentSessionEntity::class,
        AgentRunEntity::class,
        AgentToolCallEntity::class,
        AgentMessageOwnerEntity::class,
        AgentChatBindingEntity::class,
        TokenUsageRecordEntity::class,
        TokenStatsModelEntity::class,
    ],
    version = 23,
    exportSchema = true
)
abstract class AppDatabase : RoomDatabase() {
    /** 获取聊天DAO */
    abstract fun chatDao(): ChatDao

    /** 获取消息DAO */
    abstract fun messageDao(): MessageDao
    abstract fun messageVariantDao(): MessageVariantDao
    abstract fun agentExecutionDao(): AgentExecutionDao
    abstract fun chatContentDao(): ChatContentDao
    abstract fun tokenUsageDao(): TokenUsageDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        // 定义从版本1到2的迁移
        private val MIGRATION_1_2 =
            object : Migration(1, 2) {
                override fun migrate(db: SupportSQLiteDatabase) {
                    // 创建chats表
                    db.execSQL(
                        """
                            CREATE TABLE IF NOT EXISTS `chats` (
                                `id` TEXT NOT NULL,
                                `title` TEXT NOT NULL,
                                `createdAt` INTEGER NOT NULL,
                                `updatedAt` INTEGER NOT NULL,
                                `inputTokens` INTEGER NOT NULL DEFAULT 0,
                                `outputTokens` INTEGER NOT NULL DEFAULT 0,
                                PRIMARY KEY(`id`)
                            )
                        """.trimIndent()
                    )

                    // 创建messages表
                    db.execSQL(
                        """
                            CREATE TABLE IF NOT EXISTS `messages` (
                                `messageId` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                                `chatId` TEXT NOT NULL,
                                `sender` TEXT NOT NULL,
                                `content` TEXT NOT NULL,
                                `timestamp` INTEGER NOT NULL,
                                `orderIndex` INTEGER NOT NULL,
                                FOREIGN KEY(`chatId`) REFERENCES `chats`(`id`) ON DELETE CASCADE
                            )
                        """.trimIndent()
                    )

                    // 为messages表创建索引
                    db.execSQL("CREATE INDEX IF NOT EXISTS `index_messages_chatId` ON `messages` (`chatId`)")
                }

            }

        // 定义从版本10到11的迁移
        private val MIGRATION_10_11 =
            object : Migration(10, 11) {
                override fun migrate(db: SupportSQLiteDatabase) {
                    // 向chats表添加workspaceEnv列
                    try {
                        db.execSQL("ALTER TABLE chats ADD COLUMN `workspaceEnv` TEXT")
                    } catch (_: Exception) {

                    }
                }
            }

        // 定义从版本11到12的迁移
        private val MIGRATION_11_12 =
            object : Migration(11, 12) {
                override fun migrate(db: SupportSQLiteDatabase) {
                    // 向chats表添加characterGroupId列（用于绑定群组角色卡）
                    try {
                        db.execSQL("ALTER TABLE chats ADD COLUMN `characterGroupId` TEXT")
                    } catch (_: Exception) {

                    }
                }
            }

        private val MIGRATION_12_13 =
            object : Migration(12, 13) {
                override fun migrate(db: SupportSQLiteDatabase) {
                    try {
                        db.execSQL("ALTER TABLE messages ADD COLUMN `inputTokens` INTEGER NOT NULL DEFAULT 0")
                    } catch (_: Exception) {
                    }
                    try {
                        db.execSQL("ALTER TABLE messages ADD COLUMN `outputTokens` INTEGER NOT NULL DEFAULT 0")
                    } catch (_: Exception) {
                    }
                    try {
                        db.execSQL("ALTER TABLE messages ADD COLUMN `cachedInputTokens` INTEGER NOT NULL DEFAULT 0")
                    } catch (_: Exception) {
                    }
                    try {
                        db.execSQL("ALTER TABLE messages ADD COLUMN `sentAt` INTEGER NOT NULL DEFAULT 0")
                    } catch (_: Exception) {
                    }
                    try {
                        db.execSQL("ALTER TABLE messages ADD COLUMN `outputDurationMs` INTEGER NOT NULL DEFAULT 0")
                    } catch (_: Exception) {
                    }
                    try {
                        db.execSQL("ALTER TABLE messages ADD COLUMN `waitDurationMs` INTEGER NOT NULL DEFAULT 0")
                    } catch (_: Exception) {
                    }
                }
            }

        private val MIGRATION_13_14 =
            object : Migration(13, 14) {
                override fun migrate(db: SupportSQLiteDatabase) {
                    db.execSQL("DROP TABLE IF EXISTS `problem_records`")
                }
            }

        private val MIGRATION_14_15 =
            object : Migration(14, 15) {
                override fun migrate(db: SupportSQLiteDatabase) {
                    db.execSQL(
                        "ALTER TABLE messages ADD COLUMN `selectedVariantIndex` INTEGER NOT NULL DEFAULT 0"
                    )
                    db.execSQL(
                        """
                            CREATE TABLE IF NOT EXISTS `message_variants` (
                                `variantId` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                                `chatId` TEXT NOT NULL,
                                `messageTimestamp` INTEGER NOT NULL,
                                `variantIndex` INTEGER NOT NULL,
                                `content` TEXT NOT NULL,
                                `roleName` TEXT NOT NULL DEFAULT '',
                                `provider` TEXT NOT NULL DEFAULT '',
                                `modelName` TEXT NOT NULL DEFAULT '',
                                `inputTokens` INTEGER NOT NULL DEFAULT 0,
                                `outputTokens` INTEGER NOT NULL DEFAULT 0,
                                `cachedInputTokens` INTEGER NOT NULL DEFAULT 0,
                                `sentAt` INTEGER NOT NULL DEFAULT 0,
                                `outputDurationMs` INTEGER NOT NULL DEFAULT 0,
                                `waitDurationMs` INTEGER NOT NULL DEFAULT 0,
                                FOREIGN KEY(`chatId`) REFERENCES `chats`(`id`) ON DELETE CASCADE
                            )
                        """.trimIndent()
                    )
                    db.execSQL(
                        "CREATE INDEX IF NOT EXISTS `index_message_variants_chatId_messageTimestamp` ON `message_variants` (`chatId`, `messageTimestamp`)"
                    )
                    db.execSQL(
                        "CREATE UNIQUE INDEX IF NOT EXISTS `index_message_variants_chatId_messageTimestamp_variantIndex` ON `message_variants` (`chatId`, `messageTimestamp`, `variantIndex`)"
                    )
                }
            }

        private val MIGRATION_15_16 =
            object : Migration(15, 16) {
                override fun migrate(db: SupportSQLiteDatabase) {
                    db.execSQL(
                        "ALTER TABLE messages ADD COLUMN `displayMode` TEXT NOT NULL DEFAULT 'NORMAL'"
                    )
                }
            }

        private val MIGRATION_16_17 =
            object : Migration(16, 17) {
                override fun migrate(db: SupportSQLiteDatabase) {
                    db.execSQL(
                        "CREATE INDEX IF NOT EXISTS `index_messages_chatId_timestamp` ON `messages` (`chatId`, `timestamp`)"
                    )
                }
            }

        private val MIGRATION_17_18 =
            object : Migration(17, 18) {
                override fun migrate(db: SupportSQLiteDatabase) {
                    db.execSQL(
                        "ALTER TABLE messages ADD COLUMN `isFavorite` INTEGER NOT NULL DEFAULT 0"
                    )
                }
            }

        private val MIGRATION_18_19 =
            object : Migration(18, 19) {
                override fun migrate(db: SupportSQLiteDatabase) {
                    db.execSQL(
                        "ALTER TABLE messages ADD COLUMN `completedAt` INTEGER NOT NULL DEFAULT 0"
                    )
                    db.execSQL(
                        "ALTER TABLE message_variants ADD COLUMN `completedAt` INTEGER NOT NULL DEFAULT 0"
                    )
                }
            }

        private val MIGRATION_19_20 =
            object : Migration(19, 20) {
                override fun migrate(db: SupportSQLiteDatabase) {
                    db.execSQL("ALTER TABLE chats ADD COLUMN `pinned` INTEGER NOT NULL DEFAULT 0")
                }
            }

        /** v20 -> v21: final unpublished token statistics schema. */
        internal val MIGRATION_20_21 =
            object : Migration(20, 21) {
                override fun migrate(db: SupportSQLiteDatabase) {
                    runSql { db.execSQL(it) }
                }

                override fun migrate(connection: androidx.sqlite.SQLiteConnection) {
                    runSql { sql ->
                        val stmt = connection.prepare(sql)
                        try {
                            stmt.step()
                        } finally {
                            stmt.close()
                        }
                    }
                }

                private fun runSql(exec: (String) -> Unit) {
                    exec(
                        """
                        CREATE TABLE IF NOT EXISTS `token_usage_records` (
                            `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                            `importKey` TEXT,
                            `occurredAtMs` INTEGER,
                            `configId` TEXT NOT NULL,
                            `provider` TEXT NOT NULL,
                            `model` TEXT NOT NULL,
                            `requestCount` INTEGER NOT NULL DEFAULT 1,
                            `uncachedInputTokens` INTEGER,
                            `cachedInputTokens` INTEGER,
                            `cacheWriteTokens` INTEGER,
                            `totalInputTokens` INTEGER,
                            `outputTokens` INTEGER
                        )
                        """.trimIndent()
                    )
                    exec(
                        "CREATE INDEX IF NOT EXISTS `index_token_usage_records_occurredAtMs` " +
                            "ON `token_usage_records` (`occurredAtMs`)"
                    )
                    exec(
                        "CREATE INDEX IF NOT EXISTS " +
                            "`index_token_usage_records_provider_model_configId_occurredAtMs` " +
                            "ON `token_usage_records` " +
                            "(`provider`, `model`, `configId`, `occurredAtMs`)"
                    )
                    exec(
                        """
                        CREATE TABLE IF NOT EXISTS `token_stats_models` (
                            `configId` TEXT NOT NULL,
                            `provider` TEXT NOT NULL,
                            `model` TEXT NOT NULL,
                            `billingMode` TEXT,
                            `currency` TEXT,
                            `inputPricePerMillion` REAL,
                            `cachedInputPricePerMillion` REAL,
                            `cacheWritePricePerMillion` REAL,
                            `outputPricePerMillion` REAL,
                            `pricePerRequest` REAL,
                            PRIMARY KEY(`configId`, `provider`, `model`)
                        )
                        """.trimIndent()
                    )
                    exec(
                        "CREATE UNIQUE INDEX IF NOT EXISTS `index_token_usage_records_importKey` " +
                            "ON `token_usage_records` (`importKey`)"
                    )
                    // Preserve token-bearing history before the new ledger starts recording requests.
                    exec(
                        """
                        INSERT INTO `token_usage_records` (
                            `occurredAtMs`, `configId`, `provider`, `model`,
                            `requestCount`, `uncachedInputTokens`, `cachedInputTokens`, `totalInputTokens`, `outputTokens`
                        )
                        SELECT
                            `timestamp`, '', `provider`, `modelName`, 1,
                            MAX(`inputTokens` - `cachedInputTokens`, 0), `cachedInputTokens`,
                            `inputTokens`, `outputTokens`
                        FROM `messages`
                        WHERE `sender` = 'ai'
                            AND TRIM(`provider`) <> ''
                            AND TRIM(`modelName`) <> ''
                            AND (`inputTokens` > 0 OR `cachedInputTokens` > 0 OR `outputTokens` > 0)
                        """.trimIndent()
                    )
                    exec(
                        """
                        INSERT INTO `token_usage_records` (
                            `occurredAtMs`, `configId`, `provider`, `model`,
                            `requestCount`, `uncachedInputTokens`, `cachedInputTokens`, `totalInputTokens`, `outputTokens`
                        )
                        SELECT
                            `messageTimestamp`, '', `provider`, `modelName`, 1,
                            MAX(`inputTokens` - `cachedInputTokens`, 0), `cachedInputTokens`,
                            `inputTokens`, `outputTokens`
                        FROM `message_variants`
                        WHERE TRIM(`provider`) <> ''
                            AND TRIM(`modelName`) <> ''
                            AND (`inputTokens` > 0 OR `cachedInputTokens` > 0 OR `outputTokens` > 0)
                        """.trimIndent()
                    )
                }
            }

        /** v21 -> v22: normalize published v21 token schemas and add Agent records. */
        private val MIGRATION_21_22 =
            object : Migration(21, 22) {
                override fun migrate(db: SupportSQLiteDatabase) {
                    migrateTokenUsageRecords(
                        db.tokenUsageRecordColumns()
                    ) { sql -> db.execSQL(sql) }
                    runSql { db.execSQL(it) }
                }

                override fun migrate(connection: androidx.sqlite.SQLiteConnection) {
                    migrateTokenUsageRecords(
                        connection.tokenUsageRecordColumns()
                    ) { sql ->
                        val statement = connection.prepare(sql)
                        try {
                            statement.step()
                        } finally {
                            statement.close()
                        }
                    }
                    runSql { sql ->
                        val statement = connection.prepare(sql)
                        try {
                            statement.step()
                        } finally {
                            statement.close()
                        }
                    }
                }

                private fun SupportSQLiteDatabase.tokenUsageRecordColumns(): Set<String> {
                    val columns = linkedSetOf<String>()
                    query("PRAGMA table_info(`token_usage_records`)").use { cursor ->
                        val nameIndex = cursor.getColumnIndexOrThrow("name")
                        while (cursor.moveToNext()) {
                            columns += cursor.getString(nameIndex)
                        }
                    }
                    return columns
                }

                private fun androidx.sqlite.SQLiteConnection.tokenUsageRecordColumns(): Set<String> {
                    val columns = linkedSetOf<String>()
                    val statement = prepare("PRAGMA table_info(`token_usage_records`)")
                    try {
                        while (statement.step()) {
                            columns += statement.getText(1)
                        }
                    } finally {
                        statement.close()
                    }
                    return columns
                }

                private fun migrateTokenUsageRecords(
                    columns: Set<String>,
                    exec: (String) -> Unit,
                ) {
                    val tableExists = columns.isNotEmpty()
                    if (tableExists) {
                        TOKEN_USAGE_INDEX_NAMES.forEach { indexName ->
                            exec("DROP INDEX IF EXISTS `$indexName`")
                        }
                        exec(
                            "ALTER TABLE `token_usage_records` " +
                                "RENAME TO `token_usage_records_v21_legacy`"
                        )
                    }

                    exec(
                        """
                        CREATE TABLE `token_usage_records` (
                            `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                            `importKey` TEXT,
                            `occurredAtMs` INTEGER,
                            `configId` TEXT NOT NULL,
                            `provider` TEXT NOT NULL,
                            `model` TEXT NOT NULL,
                            `requestCount` INTEGER NOT NULL,
                            `uncachedInputTokens` INTEGER,
                            `cachedInputTokens` INTEGER,
                            `cacheWriteTokens` INTEGER,
                            `totalInputTokens` INTEGER,
                            `outputTokens` INTEGER
                        )
                        """.trimIndent()
                    )

                    if (tableExists) {
                        val importKey = legacyColumn(columns, "importKey", "legacy.`importKey`", "NULL")
                        val occurredAtMs = legacyColumn(columns, "occurredAtMs", "legacy.`occurredAtMs`", "NULL")
                        val configId = legacyColumn(columns, "configId", "COALESCE(legacy.`configId`, '')", "''")
                        val provider = legacyColumn(columns, "provider", "COALESCE(legacy.`provider`, '')", "''")
                        val model = legacyColumn(columns, "model", "COALESCE(legacy.`model`, '')", "''")
                        val requestCount = legacyColumn(
                            columns,
                            "requestCount",
                            "COALESCE(legacy.`requestCount`, 1)",
                            "1"
                        )
                        val uncachedInputTokens = legacyColumn(
                            columns,
                            "uncachedInputTokens",
                            "legacy.`uncachedInputTokens`",
                            "NULL"
                        )
                        val cachedInputTokens = legacyColumn(
                            columns,
                            "cachedInputTokens",
                            "legacy.`cachedInputTokens`",
                            "NULL"
                        )
                        val cacheWriteTokens = legacyColumn(
                            columns,
                            "cacheWriteTokens",
                            "legacy.`cacheWriteTokens`",
                            "NULL"
                        )
                        val totalInputTokens = legacyColumn(
                            columns,
                            "totalInputTokens",
                            "legacy.`totalInputTokens`",
                            "NULL"
                        )
                        val outputTokens = legacyColumn(
                            columns,
                            "outputTokens",
                            "legacy.`outputTokens`",
                            "NULL"
                        )
                        val id = legacyColumn(columns, "id", "legacy.`id`", "NULL")
                        exec(
                            """
                            INSERT INTO `token_usage_records` (
                                `id`, `importKey`, `occurredAtMs`, `configId`, `provider`, `model`,
                                `requestCount`, `uncachedInputTokens`, `cachedInputTokens`,
                                `cacheWriteTokens`, `totalInputTokens`, `outputTokens`
                            )
                            SELECT
                                $id, $importKey, $occurredAtMs, $configId, $provider, $model,
                                $requestCount, $uncachedInputTokens, $cachedInputTokens,
                                $cacheWriteTokens, $totalInputTokens, $outputTokens
                            FROM `token_usage_records_v21_legacy` AS legacy
                            """.trimIndent()
                        )
                        exec("DROP TABLE `token_usage_records_v21_legacy`")
                    }

                    exec(
                        "CREATE INDEX `index_token_usage_records_occurredAtMs` " +
                            "ON `token_usage_records` (`occurredAtMs`)"
                    )
                    exec(
                        "CREATE INDEX `index_token_usage_records_provider_model_configId_occurredAtMs` " +
                            "ON `token_usage_records` " +
                            "(`provider`, `model`, `configId`, `occurredAtMs`)"
                    )
                    exec(
                        "CREATE UNIQUE INDEX `index_token_usage_records_importKey` " +
                            "ON `token_usage_records` (`importKey`)"
                    )
                }

                private fun legacyColumn(
                    columns: Set<String>,
                    name: String,
                    presentExpression: String,
                    absentExpression: String,
                ): String = if (name in columns) presentExpression else absentExpression

                private val TOKEN_USAGE_INDEX_NAMES =
                    listOf(
                        "index_token_usage_records_occurredAtMs",
                        "index_token_usage_records_provider_model_configId_occurredAtMs",
                        "index_token_usage_records_source_occurredAtMs",
                        "index_token_usage_records_category_status_occurredAtMs",
                        "index_token_usage_records_importKey",
                    )

                private fun runSql(exec: (String) -> Unit) {
                    exec(
                        """
                        CREATE TABLE IF NOT EXISTS `agent_sessions` (
                            `sessionId` TEXT NOT NULL,
                            `chatId` TEXT NOT NULL,
                            `pluginId` TEXT NOT NULL,
                            `agentId` TEXT NOT NULL,
                            `displayName` TEXT NOT NULL,
                            `profileVersion` TEXT NOT NULL,
                            `mode` TEXT NOT NULL,
                            `parentSessionId` TEXT,
                            `depth` INTEGER NOT NULL,
                            `status` TEXT NOT NULL,
                            `createdAt` INTEGER NOT NULL,
                            `startedAt` INTEGER,
                            `finishedAt` INTEGER,
                            `updatedAt` INTEGER NOT NULL,
                            PRIMARY KEY(`sessionId`),
                            FOREIGN KEY(`chatId`) REFERENCES `chats`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
                        )
                        """.trimIndent()
                    )
                    exec("CREATE INDEX IF NOT EXISTS `index_agent_sessions_chatId` ON `agent_sessions` (`chatId`)")
                    exec(
                        "CREATE INDEX IF NOT EXISTS `index_agent_sessions_parentSessionId` " +
                            "ON `agent_sessions` (`parentSessionId`)"
                    )
                    exec(
                        "CREATE INDEX IF NOT EXISTS `index_agent_sessions_chatId_updatedAt` " +
                            "ON `agent_sessions` (`chatId`, `updatedAt`)"
                    )
                    exec(
                        """
                        CREATE TABLE IF NOT EXISTS `agent_runs` (
                            `runId` TEXT NOT NULL,
                            `sessionId` TEXT NOT NULL,
                            `parentRunId` TEXT,
                            `parentMessageId` INTEGER,
                            `promptSnapshot` TEXT NOT NULL,
                            `modelSnapshotJson` TEXT NOT NULL,
                            `permissionSnapshotJson` TEXT NOT NULL,
                            `status` TEXT NOT NULL,
                            `summary` TEXT,
                            `errorMessage` TEXT,
                            `createdAt` INTEGER NOT NULL,
                            `startedAt` INTEGER,
                            `finishedAt` INTEGER,
                            `updatedAt` INTEGER NOT NULL,
                            PRIMARY KEY(`runId`),
                            FOREIGN KEY(`sessionId`) REFERENCES `agent_sessions`(`sessionId`) ON UPDATE NO ACTION ON DELETE CASCADE
                        )
                        """.trimIndent()
                    )
                    exec("CREATE INDEX IF NOT EXISTS `index_agent_runs_sessionId` ON `agent_runs` (`sessionId`)")
                    exec("CREATE INDEX IF NOT EXISTS `index_agent_runs_parentRunId` ON `agent_runs` (`parentRunId`)")
                    exec(
                        "CREATE INDEX IF NOT EXISTS `index_agent_runs_parentMessageId` " +
                            "ON `agent_runs` (`parentMessageId`)"
                    )
                    exec(
                        "CREATE INDEX IF NOT EXISTS `index_agent_runs_sessionId_updatedAt` " +
                            "ON `agent_runs` (`sessionId`, `updatedAt`)"
                    )
                    exec(
                        """
                        CREATE TABLE IF NOT EXISTS `agent_tool_calls` (
                            `callId` TEXT NOT NULL,
                            `runId` TEXT NOT NULL,
                            `parentCallId` TEXT,
                            `sequence` INTEGER NOT NULL,
                            `toolName` TEXT NOT NULL,
                            `parametersJson` TEXT NOT NULL,
                            `status` TEXT NOT NULL,
                            `resultText` TEXT,
                            `errorMessage` TEXT,
                            `startedAt` INTEGER,
                            `finishedAt` INTEGER,
                            `updatedAt` INTEGER NOT NULL,
                            PRIMARY KEY(`callId`),
                            FOREIGN KEY(`runId`) REFERENCES `agent_runs`(`runId`) ON UPDATE NO ACTION ON DELETE CASCADE
                        )
                        """.trimIndent()
                    )
                    exec("CREATE INDEX IF NOT EXISTS `index_agent_tool_calls_runId` ON `agent_tool_calls` (`runId`)")
                    exec(
                        "CREATE INDEX IF NOT EXISTS `index_agent_tool_calls_parentCallId` " +
                            "ON `agent_tool_calls` (`parentCallId`)"
                    )
                    exec(
                        "CREATE INDEX IF NOT EXISTS `index_agent_tool_calls_runId_sequence` " +
                            "ON `agent_tool_calls` (`runId`, `sequence`)"
                    )
                    exec(
                        """
                        CREATE TABLE IF NOT EXISTS `agent_message_owners` (
                            `messageId` INTEGER NOT NULL,
                            `chatId` TEXT NOT NULL,
                            `pluginId` TEXT NOT NULL,
                            `agentId` TEXT NOT NULL,
                            `agentSessionId` TEXT NOT NULL,
                            PRIMARY KEY(`messageId`),
                            FOREIGN KEY(`chatId`) REFERENCES `chats`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE,
                            FOREIGN KEY(`messageId`) REFERENCES `messages`(`messageId`) ON UPDATE NO ACTION ON DELETE CASCADE
                        )
                        """.trimIndent()
                    )
                    exec(
                        "CREATE INDEX IF NOT EXISTS `index_agent_message_owners_chatId` " +
                            "ON `agent_message_owners` (`chatId`)"
                    )
                    exec(
                        "CREATE INDEX IF NOT EXISTS `index_agent_message_owners_agentSessionId` " +
                            "ON `agent_message_owners` (`agentSessionId`)"
                    )
                    exec(
                        "CREATE INDEX IF NOT EXISTS `index_agent_message_owners_chatId_agentSessionId` " +
                            "ON `agent_message_owners` (`chatId`, `agentSessionId`)"
                    )
                }
            }

        /** v22 -> v23: explicit root-session routing and owner/chat consistency. */
        private val MIGRATION_22_23 =
            object : Migration(22, 23) {
                override fun migrate(db: SupportSQLiteDatabase) {
                    val invalidOwnerCount =
                        db.query(AGENT_OWNER_INTEGRITY_QUERY).use { cursor ->
                            check(cursor.moveToFirst()) { "Agent owner integrity query returned no row" }
                            cursor.getLong(0)
                        }
                    require(invalidOwnerCount == 0L) {
                        "Cannot migrate inconsistent Agent message owners: $invalidOwnerCount"
                    }
                    runSql { sql -> db.execSQL(sql) }
                }

                override fun migrate(connection: androidx.sqlite.SQLiteConnection) {
                    val statement = connection.prepare(AGENT_OWNER_INTEGRITY_QUERY)
                    val invalidOwnerCount =
                        try {
                            check(statement.step()) { "Agent owner integrity query returned no row" }
                            statement.getLong(0)
                        } finally {
                            statement.close()
                        }
                    require(invalidOwnerCount == 0L) {
                        "Cannot migrate inconsistent Agent message owners: $invalidOwnerCount"
                    }
                    runSql { sql ->
                        val migrationStatement = connection.prepare(sql)
                        try {
                            migrationStatement.step()
                        } finally {
                            migrationStatement.close()
                        }
                    }
                }

                private fun runSql(exec: (String) -> Unit) {
                    exec(
                        "CREATE UNIQUE INDEX IF NOT EXISTS `index_messages_chatId_messageId` " +
                            "ON `messages` (`chatId`, `messageId`)"
                    )
                    exec(
                        "CREATE UNIQUE INDEX IF NOT EXISTS `index_agent_sessions_chatId_sessionId` " +
                            "ON `agent_sessions` (`chatId`, `sessionId`)"
                    )
                    exec("DROP INDEX IF EXISTS `index_agent_tool_calls_runId_sequence`")
                    exec(
                        "CREATE UNIQUE INDEX `index_agent_tool_calls_runId_sequence` " +
                            "ON `agent_tool_calls` (`runId`, `sequence`)"
                    )
                    exec("DROP INDEX IF EXISTS `index_agent_message_owners_chatId`")
                    exec("DROP INDEX IF EXISTS `index_agent_message_owners_agentSessionId`")
                    exec("DROP INDEX IF EXISTS `index_agent_message_owners_chatId_agentSessionId`")
                    exec(
                        "ALTER TABLE `agent_message_owners` " +
                            "RENAME TO `agent_message_owners_v22_legacy`"
                    )
                    exec(
                        """
                        CREATE TABLE `agent_message_owners` (
                            `messageId` INTEGER NOT NULL,
                            `chatId` TEXT NOT NULL,
                            `agentSessionId` TEXT NOT NULL,
                            PRIMARY KEY(`messageId`),
                            FOREIGN KEY(`chatId`, `messageId`)
                                REFERENCES `messages`(`chatId`, `messageId`)
                                ON UPDATE NO ACTION ON DELETE CASCADE,
                            FOREIGN KEY(`chatId`, `agentSessionId`)
                                REFERENCES `agent_sessions`(`chatId`, `sessionId`)
                                ON UPDATE NO ACTION ON DELETE CASCADE
                        )
                        """.trimIndent()
                    )
                    exec(
                        """
                        INSERT INTO `agent_message_owners` (
                            `messageId`, `chatId`, `agentSessionId`
                        )
                        SELECT
                            owner.`messageId`, session.`chatId`, owner.`agentSessionId`
                        FROM `agent_message_owners_v22_legacy` AS owner
                        INNER JOIN `messages` AS message
                            ON message.`messageId` = owner.`messageId`
                        INNER JOIN `agent_sessions` AS session
                            ON session.`sessionId` = owner.`agentSessionId`
                        WHERE message.`chatId` = session.`chatId`
                        """.trimIndent()
                    )
                    exec("DROP TABLE `agent_message_owners_v22_legacy`")
                    exec(
                        "CREATE INDEX `index_agent_message_owners_chatId` " +
                            "ON `agent_message_owners` (`chatId`)"
                    )
                    exec(
                        "CREATE INDEX `index_agent_message_owners_agentSessionId` " +
                            "ON `agent_message_owners` (`agentSessionId`)"
                    )
                    exec(
                        "CREATE UNIQUE INDEX `index_agent_message_owners_chatId_messageId` " +
                            "ON `agent_message_owners` (`chatId`, `messageId`)"
                    )
                    exec(
                        "CREATE INDEX `index_agent_message_owners_chatId_agentSessionId` " +
                            "ON `agent_message_owners` (`chatId`, `agentSessionId`)"
                    )
                    exec(
                        """
                        CREATE TABLE `agent_chat_bindings` (
                            `chatId` TEXT NOT NULL,
                            `activeSessionId` TEXT NOT NULL,
                            `updatedAt` INTEGER NOT NULL,
                            PRIMARY KEY(`chatId`),
                            FOREIGN KEY(`chatId`, `activeSessionId`)
                                REFERENCES `agent_sessions`(`chatId`, `sessionId`)
                                ON UPDATE NO ACTION ON DELETE CASCADE
                        )
                        """.trimIndent()
                    )
                    exec(
                        "CREATE UNIQUE INDEX `index_agent_chat_bindings_chatId_activeSessionId` " +
                            "ON `agent_chat_bindings` (`chatId`, `activeSessionId`)"
                    )
                    exec(
                        "CREATE UNIQUE INDEX `index_agent_chat_bindings_activeSessionId` " +
                            "ON `agent_chat_bindings` (`activeSessionId`)"
                    )
                }

                private val AGENT_OWNER_INTEGRITY_QUERY =
                    """
                    SELECT COUNT(*)
                    FROM `agent_message_owners` AS owner
                    LEFT JOIN `messages` AS message
                        ON message.`messageId` = owner.`messageId`
                    LEFT JOIN `agent_sessions` AS session
                        ON session.`sessionId` = owner.`agentSessionId`
                    WHERE message.`messageId` IS NULL
                        OR session.`sessionId` IS NULL
                        OR owner.`chatId` <> message.`chatId`
                        OR owner.`chatId` <> session.`chatId`
                        OR message.`chatId` <> session.`chatId`
                        OR owner.`pluginId` <> session.`pluginId`
                        OR owner.`agentId` <> session.`agentId`
                        OR message.`sender` NOT IN ('ai', 'summary')
                    """.trimIndent()
            }

        // 定义从版本2到3的迁移
        private val MIGRATION_2_3 =
            object : Migration(2, 3) {
                override fun migrate(db: SupportSQLiteDatabase) {
                    // 向chats表添加group列
                    db.execSQL("ALTER TABLE chats ADD COLUMN `group` TEXT")
                }
            }

        // 定义从版本3到4的迁移
        private val MIGRATION_3_4 =
            object : Migration(3, 4) {
                override fun migrate(db: SupportSQLiteDatabase) {
                    // 向chats表添加displayOrder列，并用updatedAt填充现有数据
                    db.execSQL(
                        "ALTER TABLE chats ADD COLUMN `displayOrder` INTEGER NOT NULL DEFAULT 0"
                    )
                    db.execSQL("UPDATE chats SET displayOrder = updatedAt")
                }
            }

        // 定义从版本4到5的迁移
        private val MIGRATION_4_5 =
            object : Migration(4, 5) {
                override fun migrate(db: SupportSQLiteDatabase) {
                    // 向chats表添加workspace列
                    db.execSQL("ALTER TABLE chats ADD COLUMN `workspace` TEXT")
                }
            }

        // 定义从版本5到6的迁移
        private val MIGRATION_5_6 =
            object : Migration(5, 6) {
                override fun migrate(db: SupportSQLiteDatabase) {
                    // 检查currentWindowSize列是否已存在，如果不存在则添加
                    try {
                        db.execSQL("ALTER TABLE chats ADD COLUMN `currentWindowSize` INTEGER NOT NULL DEFAULT 0")
                    } catch (_: Exception) {

                    }
                }
            }

        // 定义从版本6到7的迁移
        private val MIGRATION_6_7 =
            object : Migration(6, 7) {
                override fun migrate(db: SupportSQLiteDatabase) {
                    // 向messages表添加roleName列
                    db.execSQL("ALTER TABLE messages ADD COLUMN `roleName` TEXT NOT NULL DEFAULT ''")
                }
            }

        // 定义从版本7到8的迁移
        private val MIGRATION_7_8 =
            object : Migration(7, 8) {
                override fun migrate(db: SupportSQLiteDatabase) {
                    // 向chats表添加parentChatId列
                    db.execSQL("ALTER TABLE chats ADD COLUMN `parentChatId` TEXT")
                    // 向chats表添加characterCardName列（用于绑定角色卡）
                    db.execSQL("ALTER TABLE chats ADD COLUMN `characterCardName` TEXT")
                }
            }

        // 定义从版本8到9的迁移
        private val MIGRATION_8_9 =
            object : Migration(8, 9) {
                override fun migrate(db: SupportSQLiteDatabase) {
                    // 向messages表添加provider列（供应商）
                    db.execSQL("ALTER TABLE messages ADD COLUMN `provider` TEXT NOT NULL DEFAULT ''")
                    // 向messages表添加modelName列（模型名称）
                    db.execSQL("ALTER TABLE messages ADD COLUMN `modelName` TEXT NOT NULL DEFAULT ''")
                }
            }

        // 定义从版本9到10的迁移
        private val MIGRATION_9_10 =
            object : Migration(9, 10) {
                override fun migrate(db: SupportSQLiteDatabase) {
                    // 向chats表添加locked列（锁定聊天，禁止删除）
                    try {
                        db.execSQL("ALTER TABLE chats ADD COLUMN `locked` INTEGER NOT NULL DEFAULT 0")
                    } catch (_: Exception) {

                    }
                }
            }

        /** 获取数据库实例，单例模式 */
        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE
                ?: synchronized(this) {
                    val instance =
                        Room.databaseBuilder(
                            context.applicationContext,
                            AppDatabase::class.java,
                            "app_database"
                        )
                            .addMigrations(
                                MIGRATION_1_2,
                                MIGRATION_2_3,
                                MIGRATION_3_4,
                                MIGRATION_4_5,
                                MIGRATION_5_6,
                                MIGRATION_6_7,
                                MIGRATION_7_8,
                                MIGRATION_8_9,
                                MIGRATION_9_10,
                                MIGRATION_10_11,
                                MIGRATION_11_12,
                                MIGRATION_12_13,
                                MIGRATION_13_14,
                                MIGRATION_14_15,
                                MIGRATION_15_16,
                                MIGRATION_16_17,
                                MIGRATION_17_18,
                                MIGRATION_18_19,
                                MIGRATION_19_20,
                                MIGRATION_20_21,
                                MIGRATION_21_22,
                                MIGRATION_22_23
                            ) // 添加新的迁移
                            .build()
                    INSTANCE = instance
                    instance
                }
        }

        fun closeDatabase() {
            synchronized(this) {
                try {
                    INSTANCE?.close()
                } finally {
                    INSTANCE = null
                }
            }
        }
    }
}
