package com.yourname.coquettemobile.ui.components

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
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
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yourname.coquettemobile.core.models.ToolExecution

/**
 * Enhanced Timeline UI for streaming operations
 * Shows organized phases: Thinking → Intent → Tools → Response
 */
@Composable
fun OperationTimelineCard(
    // Phase content
    thinkingContent: String?,
    intentAnalysisMessage: String?,
    toolPlanningMessage: String?, 
    toolExecutions: List<ToolExecution>,
    responseGenerationMessage: String?,
    finalResponse: String?,
    
    // State
    currentPhase: OperationPhase,
    personalityName: String = "AI"
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize()
    ) {
        // Phase 1: Thinking
        if (thinkingContent != null) {
            TimelinePhaseSection(
                icon = Icons.Default.Person,
                title = "$personalityName's Reasoning",
                color = Color(0xFF2196F3), // Blue
                isActive = currentPhase == OperationPhase.THINKING,
                isComplete = currentPhase.ordinal > OperationPhase.THINKING.ordinal
            ) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.7f)
                    )
                ) {
                    Text(
                        text = thinkingContent,
                        modifier = Modifier.padding(12.dp),
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontFamily = FontFamily.Monospace,
                            fontSize = 13.sp
                        ),
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
                    )
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
        }
        
        // Phase 2: Intent Analysis
        if (intentAnalysisMessage != null) {
            TimelinePhaseSection(
                icon = Icons.Default.Search,
                title = "Intent Analysis",
                color = Color(0xFF9C27B0), // Purple
                isActive = currentPhase == OperationPhase.ANALYZING_INTENT,
                isComplete = currentPhase.ordinal > OperationPhase.ANALYZING_INTENT.ordinal
            ) {
                StatusMessage(intentAnalysisMessage)
            }
            Spacer(modifier = Modifier.height(8.dp))
        }
        
        // Phase 3: Tool Planning
        if (toolPlanningMessage != null) {
            TimelinePhaseSection(
                icon = Icons.Default.Settings,
                title = "Tool Planning",
                color = Color(0xFF607D8B), // Blue Grey
                isActive = currentPhase == OperationPhase.PLANNING_TOOLS,
                isComplete = currentPhase.ordinal > OperationPhase.PLANNING_TOOLS.ordinal
            ) {
                StatusMessage(toolPlanningMessage)
            }
            Spacer(modifier = Modifier.height(8.dp))
        }
        
        // Phase 4: Tool Execution
        if (toolExecutions.isNotEmpty()) {
            TimelinePhaseSection(
                icon = Icons.Default.PlayArrow,
                title = "Tool Execution",
                color = Color(0xFF4CAF50), // Green
                isActive = currentPhase == OperationPhase.EXECUTING_TOOL,
                isComplete = currentPhase.ordinal > OperationPhase.EXECUTING_TOOL.ordinal
            ) {
                Column {
                    toolExecutions.forEachIndexed { index, execution ->
                        ToolExecutionItem(
                            execution = execution,
                            index = index + 1,
                            total = toolExecutions.size
                        )
                        if (index < toolExecutions.size - 1) {
                            Spacer(modifier = Modifier.height(8.dp))
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
        }
        
        // Phase 5: Response Generation
        if (responseGenerationMessage != null) {
            TimelinePhaseSection(
                icon = Icons.Default.Edit,
                title = "Response Generation", 
                color = Color(0xFFFF9800), // Orange
                isActive = currentPhase == OperationPhase.GENERATING_RESPONSE,
                isComplete = currentPhase.ordinal > OperationPhase.GENERATING_RESPONSE.ordinal
            ) {
                StatusMessage(responseGenerationMessage)
            }
            Spacer(modifier = Modifier.height(8.dp))
        }
        
        // Phase 6: Final Response
        if (finalResponse != null && finalResponse.isNotBlank()) {
            TimelinePhaseSection(
                icon = Icons.Default.Done,
                title = "$personalityName's Response",
                color = Color(0xFF673AB7), // Deep Purple
                isActive = false,
                isComplete = true,
                startExpanded = true
            ) {
                androidx.compose.foundation.text.selection.SelectionContainer {
                    StreamingRichText(
                        content = finalResponse.take(50000),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }
    }
}

@Composable
fun TimelinePhaseSection(
    icon: ImageVector,
    title: String,
    color: Color,
    isActive: Boolean,
    isComplete: Boolean,
    startExpanded: Boolean = false,
    content: @Composable () -> Unit
) {
    var isExpanded by remember { mutableStateOf(startExpanded) }
    val rotationAngle by animateFloatAsState(
        targetValue = if (isExpanded) 90f else 0f,
        animationSpec = tween(300)
    )
    
    Column {
        // Phase Header
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { isExpanded = !isExpanded },
            colors = CardDefaults.cardColors(
                containerColor = when {
                    isActive -> color.copy(alpha = 0.2f)
                    isComplete -> color.copy(alpha = 0.1f)
                    else -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                }
            )
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Status Icon
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .background(
                            color = color.copy(alpha = 0.2f),
                            shape = RoundedCornerShape(8.dp)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    when {
                        isActive -> CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp,
                            color = color
                        )
                        isComplete -> Icon(
                            Icons.Default.CheckCircle,
                            contentDescription = "Complete",
                            tint = color,
                            modifier = Modifier.size(16.dp)
                        )
                        else -> Icon(
                            icon,
                            contentDescription = title,
                            tint = color.copy(alpha = 0.7f),
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
                
                // Phase Title
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = if (isActive) FontWeight.Bold else FontWeight.Medium
                    ),
                    color = when {
                        isActive -> color
                        isComplete -> MaterialTheme.colorScheme.onSurface
                        else -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                    },
                    modifier = Modifier.weight(1f)
                )
                
                // Expand/Collapse Arrow
                Icon(
                    Icons.Default.KeyboardArrowRight,
                    contentDescription = if (isExpanded) "Collapse" else "Expand",
                    tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                    modifier = Modifier.rotate(rotationAngle)
                )
            }
        }
        
        // Phase Content
        if (isExpanded) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.5f)
                )
            ) {
                Box(
                    modifier = Modifier.padding(12.dp)
                ) {
                    content()
                }
            }
        }
    }
}

