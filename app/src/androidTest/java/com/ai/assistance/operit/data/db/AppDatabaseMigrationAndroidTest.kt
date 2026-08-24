package com.ai.assistance.operit.data.db

import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AppDatabaseMigrationAndroidTest {
    @Test
    fun migration23To24AddsTypedRunSchemaAndSettlesInterruptedRows() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val databaseName = "agent-migration-${System.nanoTime()}.db"
        val helper =
            FrameworkSQLiteOpenHelperFactory().create(
                SupportSQLiteOpenHelper.Configuration.builder(context)
                    .name(databaseName)
                    .callback(
                        object : SupportSQLiteOpenHelper.Callback(23) {
                            override fun onCreate(db: SupportSQLiteDatabase) {
                                createVersion23Schema(db)
                            }

                            override fun onUpgrade(
                                db: SupportSQLiteDatabase,
                                oldVersion: Int,
                                newVersion: Int,
                            ) = Unit
                        }
                    )
                    .build()
            )
        val database = helper.writableDatabase
        try {
            database.execSQL("PRAGMA user_version = 23")

            AppDatabase.migration23To24ForTesting().migrate(database)

            assertEquals(
                setOf(
                    "sessionId",
                    "chatId",
                    "pluginId",
                    "agentId",
                    "displayName",
                    "profileVersion",
                    "profileKind",
                    "mode",
                    "parentSessionId",
                    "depth",
                    "status",
                    "createdAt",
                    "startedAt",
                    "finishedAt",
                    "updatedAt",
                ),
                columns(database, "agent_sessions"),
            )
            assertEquals(
                setOf(
                    "runId",
                    "sessionId",
                    "parentRunId",
                    "parentMessageId",
                    "promptSnapshot",
                    "modelSnapshotJson",
                    "permissionSnapshotJson",
                    "toolSnapshotJson",
                    "inputMessageId",
                    "outputMessageId",
                    "status",
                    "summary",
                    "errorCode",
                    "errorMessage",
                    "createdAt",
                    "startedAt",
                    "finishedAt",
                    "updatedAt",
                ),
                columns(database, "agent_runs"),
            )
            assertEquals(
                listOf("IDLE", "PRIMARY"),
                database.query(
                    "SELECT status, profileKind FROM agent_sessions WHERE sessionId = 'root'",
                    emptyArray(),
                ).use { cursor ->
                    assertTrue(cursor.moveToFirst())
                    listOf(cursor.getString(0), cursor.getString(1))
                },
            )
            assertEquals(
                listOf("FAILED", "MIGRATION_INTERRUPTED"),
                database.query(
                    "SELECT status, errorCode FROM agent_runs WHERE runId = 'run'",
                    emptyArray(),
                ).use { cursor ->
                    assertTrue(cursor.moveToFirst())
                    listOf(cursor.getString(0), cursor.getString(1))
                },
            )
            assertTrue(tableExists(database, "agent_steps"))
            assertTrue(tableExists(database, "agent_run_leases"))
            assertTrue(indexExists(database, "index_agent_runs_sessionId_runId"))
            assertTrue(indexExists(database, "index_agent_steps_modelRequestId"))
            assertTrue(indexExists(database, "index_agent_run_leases_runId"))
        } finally {
            helper.close()
            context.deleteDatabase(databaseName)
        }
    }

    private fun createVersion23Schema(database: SupportSQLiteDatabase) {
        database.execSQL(
            """
            CREATE TABLE `agent_sessions` (
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
                PRIMARY KEY(`sessionId`)
            )
            """.trimIndent()
        )
        database.execSQL(
            """
            CREATE TABLE `agent_runs` (
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
                PRIMARY KEY(`runId`)
            )
            """.trimIndent()
        )
        database.execSQL(
            """
            INSERT INTO `agent_sessions` (
                `sessionId`, `chatId`, `pluginId`, `agentId`, `displayName`, `profileVersion`,
                `mode`, `parentSessionId`, `depth`, `status`, `createdAt`, `startedAt`,
                `finishedAt`, `updatedAt`
            ) VALUES ('root', 'chat', 'plugin', 'agent', 'Agent', '1', 'build', NULL, 0,
                'RUNNING', 1, 1, NULL, 2)
            """.trimIndent()
        )
        database.execSQL(
            """
            INSERT INTO `agent_runs` (
                `runId`, `sessionId`, `parentRunId`, `parentMessageId`, `promptSnapshot`,
                `modelSnapshotJson`, `permissionSnapshotJson`, `status`, `summary`,
                `errorMessage`, `createdAt`, `startedAt`, `finishedAt`, `updatedAt`
            ) VALUES ('run', 'root', NULL, NULL, 'prompt', '{}', '[]', 'RUNNING', NULL,
                NULL, 1, 1, NULL, 2)
            """.trimIndent()
        )
    }

    private fun columns(database: SupportSQLiteDatabase, table: String): Set<String> {
        return database.query("PRAGMA table_info(`$table`)", emptyArray()).use { cursor ->
            buildSet {
                while (cursor.moveToNext()) {
                    add(cursor.getString(1))
                }
            }
        }
    }

    private fun tableExists(database: SupportSQLiteDatabase, table: String): Boolean {
        return database.query(
            "SELECT 1 FROM sqlite_master WHERE type = 'table' AND name = ?",
            arrayOf(table),
        ).use { cursor -> cursor.moveToFirst() }
    }

    private fun indexExists(database: SupportSQLiteDatabase, index: String): Boolean {
        return database.query(
            "SELECT 1 FROM sqlite_master WHERE type = 'index' AND name = ?",
            arrayOf(index),
        ).use { cursor -> cursor.moveToFirst() }
    }
}
