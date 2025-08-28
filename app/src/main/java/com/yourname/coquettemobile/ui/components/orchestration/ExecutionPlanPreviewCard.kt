package com.yourname.coquettemobile.ui.components.orchestration

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yourname.coquettemobile.core.orchestration.ExecutionPlan
import com.yourname.coquettemobile.core.orchestration.OperationStep
import com.yourname.coquettemobile.core.orchestration.RouterDomain
import com.yourname.coquettemobile.core.tools.RiskLevel

/**
 * Displays execution plan preview with user control options
 * Shows the "Mission Control" view of what the AI team is about to do
 */
@Composable
fun ExecutionPlanPreviewCard(
    executionPlan: ExecutionPlan,
    personalityName: String,
    onExecute: () -> Unit,
    onModify: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier
) {
    var isExpanded by remember { mutableStateOf(false) }
    
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
                .animateContentSize()
        ) {
            // Header with personality and operation summary
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "🎯 $personalityName's Plan",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = executionPlan.originalIntent,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                
                // Risk indicator
                RiskIndicator(executionPlan.riskLevel)
            }
            
            Spacer(modifier = Modifier.height(12.dp))
            
            // Operation metadata
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                MetadataChip(
                    icon = Icons.Default.Settings,
                    label = "~${formatDuration(executionPlan.estimatedDurationMs)}",
                    color = MaterialTheme.colorScheme.primary
                )
                
                MetadataChip(
                    icon = Icons.Default.List,
                    label = "${executionPlan.steps.size} steps",
                    color = MaterialTheme.colorScheme.secondary
                )
                
                TextButton(
                    onClick = { isExpanded = !isExpanded }
                ) {
                    Text(if (isExpanded) "Less" else "Details")
                    Icon(
                        imageVector = if (isExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                        contentDescription = null
                    )
                }
            }
            
            // Expandable step details
            if (isExpanded) {
                Spacer(modifier = Modifier.height(12.dp))
                
                Text(
                    text = "Execution Steps:",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Medium
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                
                executionPlan.steps.forEachIndexed { index, step ->
                    OperationStepPreview(
                        step = step,
                        stepNumber = index + 1,
                        modifier = Modifier.padding(vertical = 4.dp)
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Action buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                OutlinedButton(
                    onClick = onCancel,
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Icon(Icons.Default.Close, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("No")
                }
                
                OutlinedButton(
                    onClick = onModify
                ) {
                    Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Edit")
                }
                
                Button(
                    onClick = onExecute,
                    enabled = executionPlan.steps.isNotEmpty(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = when (executionPlan.riskLevel) {
                            RiskLevel.LOW -> MaterialTheme.colorScheme.primary
                            RiskLevel.MEDIUM -> Color(0xFFFF9800)
                            RiskLevel.HIGH -> Color(0xFFFF5722)
                            RiskLevel.CRITICAL -> Color(0xFFD32F2F)
                        }
                    )
                ) {
                    Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Yes")
                }
            }
        }
    }
}

/**
 * Individual operation step preview
 */
@Composable
private fun OperationStepPreview(
    step: OperationStep,
    stepNumber: Int,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(
                color = MaterialTheme.colorScheme.surface,
                shape = RoundedCornerShape(8.dp)
            )
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Step number indicator
        Box(
            modifier = Modifier
                .size(24.dp)
                .background(
                    color = getRouterColor(step.domain),
                    shape = RoundedCornerShape(50)
                ),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = stepNumber.toString(),
                style = MaterialTheme.typography.bodySmall,
                color = Color.White,
                fontWeight = FontWeight.Bold
            )
        }
        
        Spacer(modifier = Modifier.width(12.dp))
        
        Column(modifier = Modifier.weight(1f)) {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = getRouterIcon(step.domain),
                    fontSize = 14.sp
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = formatRouterName(step.domain),
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Medium,
                    color = getRouterColor(step.domain)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "→",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = step.type.name.replace("_", " ").lowercase().capitalize(),
                    style = MaterialTheme.typography.bodySmall
                )
            }
            
            Text(
                text = step.description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            
            if (step.dependencies.isNotEmpty()) {
                Text(
                    text = "Depends on: ${step.dependencies.joinToString(", ")}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline
                )
            }
        }
        
        // Duration estimate
        Text(
            text = formatDuration(step.estimatedDurationMs),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.outline
        )
    }
}

