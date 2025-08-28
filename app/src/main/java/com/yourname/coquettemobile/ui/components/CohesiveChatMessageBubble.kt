package com.yourname.coquettemobile.ui.components

import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.yourname.coquettemobile.core.models.AiMessageState
import com.yourname.coquettemobile.core.models.ChatMessage
import com.yourname.coquettemobile.core.models.OrchestrationPhase
import com.yourname.coquettemobile.ui.components.orchestration.ExecutionPlanPreviewCard

@Composable
fun CohesiveChatMessageBubble(
    message: ChatMessage,
    personalityName: String = "AI",
    showModelUsed: Boolean = true
) {
    val backgroundColor = MaterialTheme.colorScheme.surfaceVariant
    val textColor = MaterialTheme.colorScheme.onSurfaceVariant

    Box(
        modifier = Modifier.fillMaxWidth(),
        contentAlignment = Alignment.CenterStart
    ) {
        Card(
            modifier = Modifier.widthIn(max = 380.dp),
            shape = RoundedCornerShape(topStart = 4.dp, topEnd = 16.dp, bottomStart = 16.dp, bottomEnd = 16.dp),
            colors = CardDefaults.cardColors(containerColor = backgroundColor)
        ) {
            Column(
                modifier = Modifier
                    .padding(12.dp)
                    .animateContentSize()
            ) {
                // The "Evolving Thought Bubble" starts here
                if (message.orchestrationPhases.isNotEmpty()) {
                    var isReasoningExpanded by remember { mutableStateOf(true) }

                    // Parent Dropdown: "{PersonalityName}'s Reasoning"
                    ReasoningHeader(isExpanded = isReasoningExpanded, onToggle = { isReasoningExpanded = !isReasoningExpanded }, personalityName = personalityName, messageState = message.messageState)

                    AnimatedVisibility(
                        visible = isReasoningExpanded,
                        enter = expandVertically() + fadeIn(),
                        exit = shrinkVertically() + fadeOut()
                    ) {
                        Column(modifier = Modifier.padding(top = 8.dp, start = 8.dp)) {
                            message.orchestrationPhases.forEach { phase ->
                                PhaseCard(phase = phase)
                            }
                        }
                    }
                }

                // The final response appears below all the phases
                if (message.messageState == AiMessageState.COMPLETE && message.content.isNotBlank()) {
                    if (message.orchestrationPhases.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(12.dp))
                    }
                    StreamingRichText(
                        content = message.content,
                        color = textColor
                    )
                }

                // Footer with Model Info
                if (message.modelUsed != null && showModelUsed) {
                    Spacer(modifier = Modifier.height(8.dp))
                    ModelInfoFooter(message = message)
                }
            }
        }
    }
}

@Composable
private fun ReasoningHeader(isExpanded: Boolean, onToggle: () -> Unit, personalityName: String, messageState: AiMessageState) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onToggle)
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (messageState != AiMessageState.COMPLETE) {
                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
            } else {
                Icon(Icons.Default.CheckCircle, contentDescription = "Complete", modifier = Modifier.size(16.dp))
            }
            Text(
                text = "${personalityName}'s Reasoning",
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold, fontStyle = FontStyle.Italic)
            )
        }
        Icon(
            imageVector = if (isExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
            contentDescription = if (isExpanded) "Collapse" else "Expand"
        )
    }
}

