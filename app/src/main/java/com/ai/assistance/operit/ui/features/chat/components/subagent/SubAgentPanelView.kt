package com.ai.assistance.operit.ui.features.chat.components.subagent

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.ai.assistance.operit.api.chat.subagent.SubAgentCardState
import com.ai.assistance.operit.api.chat.subagent.SubAgentGeneration
import com.ai.assistance.operit.data.model.SubAgentStatus

/**
 * 子代理面板：嵌入主代理消息下方，显示当前和历史子代理。
 *
 * @param generations 按 generationId 降序排列的所有代次（[0] = 最新）
 * @param onAgentClick 用户点击某个子代理卡片时回调（用于打开详情）
 */
@Composable
fun SubAgentPanelView(
    generations: List<SubAgentGeneration>,
    onAgentClick: (SubAgentCardState) -> Unit,
    modifier: Modifier = Modifier
) {
    if (generations.isEmpty()) return

    // 扁平化所有代的代理列表，按代分组显示
    var showHistory by remember { mutableStateOf(false) }
    val currentGen = generations.firstOrNull { it.anyRunning } ?: generations.first()
    val historicalGens = generations.filter { it.generationId != currentGen.generationId }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = 6.dp),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        tonalElevation = 1.dp
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            // 当前代的卡片
            currentGen.agents.forEach { agent ->
                SubAgentCardView(
                    agent = agent,
                    onClick = { onAgentClick(agent) },
                    modifier = Modifier.padding(bottom = if (agent == currentGen.agents.last()) 0.dp else 4.dp)
                )
            }

            // 历史代（折叠）
            if (historicalGens.isNotEmpty()) {
                Spacer(Modifier.height(4.dp))

                val totalHistoricalAgents = historicalGens.sumOf { it.agents.size }
                val completedCount = historicalGens.sumOf { gen ->
                    gen.agents.count { it.status == SubAgentStatus.COMPLETED }
                }

                TextButton(
                    onClick = { showHistory = !showHistory },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "历史 (${historicalGens.size} 代, $completedCount/$totalHistoricalAgents 完成)",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Icon(
                            imageVector = if (showHistory) Icons.Default.KeyboardArrowUp
                                         else Icons.Default.KeyboardArrowDown,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                AnimatedVisibility(
                    visible = showHistory,
                    enter = expandVertically(),
                    exit = shrinkVertically()
                ) {
                    Column {
                        historicalGens.forEach { gen ->
                            gen.agents.forEach { agent ->
                                SubAgentCardView(
                                    agent = agent,
                                    onClick = { onAgentClick(agent) },
                                    modifier = Modifier.padding(bottom = 4.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}