package com.yourname.coquettemobile.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

/**
 * Unified streaming-first rich text component for UltraThink architecture
 * - Full streaming support for all content types
 * - Clean, minimal UI without complex nested components
 * - Works with images, thinking, tools, and regular content
 */
@Composable
fun StreamingRichText(
    content: String,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.onSurface,
    isStreaming: Boolean = false,
    showThinking: Boolean = false,
    thinkingContent: String? = null
) {
    Column(modifier = modifier) {
        // Optional thinking section - simple and clean
        if (showThinking && !thinkingContent.isNullOrBlank()) {
            ThinkingSection(
                content = thinkingContent,
                isStreaming = isStreaming,
                color = color.copy(alpha = 0.7f)
            )
            Spacer(modifier = Modifier.height(8.dp))
        }
        
        // Main content with streaming cursor
        Row {
            RichText(
                text = content,
                color = color,
                modifier = Modifier.weight(1f)
            )
            
            // Simple streaming indicator - no complex animations
            if (isStreaming) {
                StreamingCursor()
            }
        }
    }
}

/**
 * Simple thinking section - no complex dropdowns or nested components
 */
@Composable
private fun ThinkingSection(
    content: String,
    isStreaming: Boolean,
    color: Color
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
        shape = MaterialTheme.shapes.small,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(12.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(bottom = 4.dp)
            ) {
                Text(
                    text = "💭",
                    fontSize = 14.sp,
                    modifier = Modifier.padding(end = 6.dp)
                )
                Text(
                    text = if (isStreaming) "Thinking..." else "Thought Process",
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontWeight = FontWeight.Medium
                    ),
                    color = color
                )
            }
            
            RichText(
                text = content,
                color = color.copy(alpha = 0.9f),
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

/**
 * Minimal streaming cursor - no complex animations
 */
@Composable
private fun StreamingCursor() {
    val alpha by rememberInfiniteTransition(label = "cursor").animateFloat(
        initialValue = 0.3f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(800),
            repeatMode = RepeatMode.Reverse
        ),
        label = "cursor_alpha"
    )
    
    Text(
        text = "▎",
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier
            .alpha(alpha)
            .padding(start = 2.dp)
    )
}

/**
 * Streaming tool execution component - replaces complex tool dropdowns
 */
@Composable
fun StreamingToolExecution(
    toolName: String,
    progress: String,
    result: String? = null,
    modifier: Modifier = Modifier
) {
    Surface(
        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f),
        shape = MaterialTheme.shapes.small,
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(12.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(bottom = 4.dp)
            ) {
                Text(
                    text = "🔧",
                    fontSize = 14.sp,
                    modifier = Modifier.padding(end = 6.dp)
                )
                Text(
                    text = toolName,
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontWeight = FontWeight.Medium
                    ),
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
            
            if (progress.isNotBlank()) {
                Text(
                    text = progress,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f),
                    modifier = Modifier.padding(bottom = 4.dp)
                )
            }
            
            result?.let { resultText ->
                if (resultText.isNotBlank()) {
                    RichText(
                        text = resultText,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.9f)
                    )
                }
            }
        }
    }
}

/**
 * Image display component with streaming support
 */
@Composable
fun StreamingImageDisplay(
    imageUrl: String,
    description: String? = null,
    modifier: Modifier = Modifier
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
        shape = MaterialTheme.shapes.medium,
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(12.dp)
        ) {
            if (!description.isNullOrBlank()) {
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
            }
            
            // Simple image display - could be enhanced with AsyncImage later
            Text(
                text = "🖼️ Image: $imageUrl",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}