/**
 * Risk level indicator
 */
@Composable
private fun RiskIndicator(riskLevel: RiskLevel) {
    val (color, icon, text) = when (riskLevel) {
        RiskLevel.LOW -> Triple(Color(0xFF4CAF50), "🟢", "LOW RISK")
        RiskLevel.MEDIUM -> Triple(Color(0xFFFF9800), "🟡", "MEDIUM RISK")  
        RiskLevel.HIGH -> Triple(Color(0xFFFF5722), "🟠", "HIGH RISK")
        RiskLevel.CRITICAL -> Triple(Color(0xFFD32F2F), "🔴", "CRITICAL")
    }
    
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .background(
                color = color.copy(alpha = 0.1f),
                shape = RoundedCornerShape(12.dp)
            )
            .padding(horizontal = 8.dp, vertical = 4.dp)
    ) {
        Text(text = icon, fontSize = 12.sp)
        Spacer(modifier = Modifier.width(4.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = color,
            fontWeight = FontWeight.Bold
        )
    }
}

/**
 * Metadata chip component
 */
@Composable
private fun MetadataChip(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    color: Color
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .background(
                color = color.copy(alpha = 0.1f),
                shape = RoundedCornerShape(12.dp)
            )
            .padding(horizontal = 8.dp, vertical = 4.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = color,
            modifier = Modifier.size(14.dp)
        )
        Spacer(modifier = Modifier.width(4.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = color
        )
    }
}

// Utility functions
private fun getRouterColor(domain: RouterDomain): Color {
    return when (domain) {
        RouterDomain.ANDROID_INTELLIGENCE -> Color(0xFF2196F3)
        RouterDomain.DESKTOP_EXPLOIT -> Color(0xFFFF5722)  
        RouterDomain.NETWORK_OPERATIONS -> Color(0xFF4CAF50)
        RouterDomain.FILE_OPERATIONS -> Color(0xFFFF9800)
        RouterDomain.DEVICE_CONTROL -> Color(0xFF9C27B0)
        RouterDomain.SECURITY_OPERATIONS -> Color(0xFFD32F2F)
        RouterDomain.DATA_PROCESSING -> Color(0xFF607D8B)
        RouterDomain.COMMUNICATION -> Color(0xFF00BCD4)
        RouterDomain.WEB_INTELLIGENCE -> Color(0xFF0891B2)
    }
}

private fun getRouterIcon(domain: RouterDomain): String {
    return when (domain) {
        RouterDomain.ANDROID_INTELLIGENCE -> "🔍"
        RouterDomain.DESKTOP_EXPLOIT -> "🖱️"
        RouterDomain.NETWORK_OPERATIONS -> "🌐" 
        RouterDomain.FILE_OPERATIONS -> "📁"
        RouterDomain.DEVICE_CONTROL -> "⌨️"
        RouterDomain.SECURITY_OPERATIONS -> "🔒"
        RouterDomain.DATA_PROCESSING -> "⚙️"
        RouterDomain.COMMUNICATION -> "📡"
        RouterDomain.WEB_INTELLIGENCE -> "🌍"
    }
}

private fun formatRouterName(domain: RouterDomain): String {
    return domain.name.split("_").joinToString(" ") { it.lowercase().capitalize() }
}

private fun formatDuration(milliseconds: Long): String {
    val seconds = milliseconds / 1000
    return when {
        seconds < 60 -> "${seconds}s"
        seconds < 3600 -> "${seconds / 60}m ${seconds % 60}s" 
        else -> "${seconds / 3600}h ${(seconds % 3600) / 60}m"
    }
}