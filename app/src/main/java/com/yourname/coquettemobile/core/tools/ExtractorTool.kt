package com.yourname.coquettemobile.core.tools

import android.text.Html
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject

/**
 * ExtractorTool - Convert HTML to readable text
 * Simplified version of desktop Coquette's HTML extraction
 */
class ExtractorTool @Inject constructor() : MobileTool {

    override val name = "ExtractorTool"
    override val description = "Extract readable text content from HTML"
    override val riskLevel = RiskLevel.LOW
    override val requiredPermissions = emptyList<String>()

    override suspend fun execute(parameters: Map<String, Any>): ToolResult = withContext(Dispatchers.Default) {
        executeStreaming(parameters) { /* ignore progress for sync execution */ }
    }

    override suspend fun executeStreaming(
        parameters: Map<String, Any>,
        onProgress: (String) -> Unit
    ): ToolResult = withContext(Dispatchers.Default) {
        val html = parameters["html"] as? String ?: parameters["content"] as? String
        ?: return@withContext ToolResult.error("Missing required parameter: html or content")

        try {
            onProgress("Starting HTML extraction...")

            // Use Android's built-in HTML parser for basic extraction
            onProgress("Parsing HTML content...")
            val cleanText = Html.fromHtml(html, Html.FROM_HTML_MODE_LEGACY)
            .toString()
            .trim()

            onProgress("Cleaning extracted text...")
            // Clean up excessive whitespace
            val normalizedText = cleanText
            .replace(Regex("\\s+"), " ")
            .replace(Regex("\\n\\s*\\n\\s*\\n"), "\n\n")
            .trim()

            val wordCount = normalizedText.split("\\s+".toRegex()).size

            onProgress("Extraction complete: $wordCount words, ${normalizedText.length} characters")

            ToolResult.success(
                output = normalizedText,
                metadata = mapOf(
                    "word_count" to wordCount,
                    "character_count" to normalizedText.length,
                    "extraction_method" to "android_html_parser"
                )
            )

        } catch (e: Exception) {
            val error = "Failed to extract text from HTML: ${e.message}"
            onProgress(error)
            ToolResult.error(error)
        }
    }

    override fun getDescription(params: Map<String, Any>): String {
        return "Converting HTML content to readable text"
    }

    override fun validateParams(params: Map<String, Any>): String? {
        val html = params["html"] as? String ?: params["content"] as? String
        return when {
            html.isNullOrBlank() -> "html or content parameter is required"
            html.length > 500_000 -> "HTML content too large (max 500KB)"
            else -> null
        }
    }

    override fun getParameterSchema(): String {
        return """
        Parameters:
        - html (string): The HTML content to extract readable text from.
        Maximum size: 500KB
        - content (string): Alternative parameter for the HTML content.
        Maximum size: 500KB

        Example: {"html": "<html><body><p>Content to extract</p></body></html>"}
        Example: {"content": "<html><body><p>Content to extract</p></body></html>"}
        """.trimIndent()
    }
}
