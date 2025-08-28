package com.yourname.coquettemobile.ui.components

import androidx.compose.animation.*
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.layout.ContentScale // Added
import coil.compose.SubcomposeAsyncImage // Added
import androidx.compose.foundation.background // Added
import androidx.compose.foundation.shape.RoundedCornerShape // Added
import androidx.compose.material.icons.filled.Warning // Added
// Removed: import androidx.compose.material.icons.Icons // Removed due to conflict
import androidx.compose.material3.CircularProgressIndicator // Added
import androidx.compose.material3.Icon // Already there, but good to check
import androidx.compose.material3.MaterialTheme // Already there, but good to check
import androidx.compose.ui.draw.clip // Added for clip modifier
import com.yourname.coquettemobile.core.models.ToolExecution

/**
 * UltraThink tool output dropdown with scrollable rich content
 * Supports code blocks, images, and all rich text features
 */
@Composable
fun ToolOutputDropdown(
    toolExecutions: List<ToolExecution>,
    isExpanded: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (toolExecutions.isEmpty()) return
    
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f),
        tonalElevation = 2.dp
    ) {
        Column {
            // Header with tool count and toggle
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onToggle() }
                    .padding(12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Build,
                        contentDescription = "Tools",
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier
                            .size(16.dp)
                            .padding(end = 6.dp)
                    )
                    Text(
                        text = "${toolExecutions.size} tools executed",
                        style = MaterialTheme.typography.labelMedium.copy(
                            fontWeight = FontWeight.Medium
                        ),
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
                
                Row(verticalAlignment = Alignment.CenterVertically) {
                    // Success/error indicators
                    val successful = toolExecutions.count { it.wasSuccessful }
                    val failed = toolExecutions.size - successful
                    
                    if (successful > 0) {
                        Text(
                            text = "✓$successful",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(end = 4.dp)
                        )
                    }
                    if (failed > 0) {
                        Text(
                            text = "✗$failed",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.padding(end = 4.dp)
                        )
                    }
                    
                    Text(
                        text = if (isExpanded) "▲" else "▼",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                    )
                }
            }
            
            // Expandable tool results
            AnimatedVisibility(
                visible = isExpanded,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.small,
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.8f)
                ) {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 400.dp) // Scrollable with max height
                            .padding(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(toolExecutions) { execution ->
                            ToolExecutionItem(execution = execution)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ToolExecutionItem(
    execution: ToolExecution,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.small,
        color = if (execution.wasSuccessful) {
            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        } else {
            MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f)
        },
        tonalElevation = 1.dp
    ) {
        Column(
            modifier = Modifier.padding(12.dp)
        ) {
            // Tool header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = getToolIcon(execution.toolName),
                        fontSize = 16.sp,
                        modifier = Modifier.padding(end = 6.dp)
                    )
                    Text(
                        text = execution.toolName,
                        style = MaterialTheme.typography.titleSmall.copy(
                            fontWeight = FontWeight.Medium
                        ),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
                
                // Execution time and status
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "${execution.endTime - execution.startTime}ms",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                        modifier = Modifier.padding(end = 4.dp)
                    )
                    Text(
                        text = if (execution.wasSuccessful) "✓" else "✗",
                        color = if (execution.wasSuccessful) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.error
                        },
                        fontSize = 12.sp
                    )
                }
            }
            
            // Tool reasoning (if available)
            execution.reasoning?.let { reasoning ->
                if (reasoning.isNotBlank()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = reasoning,
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontStyle = androidx.compose.ui.text.font.FontStyle.Italic
                        ),
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                    )
                }
            }
            
            // Tool arguments (collapsed)
            if (execution.args.isNotEmpty()) {
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Args: ${execution.args.entries.joinToString(", ") { "${it.key}=${it.value.toString().take(30)}${if (it.value.toString().length > 30) "..." else ""}" }}",
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
            }
            
            // Tool result - main content with rich text or image support
            if (execution.result.isNotEmpty() || !execution.imageUrl.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(8.dp))
                Divider(
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f),
                    thickness = 0.5.dp
                )
                Spacer(modifier = Modifier.height(8.dp))

                // Display image if imageUrl is present, otherwise display rich text result
                if (!execution.imageUrl.isNullOrBlank()) {
                    SubcomposeAsyncImage(
                        model = execution.imageUrl,
                        contentDescription = "Image from tool: ${execution.toolName}",
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 350.dp)
                            .clip(MaterialTheme.shapes.small), // Use MaterialTheme shape
                        contentScale = ContentScale.Fit, // Use Fit to ensure full image is visible
                        loading = {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(200.dp)
                                    .background(MaterialTheme.colorScheme.surfaceVariant),
                                contentAlignment = Alignment.Center
                            ) {
                                CircularProgressIndicator()
                            }
                        },
                        error = {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(200.dp)
                                    .background(MaterialTheme.colorScheme.errorContainer),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Warning,
                                    contentDescription = "Error loading image",
                                    tint = MaterialTheme.colorScheme.onErrorContainer
                                )
                            }
                        }
                    )
                } else {
                    // Rich text result with scrollable content
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 200.dp)
                    ) {
                        Column(
                            modifier = Modifier.verticalScroll(rememberScrollState())
                        ) {
                            RichText(
                                text = execution.result,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.9f),
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun getToolIcon(toolName: String): String {
    return when (toolName) {
        "WebFetchTool" -> "🌐"
        "ExtractorTool" -> "📄"
        "SummarizerTool" -> "📝"
        "DeviceContextTool" -> "📱"
        "NotificationTool" -> "🔔"
        "WebImageTool" -> "🖼️"
        else -> "🔧"
    }
}