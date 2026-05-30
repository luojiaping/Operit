package com.ai.assistance.operit.ui.features.chat.components.subagent

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ai.assistance.operit.api.chat.subagent.SubAgentCardState
import com.ai.assistance.operit.data.model.SubAgentStatus

/**
 * 单个子代理的卡片视图
 */
@Composable
fun SubAgentCardView(
    agent: SubAgentCardState,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val containerColor = when (agent.status) {
        SubAgentStatus.RUNNING -> MaterialTheme.colorScheme.primaryContainer
        SubAgentStatus.COMPLETED -> MaterialTheme.colorScheme.tertiaryContainer
        SubAgentStatus.FAILED -> MaterialTheme.colorScheme.errorContainer
        SubAgentStatus.PENDING -> MaterialTheme.colorScheme.surfaceVariant
        SubAgentStatus.CANCELLED -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        else -> MaterialTheme.colorScheme.surfaceVariant
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = containerColor)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 状态图标
            SubAgentStatusIcon(status = agent.status)

            Spacer(Modifier.width(10.dp))

            // 名称 + 状态描述
            Column(Modifier.weight(1f)) {
                Text(
                    text = agent.name,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = getStatusDescription(agent),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            // 耗时
            val elapsed = formatElapsedTime(agent.elapsedTimeMs)
            if (elapsed.isNotEmpty()) {
                Text(
                    text = elapsed,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun getStatusDescription(agent: SubAgentCardState): String {
    return when (agent.status) {
        SubAgentStatus.PENDING -> "等待中..."
        SubAgentStatus.RUNNING -> agent.currentStep ?: "执行中..."
        SubAgentStatus.COMPLETED -> {
            val summary = agent.resultSummary ?: "已完成"
            if (summary.length > 40) summary.take(40) + "..." else summary
        }
        SubAgentStatus.FAILED -> agent.resultSummary?.take(40) ?: "执行失败"
        SubAgentStatus.CANCELLED -> "已取消"
        else -> ""
    }
}