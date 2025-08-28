package com.yourname.coquettemobile.ui.components.orchestration

import androidx.compose.animation.*
import androidx.compose.animation.core.*
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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.graphicsLayer
import com.yourname.coquettemobile.core.orchestration.OperationResult
import com.yourname.coquettemobile.core.orchestration.RouterDomain
import com.yourname.coquettemobile.core.orchestration.StepResult

/**
 * Real-time progress display for orchestrated operations
 * Shows the "Mission Control" view of AI specialists working
 */
@Composable
fun LiveOperationProgress(
    operationTitle: String,
    personalityName: String,
    stepResults: List<StepResult>,
    currentStepIndex: Int = -1,
    isComplete: Boolean = false,
    onPause: (() -> Unit)? = null,
    onStop: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (isComplete) 
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
            else
                MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.7f)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 6.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            // Header with operation title and controls
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (isComplete) {
                            Text("✅", fontSize = 16.sp)
                        } else {
                            PulsingIcon()
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (isComplete) "Operation Complete" else "$personalityName Working...",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Text(
                        text = operationTitle,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                
                // Control buttons (only show during active operation)
                if (!isComplete && (onPause != null || onStop != null)) {
                    Row {
                        onPause?.let {
                            IconButton(onClick = it) {
                                Icon(
                                    Icons.Default.PlayArrow, // Using PlayArrow as Pause alternative
                                    contentDescription = "Pause",
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                        onStop?.let {
                            IconButton(onClick = it) {
                                Icon(
                                    Icons.Default.Close, // Using Close as Stop alternative
                                    contentDescription = "Stop", 
                                    tint = MaterialTheme.colorScheme.error
                                )
                            }
                        }
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(12.dp))
            
            // Progress overview
            ProgressOverview(stepResults, currentStepIndex, isComplete)
            
            Spacer(modifier = Modifier.height(12.dp))
            
            // Step details
            stepResults.forEachIndexed { index, stepResult ->
                StepProgressItem(
                    stepResult = stepResult,
                    stepNumber = index + 1,
                    isActive = index == currentStepIndex,
                    modifier = Modifier.padding(vertical = 4.dp)
                )
            }
        }
    }
}

/**
 * Pulsing activity indicator
 */
@Composable
private fun PulsingIcon() {
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "alpha"
    )
    
    Text(
        text = "🎯",
        fontSize = 16.sp,
        modifier = Modifier.graphicsLayer { this.alpha = alpha }
    )
}

/**
 * Overall progress summary
 */
@Composable
private fun ProgressOverview(
    stepResults: List<StepResult>,
    currentStepIndex: Int,
    isComplete: Boolean
) {
    val completedSteps = stepResults.count { it.success }
    val failedSteps = stepResults.count { !it.success && it.stepId.isNotEmpty() } // Exclude pending
    val totalSteps = stepResults.size
    val progress = if (totalSteps > 0) completedSteps.toFloat() / totalSteps else 0f
    
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = if (isComplete) {
                    "✅ $completedSteps/$totalSteps steps completed" + 
                    if (failedSteps > 0) " ($failedSteps failed)" else ""
                } else {
                    "🔄 Step ${currentStepIndex + 1}/$totalSteps in progress..."
                },
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Medium
            )
            
            Text(
                text = "${(progress * 100).toInt()}%",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary
            )
        }
        
        Spacer(modifier = Modifier.height(8.dp))
        
        LinearProgressIndicator(
            progress = progress,
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp)
                .clip(RoundedCornerShape(4.dp)),
            color = if (failedSteps > 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
        )
    }
}

/**
 * Individual step progress item
 */
@Composable
private fun StepProgressItem(
    stepResult: StepResult,
    stepNumber: Int,
    isActive: Boolean,
    modifier: Modifier = Modifier
) {
    val backgroundColor = when {
        stepResult.success -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
        !stepResult.success && stepResult.error != null -> MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f)
        isActive -> MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f)
        else -> MaterialTheme.colorScheme.surface
    }
    
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(
                color = backgroundColor,
                shape = RoundedCornerShape(8.dp)
            )
            .padding(12.dp)
            .animateContentSize(),
        verticalAlignment = Alignment.Top
    ) {
        // Status indicator
        Box(
            modifier = Modifier
                .size(24.dp)
                .background(
                    color = when {
                        stepResult.success -> Color(0xFF4CAF50)
                        !stepResult.success && stepResult.error != null -> Color(0xFFD32F2F)
                        isActive -> Color(0xFF2196F3)
                        else -> Color(0xFF9E9E9E)
                    },
                    shape = RoundedCornerShape(50)
                ),
            contentAlignment = Alignment.Center
        ) {
            when {
                stepResult.success -> Text("✓", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                !stepResult.success && stepResult.error != null -> Text("✗", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                isActive -> {
                    // Animated loading indicator
                    val infiniteTransition = rememberInfiniteTransition(label = "loading")
                    val rotation by infiniteTransition.animateFloat(
                        initialValue = 0f,
                        targetValue = 360f,
                        animationSpec = infiniteRepeatable(
                            animation = tween(1000, easing = LinearEasing)
                        ),
                        label = "rotation"
                    )
                    Text(
                        "⟳",
                        color = Color.White,
                        fontSize = 12.sp,
                        modifier = Modifier.graphicsLayer { rotationZ = rotation }
                    )
                }
                else -> Text(stepNumber.toString(), color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold)
            }
        }
        
        Spacer(modifier = Modifier.width(12.dp))
        
        Column(modifier = Modifier.weight(1f)) {
            // Router and operation type
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = getRouterIcon(stepResult.domain),
                    fontSize = 14.sp
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = formatRouterName(stepResult.domain),
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Medium,
                    color = getRouterColor(stepResult.domain)
                )
                
                if (isActive) {
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "• ACTIVE",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color(0xFF2196F3),
                        fontWeight = FontWeight.Bold
                    )
                }
            }
            
            // Step description or result
            if (stepResult.success && stepResult.data.isNotEmpty()) {
                Text(
                    text = "✅ ${getStepSummary(stepResult)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else if (stepResult.error != null) {
                Text(
                    text = "❌ ${stepResult.error}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            } else if (isActive) {
                Text(
                    text = "🔄 Executing step...",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary
                )
            } else {
                Text(
                    text = "⏸️ Pending",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline
                )
            }
        }
        
        // Execution time
        if (stepResult.executionTimeMs > 0) {
            Text(
                text = formatDuration(stepResult.executionTimeMs),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.outline
            )
        }
    }
}

// Utility functions (reuse from ExecutionPlanPreviewCard)
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
    return domain.name.split("_").joinToString(" ") { it.lowercase().replaceFirstChar { it.uppercase() } }
}

private fun formatDuration(milliseconds: Long): String {
    val seconds = milliseconds / 1000
    return when {
        seconds < 60 -> "${seconds}s"
        seconds < 3600 -> "${seconds / 60}m ${seconds % 60}s" 
        else -> "${seconds / 3600}h ${(seconds % 3600) / 60}m"
    }
}

private fun getStepSummary(stepResult: StepResult): String {
    // Extract key information from step result data
    val dataKeys = stepResult.data.keys.joinToString(", ")
    return when {
        stepResult.data.isEmpty() -> "Completed successfully"
        dataKeys.length > 50 -> "Found ${stepResult.data.size} items"
        else -> "Found: $dataKeys"
    }
}