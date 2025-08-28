package com.yourname.coquettemobile.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.ClickableText
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.ParagraphStyle
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextIndent
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.withStyle

/**
 * Rich text component that supports markdown-style formatting
 * - **bold**, *italic*, `code`, ```code blocks```
 * - Lists (* or -)
 * - Links [text](url)
 */
@Composable
fun RichText(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.onSurface
) {
    val linkColor = MaterialTheme.colorScheme.primary
    val cleanedText = preprocessMessageText(text)
    val annotatedString = buildAnnotatedString {
        parseAndApplyFormatting(cleanedText, color, linkColor)
    }
    val uriHandler = LocalUriHandler.current

    ClickableText(
        text = annotatedString,
        modifier = modifier,
        style = MaterialTheme.typography.bodyMedium.copy(color = color),
        onClick = { offset ->
            annotatedString.getStringAnnotations(tag = "URL", start = offset, end = offset)
                .firstOrNull()?.let { annotation ->
                    try {
                        uriHandler.openUri(annotation.item)
                    } catch (e: Exception) {
                        android.util.Log.w("RichText", "Failed to open URI: ${annotation.item}", e)
                    }
                }
        }
    )
}

private fun androidx.compose.ui.text.AnnotatedString.Builder.parseAndApplyFormatting(
    text: String,
    baseColor: Color,
    linkColor: Color
) {
    val lines = text.lines()
    lines.forEachIndexed { index, line ->
        val trimmedLine = line.trimStart()
        if ((trimmedLine.startsWith("*") || trimmedLine.startsWith("- ")) && (trimmedLine.length > 2 && trimmedLine[1] == ' ')) {
            withStyle(style = ParagraphStyle(textIndent = TextIndent(firstLine = 0.sp, restLine = 12.sp))) {
                append("• ")
                parseInlineFormatting(trimmedLine.substring(2), baseColor, linkColor)
            }
        } else {
            parseInlineFormatting(line, baseColor, linkColor)
        }
        
        // Add newline unless this is the last line
        if (index < lines.size - 1) {
            append("\n")
        }
    }
}

private fun androidx.compose.ui.text.AnnotatedString.Builder.parseInlineFormatting(
    text: String,
    baseColor: Color,
    linkColor: Color
) {
    var currentIndex = 0
    val length = text.length
    val linkRegex = """\\[([^\]]+)\\]\\(([^)]+)\\)""".toRegex()

    while (currentIndex < length) {
        val remainingText = text.substring(currentIndex)
        val linkMatch = linkRegex.find(remainingText)

        val nextMatchIndex = linkMatch?.range?.first ?: -1

        // Find the nearest special character for non-regex parsing
        var nextSpecialCharIndex = -1
        val specialChars = listOf("**", "*", "`", "```")
        for (char in specialChars) {
            val index = remainingText.indexOf(char)
            if (index != -1 && (nextSpecialCharIndex == -1 || index < nextSpecialCharIndex)) {
                nextSpecialCharIndex = index
            }
        }

        val nextParseIndex = when {
            nextMatchIndex != -1 && nextSpecialCharIndex != -1 -> minOf(nextMatchIndex, nextSpecialCharIndex)
            nextMatchIndex != -1 -> nextMatchIndex
            nextSpecialCharIndex != -1 -> nextSpecialCharIndex
            else -> -1
        }

        if (nextParseIndex != -1) {
            // Append text before the next formatting
            if (nextParseIndex > 0) {
                append(remainingText.substring(0, nextParseIndex))
                currentIndex += nextParseIndex
            }

            // Handle the format that we found
            if (nextParseIndex == nextMatchIndex) { // It's a link
                val (displayText, url) = linkMatch!!.destructured
                pushStringAnnotation(tag = "URL", annotation = url)
                withStyle(SpanStyle(color = linkColor, textDecoration = TextDecoration.Underline)) {
                    append(displayText)
                }
                pop()
                currentIndex += linkMatch.value.length
            } else { // It's a non-regex format
                when {
                    text.startsWith("```", currentIndex) -> {
                        val endIndex = text.indexOf("```", currentIndex + 3)
                        if (endIndex != -1) {
                            val codeText = text.substring(currentIndex + 3, endIndex)
                            withStyle(SpanStyle(fontFamily = FontFamily.Monospace, background = baseColor.copy(alpha = 0.1f), fontWeight = FontWeight.Medium)) {
                                append(codeText)
                            }
                            currentIndex = endIndex + 3
                        } else { append(text[currentIndex]); currentIndex++ }
                    }
                    text.startsWith("`", currentIndex) -> {
                        val endIndex = text.indexOf("`", currentIndex + 1)
                        if (endIndex != -1) {
                            val codeText = text.substring(currentIndex + 1, endIndex)
                            withStyle(SpanStyle(fontFamily = FontFamily.Monospace, background = baseColor.copy(alpha = 0.1f))) {
                                append(codeText)
                            }
                            currentIndex = endIndex + 1
                        } else { append(text[currentIndex]); currentIndex++ }
                    }
                    text.startsWith("**", currentIndex) -> {
                        val endIndex = text.indexOf("**", currentIndex + 2)
                        if (endIndex != -1) {
                            val boldText = text.substring(currentIndex + 2, endIndex)
                            withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
                                append(boldText)
                            }
                            currentIndex = endIndex + 2
                        } else { append(text[currentIndex]); currentIndex++ }
                    }
                    text.startsWith("*", currentIndex) -> {
                        val endIndex = text.indexOf("*", currentIndex + 1)
                        if (endIndex != -1) {
                            val italicText = text.substring(currentIndex + 1, endIndex)
                            withStyle(SpanStyle(fontStyle = FontStyle.Italic)) {
                                append(italicText)
                            }
                            currentIndex = endIndex + 1
                        } else { append(text[currentIndex]); currentIndex++ }
                    }
                    else -> { append(text[currentIndex]); currentIndex++ }
                }
            }
        } else {
            // No more formatting found, append the rest of the text
            append(remainingText)
            currentIndex = length
        }
    }
}

/**
 * Preprocesses message text to remove unnecessary whitespace and normalize formatting
 * - Trims leading and trailing whitespace
 * - Removes consecutive blank lines
 * - Normalizes line endings
 * - Fixes bullet point spacing issues
 */
private fun preprocessMessageText(text: String): String {
    if (text.isBlank()) return text
    
    // Trim leading and trailing whitespace
    var processed = text.trim()
    
    // Fix bullet point formatting - ensure proper spacing after bullets
    processed = processed.replace(Regex("^\\s*[*-]\\s+", RegexOption.MULTILINE), "* ")
    
    // Remove consecutive blank lines (more than one empty line in a row)
    processed = processed.replace(Regex("(\n\\s*){3,}"), "\n\n")
    
    // Remove trailing newlines at the end but keep structure
    processed = processed.replace(Regex("\\n+$"), "")
    
    return processed
}

@Composable
fun CodeBlock(
    code: String,
    language: String? = null,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .background(
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                RoundedCornerShape(8.dp)
            )
            .padding(12.dp)
    ) {
        Text(
            text = code,
            fontFamily = FontFamily.Monospace,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
