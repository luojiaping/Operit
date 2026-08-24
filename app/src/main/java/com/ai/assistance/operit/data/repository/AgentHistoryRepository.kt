package com.ai.assistance.operit.data.repository

import android.content.Context
import androidx.room.withTransaction
import com.ai.assistance.operit.core.agent.contract.AgentSessionId
import com.ai.assistance.operit.core.agent.history.AgentHistoryAdapter
import com.ai.assistance.operit.core.agent.history.AgentHistoryItem
import com.ai.assistance.operit.core.agent.history.AgentHistoryProjection
import com.ai.assistance.operit.core.agent.history.AgentHistorySourceMessage
import com.ai.assistance.operit.data.db.AppDatabase
import com.ai.assistance.operit.data.model.MessageEntity

class AgentHistoryRepository private constructor(context: Context) {
    companion object {
        private const val SQLITE_IN_QUERY_BATCH_SIZE = 500

        @Volatile
        private var instance: AgentHistoryRepository? = null

        fun getInstance(context: Context): AgentHistoryRepository {
            return instance ?: synchronized(this) {
                instance ?: AgentHistoryRepository(context.applicationContext).also { instance = it }
            }
        }
    }

    private val database = AppDatabase.getDatabase(context)
    private val chatContentDao = database.chatContentDao()
    private val agentExecutionDao = database.agentExecutionDao()

    suspend fun loadPluginHistory(
        chatId: String,
        sessionId: AgentSessionId,
    ): List<AgentHistoryItem> {
        require(chatId.isNotBlank()) { "chatId must not be blank" }
        return database.withTransaction {
            val session = requireNotNull(agentExecutionDao.getSession(sessionId.value)) {
                "Agent session not found: ${sessionId.value}"
            }
            require(session.chatId == chatId) { "Agent session belongs to another chat" }
            val messageIds =
                agentExecutionDao.getPluginHistoryMessageIds(
                    chatId = chatId,
                    sessionId = sessionId.value,
                )
            val messages =
                messageIds
                    .chunked(SQLITE_IN_QUERY_BATCH_SIZE)
                    .flatMap { batch -> chatContentDao.getMessagesForChatByIds(chatId, batch) }
            AgentHistoryProjection.forPluginAgent(
                loadHistoryItemsLocked(chatId, messages),
                sessionId,
            )
        }
    }

    private suspend fun loadHistoryItemsLocked(
        chatId: String,
        messages: List<MessageEntity>,
    ): List<AgentHistoryItem> {
        if (messages.isEmpty()) {
            return emptyList()
        }
        val variants =
            messages
                .map { message -> message.timestamp }
                .distinct()
                .chunked(SQLITE_IN_QUERY_BATCH_SIZE)
                .flatMap { batch ->
                    chatContentDao.getVariantsForMessages(
                        chatId = chatId,
                        messageTimestamps = batch,
                    )
                }
        val variantsByIdentity =
            variants.associateBy { variant -> variant.messageTimestamp to variant.variantIndex }
        val ownersByMessageId =
            agentExecutionDao.getMessageOwners(chatId).associateBy { owner -> owner.messageId }
        val sourceMessages =
            messages.map { message ->
                val content =
                    when (message.selectedVariantIndex) {
                        0 -> message.content
                        else ->
                            requireNotNull(
                                variantsByIdentity[message.timestamp to message.selectedVariantIndex]
                            ) {
                                "Selected message variant not found: ${message.messageId}/${message.selectedVariantIndex}"
                            }.content
                    }
                AgentHistorySourceMessage(
                    messageId = message.messageId,
                    timestamp = message.timestamp,
                    orderIndex = message.orderIndex,
                    sender = message.sender,
                    content = content,
                    pluginOwner = ownersByMessageId[message.messageId]?.toOwner(),
                )
            }
        return AgentHistoryAdapter.adapt(sourceMessages)
    }
}
