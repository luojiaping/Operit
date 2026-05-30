package com.ai.assistance.operit.ui.features.chat.components.subagent

import androidx.compose.animation.animateColor
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.unit.dp
import com.ai.assistance.operit.data.model.SubAgentStatus

/**
 * 子代理状态图标
 */
@Composable
fun SubAgentStatusIcon(
    status: String,
    modifier: Modifier = Modifier
) {
    val iconSize = 20.dp

    when (status) {
        SubAgentStatus.PENDING -> {
            val infiniteTransition = rememberInfiniteTransition(label = "pending_pulse")
            val alpha by infiniteTransition.animateFloat(
                initialValue = 0.3f,
                targetValue = 1.0f,
                animationSpec = infiniteRepeatable(
                    animation = tween(800),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "pending_alpha"
            )
            Icon(
                imageVector = Icons.Default.Refresh,
                contentDescription = "等待中",
                modifier = modifier.size(iconSize).alpha(alpha),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        SubAgentStatus.RUNNING -> {
            CircularProgressIndicator(
                modifier = modifier.size(iconSize),
                strokeWidth = 2.5.dp,
                color = MaterialTheme.colorScheme.primary
            )
        }

        SubAgentStatus.COMPLETED -> {
            Icon(
                imageVector = Icons.Default.Check,
                contentDescription = "已完成",
                modifier = modifier.size(iconSize),
                tint = MaterialTheme.colorScheme.tertiary
            )
        }

        SubAgentStatus.FAILED -> {
            Icon(
                imageVector = Icons.Default.Close,
                contentDescription = "失败",
                modifier = modifier.size(iconSize),
                tint = MaterialTheme.colorScheme.error
            )
        }

        SubAgentStatus.CANCELLED -> {
            Icon(
                imageVector = Icons.Default.Close,
                contentDescription = "已取消",
                modifier = modifier.size(iconSize),
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
            )
        }
    }
}

/**
 * 格式化耗时
 */
fun formatElapsedTime(ms: Long): String {
    if (ms <= 0) return ""
    val seconds = ms / 1000
    return when {
        seconds < 60 -> "${seconds}s"
        seconds < 3600 -> "${seconds / 60}m${seconds % 60}s"
        else -> "${seconds / 3600}h${(seconds % 3600) / 60}m"
    }
}