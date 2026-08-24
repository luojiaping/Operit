package com.ai.assistance.operit.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Upsert
import androidx.room.Update
import com.ai.assistance.operit.core.agent.contract.AgentId
import com.ai.assistance.operit.core.agent.contract.AgentMessageOwnerSnapshot
import com.ai.assistance.operit.core.agent.contract.AgentOwner
import com.ai.assistance.operit.core.agent.contract.AgentSessionId
import com.ai.assistance.operit.data.model.AgentChatBindingEntity
import com.ai.assistance.operit.data.model.AgentMessageOwnerEntity
import com.ai.assistance.operit.data.model.AgentRunEntity
import com.ai.assistance.operit.data.model.AgentRunLeaseEntity
import com.ai.assistance.operit.data.model.AgentSessionEntity
import com.ai.assistance.operit.data.model.AgentStepEntity
import com.ai.assistance.operit.data.model.AgentToolCallEntity
import kotlinx.coroutines.flow.Flow

data class AgentMessageIdentity(
    val messageId: Long,
    val chatId: String,
    val sender: String,
    val timestamp: Long,
    val orderIndex: Int,
    val selectedVariantIndex: Int,
)

data class AgentMessageOwnerRecord(
    val messageId: Long,
    val chatId: String,
    val agentSessionId: String,
    val pluginId: String,
    val agentId: String,
) {
    fun toOwner(): AgentOwner.PluginAgent {
        return AgentOwner.PluginAgent(
            pluginId = pluginId,
            agentId = AgentId(agentId),
            sessionId = AgentSessionId(agentSessionId),
        )
    }

    fun toSnapshot(): AgentMessageOwnerSnapshot {
        return AgentMessageOwnerSnapshot(
            messageId = messageId,
            chatId = chatId,
            owner = toOwner(),
        )
    }
}

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

    @Query("SELECT COUNT(*) FROM agent_sessions WHERE chatId = :chatId")
    suspend fun countSessions(chatId: String): Int

    @Upsert
    suspend fun setChatBinding(entity: AgentChatBindingEntity)

    @Query("SELECT * FROM agent_chat_bindings WHERE chatId = :chatId LIMIT 1")
    suspend fun getChatBinding(chatId: String): AgentChatBindingEntity?

    @Query("SELECT * FROM agent_chat_bindings WHERE chatId = :chatId LIMIT 1")
    fun observeChatBinding(chatId: String): Flow<AgentChatBindingEntity?>

    @Query("DELETE FROM agent_chat_bindings WHERE chatId = :chatId")
    suspend fun clearChatBinding(chatId: String)

    @Query(
        "DELETE FROM agent_chat_bindings " +
            "WHERE chatId = :chatId AND activeSessionId = :sessionId"
    )
    suspend fun clearChatBindingForSession(chatId: String, sessionId: String)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertRun(entity: AgentRunEntity)

    @Update
    suspend fun updateRun(entity: AgentRunEntity)

    @Query("SELECT * FROM agent_runs WHERE runId = :runId LIMIT 1")
    suspend fun getRun(runId: String): AgentRunEntity?

    @Query("SELECT * FROM agent_runs WHERE sessionId = :sessionId ORDER BY createdAt ASC")
    fun observeRuns(sessionId: String): Flow<List<AgentRunEntity>>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertStep(entity: AgentStepEntity)

    @Update
    suspend fun updateStep(entity: AgentStepEntity)

    @Query("SELECT * FROM agent_steps WHERE stepId = :stepId LIMIT 1")
    suspend fun getStep(stepId: String): AgentStepEntity?

    @Query("SELECT * FROM agent_steps WHERE runId = :runId ORDER BY sequence ASC")
    fun observeSteps(runId: String): Flow<List<AgentStepEntity>>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertRunLease(entity: AgentRunLeaseEntity)

    @Query("SELECT * FROM agent_run_leases WHERE sessionId = :sessionId LIMIT 1")
    suspend fun getRunLease(sessionId: String): AgentRunLeaseEntity?

    @Query("SELECT * FROM agent_run_leases ORDER BY acquiredAt ASC")
    suspend fun getRunLeases(): List<AgentRunLeaseEntity>

    @Query("SELECT * FROM agent_steps WHERE runId = :runId AND status = 'RUNNING' LIMIT 1")
    suspend fun getRunningStep(runId: String): AgentStepEntity?

    @Query("DELETE FROM agent_run_leases WHERE sessionId = :sessionId AND runId = :runId")
    suspend fun deleteRunLease(sessionId: String, runId: String)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertToolCall(entity: AgentToolCallEntity)

    @Update
    suspend fun updateToolCall(entity: AgentToolCallEntity)

    @Query("SELECT * FROM agent_tool_calls WHERE callId = :callId LIMIT 1")
    suspend fun getToolCall(callId: String): AgentToolCallEntity?

    @Query("SELECT * FROM agent_tool_calls WHERE runId = :runId AND sequence = :sequence LIMIT 1")
    suspend fun getToolCallBySequence(runId: String, sequence: Int): AgentToolCallEntity?

    @Query("SELECT * FROM agent_tool_calls WHERE runId = :runId ORDER BY sequence ASC")
    fun observeToolCalls(runId: String): Flow<List<AgentToolCallEntity>>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertMessageOwner(entity: AgentMessageOwnerEntity)

    @Query("SELECT * FROM agent_message_owners WHERE messageId = :messageId LIMIT 1")
    suspend fun getMessageOwner(messageId: Long): AgentMessageOwnerEntity?

    @Query(
        """
        SELECT
            owner.messageId AS messageId,
            owner.chatId AS chatId,
            owner.agentSessionId AS agentSessionId,
            session.pluginId AS pluginId,
            session.agentId AS agentId
        FROM agent_message_owners AS owner
        INNER JOIN agent_sessions AS session
            ON session.chatId = owner.chatId AND session.sessionId = owner.agentSessionId
        WHERE owner.messageId = :messageId
        LIMIT 1
        """
    )
    suspend fun getMessageOwnerRecord(messageId: Long): AgentMessageOwnerRecord?

    @Query(
        "SELECT messageId, chatId, sender, timestamp, orderIndex, selectedVariantIndex " +
            "FROM messages WHERE messageId = :messageId LIMIT 1"
    )
    suspend fun getMessageIdentity(messageId: Long): AgentMessageIdentity?

    @Query(
        """
        SELECT message.messageId
        FROM messages AS message
        LEFT JOIN agent_message_owners AS owner
            ON owner.chatId = message.chatId AND owner.messageId = message.messageId
        WHERE message.chatId = :chatId
            AND (message.sender = 'user' OR owner.agentSessionId = :sessionId)
        ORDER BY message.orderIndex ASC, message.messageId ASC
        """
    )
    suspend fun getPluginHistoryMessageIds(
        chatId: String,
        sessionId: String,
    ): List<Long>

    @Query(
        """
        SELECT EXISTS(
            SELECT 1
            FROM agent_message_owners AS owner
            INNER JOIN messages AS message
                ON message.chatId = owner.chatId AND message.messageId = owner.messageId
            WHERE message.chatId = :chatId AND message.timestamp = :messageTimestamp
        )
        """
    )
    suspend fun hasMessageOwner(
        chatId: String,
        messageTimestamp: Long,
    ): Boolean

    @Query(
        """
        SELECT
            owner.messageId AS messageId,
            owner.chatId AS chatId,
            owner.agentSessionId AS agentSessionId,
            session.pluginId AS pluginId,
            session.agentId AS agentId
        FROM agent_message_owners AS owner
        INNER JOIN agent_sessions AS session
            ON session.chatId = owner.chatId AND session.sessionId = owner.agentSessionId
        WHERE owner.chatId = :chatId
        ORDER BY owner.messageId ASC
        """
    )
    fun observeMessageOwners(chatId: String): Flow<List<AgentMessageOwnerRecord>>

    @Query(
        """
        SELECT
            owner.messageId AS messageId,
            owner.chatId AS chatId,
            owner.agentSessionId AS agentSessionId,
            session.pluginId AS pluginId,
            session.agentId AS agentId
        FROM agent_message_owners AS owner
        INNER JOIN agent_sessions AS session
            ON session.chatId = owner.chatId AND session.sessionId = owner.agentSessionId
        WHERE owner.chatId = :chatId
        ORDER BY owner.messageId ASC
        """
    )
    suspend fun getMessageOwners(chatId: String): List<AgentMessageOwnerRecord>

    @Query(
        """
        SELECT COUNT(*)
        FROM agent_message_owners AS owner
        INNER JOIN messages AS message
            ON message.chatId = owner.chatId AND message.messageId = owner.messageId
        WHERE owner.chatId = :chatId
            AND (:upToTimestampInclusive IS NULL OR message.timestamp <= :upToTimestampInclusive)
        """
    )
    suspend fun countMessageOwnersUpToTimestamp(
        chatId: String,
        upToTimestampInclusive: Long?,
    ): Int

    @Query("DELETE FROM agent_message_owners WHERE messageId = :messageId")
    suspend fun deleteMessageOwner(messageId: Long)
}