@Composable
private fun PhaseCard(phase: OrchestrationPhase) {
    val (phaseColor, phaseName, phaseIcon) = when (phase) {
        is OrchestrationPhase.IntentAnalysisPhase -> Triple(Color(0xFF9C27B0), "Intent Analysis", Icons.Default.Search) // Purple
        is OrchestrationPhase.PlanningPhase -> Triple(Color(0xFF2196F3), "Tool Planning", Icons.Default.List) // Blue  
        is OrchestrationPhase.ExecutionPhase -> Triple(Color(0xFF4CAF50), "Tool Execution", Icons.Default.Build) // Green
        is OrchestrationPhase.SynthesisPhase -> Triple(Color(0xFFFF9800), "Synthesis", Icons.Default.Create) // Orange
        is OrchestrationPhase.PersonalityResponsePhase -> Triple(Color(0xFFE91E63), "Personality Response", Icons.Default.Person) // Pink
    }

    var isPhaseExpanded by remember { mutableStateOf(true) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        colors = CardDefaults.cardColors(
            containerColor = phaseColor.copy(alpha = 0.1f)
        ),
        border = BorderStroke(1.dp, phaseColor.copy(alpha = 0.3f))
    ) {
        Column {
            // Phase Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { isPhaseExpanded = !isPhaseExpanded }
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = phaseIcon,
                        contentDescription = phaseName,
                        modifier = Modifier.size(18.dp),
                        tint = phaseColor
                    )
                    Text(
                        text = phaseName,
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontWeight = FontWeight.SemiBold,
                            color = phaseColor
                        )
                    )
                }
                Icon(
                    imageVector = if (isPhaseExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                    contentDescription = if (isPhaseExpanded) "Collapse" else "Expand",
                    tint = phaseColor.copy(alpha = 0.7f)
                )
            }

            // Phase Content
            AnimatedVisibility(
                visible = isPhaseExpanded,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                Column(modifier = Modifier.padding(start = 12.dp, end = 12.dp, bottom = 12.dp)) {
                    // Show thinking content first (the real <think> content!)
                    if (phase.thinking.isNotEmpty()) {
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 8.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.5f)
                            )
                        ) {
                            Column(modifier = Modifier.padding(8.dp)) {
                                Text(
                                    text = "💭 Thinking Process",
                                    style = MaterialTheme.typography.labelMedium.copy(
                                        fontWeight = FontWeight.Medium
                                    ),
                                    modifier = Modifier.padding(bottom = 4.dp)
                                )
                                StreamingRichText(
                                    content = phase.thinking.joinToString("\n"),
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
                                )
                            }
                        }
                    }

                    // Phase-specific data
                    when (phase) {
                        is OrchestrationPhase.IntentAnalysisPhase -> {
                            Column(modifier = Modifier.padding(8.dp)) {
                                Row {
                                    Text("Complexity: ", style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium))
                                    Text("${phase.analysis.complexity}", style = MaterialTheme.typography.bodySmall)
                                }
                                Row {
                                    Text("Risk Level: ", style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium))
                                    Text("${phase.analysis.riskLevel}", style = MaterialTheme.typography.bodySmall)
                                }
                                if (phase.analysis.reasoning.isNotBlank()) {
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text("Reasoning: ${phase.analysis.reasoning}", style = MaterialTheme.typography.bodySmall)
                                }
                            }
                        }
                        is OrchestrationPhase.PlanningPhase -> {
                            ExecutionPlanPreviewCard(
                                executionPlan = phase.plan, 
                                personalityName = "AI",
                                onExecute = {}, 
                                onModify = {}, 
                                onCancel = {}
                            )
                        }
                        is OrchestrationPhase.ExecutionPhase -> {
                            ToolOutputDropdown(toolExecutions = phase.toolExecutions, isExpanded = true, onToggle = {})
                        }
                        is OrchestrationPhase.SynthesisPhase -> {
                            if (phase.response.isNotBlank()) {
                                Text(
                                    text = "📝 Data Synthesis Complete",
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        fontStyle = FontStyle.Italic,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                                    )
                                )
                            }
                        }
                        is OrchestrationPhase.PersonalityResponsePhase -> {
                            if (phase.response.isNotBlank()) {
                                Text(
                                    text = "🎭 Personality Response Generated",
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        fontStyle = FontStyle.Italic,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                                    )
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ModelInfoFooter(message: ChatMessage) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "via ${message.modelUsed}",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
        )
        if (message.processingTime != null && message.messageState == AiMessageState.COMPLETE) {
            Text(
                text = "${message.processingTime}ms",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
            )
        }
    }
}