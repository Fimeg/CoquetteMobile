package com.yourname.coquettemobile.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

/**
 * UltraThink streaming thinking indicator with expandable dropdown
 * - Pulsing indicator when thinking
 * - Streaming text left-to-right in collapsed state
 * - Expandable to show full thinking content
 */
@Composable
fun StreamingThinkingIndicator(
    personalityName: String,
    thinkingContent: String? = null,
    isExpanded: Boolean = false,
    onToggle: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val isThinking = thinkingContent == null
    
    // Pulsing animation for thinking state
    val pulseAlpha by rememberInfiniteTransition(label = "thinking_pulse").animateFloat(
        initialValue = 0.4f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse_alpha"
    )
    
    // Streaming text animation
    var displayText by remember { mutableStateOf("") }
    var isStreaming by remember { mutableStateOf(false) }
    
    LaunchedEffect(thinkingContent) {
        if (thinkingContent != null && !isExpanded) {
            isStreaming = true
            displayText = ""
            val fullText = thinkingContent.take(60) + if (thinkingContent.length > 60) "..." else ""
            
            // Stream text character by character
            for (i in fullText.indices) {
                displayText = fullText.substring(0, i + 1)
                delay(30) // 30ms per character for smooth streaming
            }
            isStreaming = false
        }
    }
    
    Box(
        modifier = modifier.fillMaxWidth(),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            modifier = Modifier
                .clickable(enabled = thinkingContent != null) { onToggle() }
                .alpha(if (isThinking) pulseAlpha else 1f),
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
            tonalElevation = 2.dp
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                // Header with personality name and status
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = "💭",
                        fontSize = 14.sp,
                        modifier = Modifier.padding(end = 6.dp)
                    )
                    Text(
                        text = if (isThinking) "$personalityName is thinking..." else "Thought Process",
                        style = MaterialTheme.typography.labelMedium.copy(
                            fontStyle = FontStyle.Italic
                        ),
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                    )
                    
                    if (thinkingContent != null) {
                        Text(
                            text = if (isExpanded) " ▲" else " ▼",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                            modifier = Modifier.padding(start = 4.dp)
                        )
                    }
                }
                
                // Streaming or expanded thinking content
                if (thinkingContent != null) {
                    if (isExpanded) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.5f)
                        ) {
                            Column(
                                modifier = Modifier.padding(12.dp)
                            ) {
                                RichText(
                                    text = thinkingContent,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.9f),
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        }
                    } else if (displayText.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = displayText,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
        }
    }
}

/**
 * Simple centered thinking indicator for when personality is processing
 */
@Composable
fun CenteredThinkingIndicator(
    personalityName: String,
    modifier: Modifier = Modifier
) {
    val pulseAlpha by rememberInfiniteTransition(label = "thinking").animateFloat(
        initialValue = 0.4f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "alpha"
    )
    
    Box(
        modifier = modifier.fillMaxWidth(),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "$personalityName is thinking...",
            style = MaterialTheme.typography.bodyMedium.copy(
                fontStyle = FontStyle.Italic
            ),
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = pulseAlpha),
            modifier = Modifier.alpha(pulseAlpha)
        )
    }
}