@Composable
fun ToolExecutionItem(
    execution: ToolExecution,
    index: Int,
    total: Int
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Tool Status Icon
        Box(
            modifier = Modifier
                .size(24.dp)
                .background(
                    color = if (execution.wasSuccessful) 
                        Color(0xFF4CAF50).copy(alpha = 0.2f)
                    else 
                        Color(0xFFF44336).copy(alpha = 0.2f),
                    shape = RoundedCornerShape(4.dp)
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                if (execution.wasSuccessful) Icons.Default.CheckCircle else Icons.Default.Close,
                contentDescription = if (execution.wasSuccessful) "Success" else "Failed",
                tint = if (execution.wasSuccessful) Color(0xFF4CAF50) else Color(0xFFF44336),
                modifier = Modifier.size(14.dp)
            )
        }
        
        // Tool Info
        Column(modifier = Modifier.weight(1f)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = execution.toolName,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontWeight = FontWeight.Medium
                    ),
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "($index/$total)",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
            }
            
            // Execution time
            val executionTime = if (execution.endTime != null) {
                execution.endTime - execution.startTime
            } else null
            
            if (executionTime != null) {
                Text(
                    text = "${executionTime}ms",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                )
            }
        }
    }
}

@Composable
fun StatusMessage(message: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        CircularProgressIndicator(
            modifier = Modifier.size(14.dp),
            strokeWidth = 2.dp,
            color = MaterialTheme.colorScheme.primary
        )
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
        )
    }
}

enum class OperationPhase {
    THINKING,
    ANALYZING_INTENT,
    PLANNING_TOOLS,
    EXECUTING_TOOL,
    GENERATING_RESPONSE,
    COMPLETE
}