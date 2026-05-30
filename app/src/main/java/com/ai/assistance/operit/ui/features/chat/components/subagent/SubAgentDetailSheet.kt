package com.ai.assistance.operit.ui.features.chat.components.subagent

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ai.assistance.operit.api.chat.subagent.SubAgentCardState
import com.ai.assistance.operit.data.model.ChatMessage
import com.ai.assistance.operit.data.model.SubAgentStatus

/**
 * 子代理详情底部弹窗
 * 点击子代理卡片后弹出，展示该子代理的完整对话记录。
 *
 * @param agentState 子代理卡片状态
 * @param chatHistory 子代理的完整对话历史（从 ObjectBox 加载）
 * @param onDismiss 关闭回调
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SubAgentDetailSheet(
    agentState: SubAgentCardState,
    chatHistory: List<ChatMessage>,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
        ) {
            // ─── 头部信息 ─────────────────────────
            SubAgentDetailHeader(agentState)

            Spacer(Modifier.height(12.dp))
            HorizontalDivider()
            Spacer(Modifier.height(8.dp))

            // ─── 对话历史 ─────────────────────────
            if (chatHistory.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "暂无对话记录",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(400.dp)
                ) {
                    items(chatHistory) { message ->
                        SubAgentMessageItem(message)
                        Spacer(Modifier.height(6.dp))
                    }
                }
            }

            // 底部留白，避免被底部导航遮挡
            Spacer(Modifier.height(32.dp))
        }
    }
}

@Composable
private fun SubAgentDetailHeader(agent: SubAgentCardState) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        SubAgentStatusIcon(status = agent.status)
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = agent.name,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = agent.task,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2
            )
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(
                text = when (agent.status) {
                    SubAgentStatus.COMPLETED -> "✅ 已完成"
                    SubAgentStatus.FAILED -> "❌ 失败"
                    SubAgentStatus.RUNNING -> "🔄 执行中"
                    SubAgentStatus.PENDING -> "⏳ 等待中"
                    SubAgentStatus.CANCELLED -> "🚫 已取消"
                    else -> ""
                },
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Medium
            )
            val elapsed = formatElapsedTime(agent.elapsedTimeMs)
            if (elapsed.isNotEmpty()) {
                Text(
                    text = "耗时 $elapsed",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (agent.roundsUsed > 0) {
                Text(
                    text = "${agent.roundsUsed}/${agent.maxRounds} 轮",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun SubAgentMessageItem(message: ChatMessage) {
    val isUser = message.sender == "user"
    val bubbleColor = if (isUser) {
        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f)
    } else {
        MaterialTheme.colorScheme.surfaceVariant
    }
    val textColor = if (isUser) {
        MaterialTheme.colorScheme.onPrimaryContainer
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = if (isUser) 32.dp else 0.dp, end = if (isUser) 0.dp else 32.dp)
    ) {
        Text(
            text = if (isUser) "用户" else "AI",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 2.dp)
        )
        Text(
            text = message.content,
            style = MaterialTheme.typography.bodySmall,
            color = textColor,
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp)
        )
    }
}