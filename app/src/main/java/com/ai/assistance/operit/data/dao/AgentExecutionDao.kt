package com.ai.assistance.operit.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.ai.assistance.operit.data.model.AgentMessageOwnerEntity
import com.ai.assistance.operit.data.model.AgentRunEntity
import com.ai.assistance.operit.data.model.AgentSessionEntity
import com.ai.assistance.operit.data.model.AgentToolCallEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface AgentExecutionDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertSession(entity: AgentSessionEntity)

    @Update
    suspend fun updateSession(entity: AgentSessionEntity)

    @Query("SELECT * FROM agent_sessions WHERE sessionId = :sessionId LIMIT 1")
    suspend fun getSession(sessionId: String): AgentSessionEntity?

    @Query("SELECT * FROM agent_sessions WHERE chatId = :chatId ORDER BY updatedAt ASC")
    fun observeSessions(chatId: String): Flow<List<AgentSessionEntity>>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertRun(entity: AgentRunEntity)

    @Update
    suspend fun updateRun(entity: AgentRunEntity)

    @Query("SELECT * FROM agent_runs WHERE runId = :runId LIMIT 1")
    suspend fun getRun(runId: String): AgentRunEntity?

    @Query("SELECT * FROM agent_runs WHERE sessionId = :sessionId ORDER BY createdAt ASC")
    fun observeRuns(sessionId: String): Flow<List<AgentRunEntity>>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertToolCall(entity: AgentToolCallEntity)

    @Update
    suspend fun updateToolCall(entity: AgentToolCallEntity)

    @Query("SELECT * FROM agent_tool_calls WHERE callId = :callId LIMIT 1")
    suspend fun getToolCall(callId: String): AgentToolCallEntity?

    @Query("SELECT * FROM agent_tool_calls WHERE runId = :runId ORDER BY sequence ASC")
    fun observeToolCalls(runId: String): Flow<List<AgentToolCallEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertMessageOwner(entity: AgentMessageOwnerEntity)

    @Query("SELECT * FROM agent_message_owners WHERE messageId = :messageId LIMIT 1")
    suspend fun getMessageOwner(messageId: Long): AgentMessageOwnerEntity?

    @Query("SELECT * FROM agent_message_owners WHERE chatId = :chatId ORDER BY messageId ASC")
    fun observeMessageOwners(chatId: String): Flow<List<AgentMessageOwnerEntity>>

    @Query("DELETE FROM agent_message_owners WHERE messageId = :messageId")
    suspend fun deleteMessageOwner(messageId: Long)
}
