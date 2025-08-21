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
    
    override suspend fun execute(params: Map<String, Any>): ToolResult = withContext(Dispatchers.Default) {
        val html = params["html"] as? String
            ?: return@withContext ToolResult.error("Missing required parameter: html")
        
        try {
            // Use Android's built-in HTML parser for basic extraction
            val cleanText = Html.fromHtml(html, Html.FROM_HTML_MODE_LEGACY)
                .toString()
                .trim()
            
            // Clean up excessive whitespace
            val normalizedText = cleanText
                .replace(Regex("\\s+"), " ")
                .replace(Regex("\\n\\s*\\n\\s*\\n"), "\n\n")
                .trim()
            
            val wordCount = normalizedText.split("\\s+".toRegex()).size
            
            ToolResult.success(
                output = normalizedText,
                metadata = mapOf(
                    "word_count" to wordCount,
                    "character_count" to normalizedText.length,
                    "extraction_method" to "android_html_parser"
                )
            )
            
        } catch (e: Exception) {
            ToolResult.error("Failed to extract text from HTML: ${e.message}")
        }
    }
    
    override fun getDescription(params: Map<String, Any>): String {
        return "Converting HTML content to readable text"
    }
    
    override fun validateParams(params: Map<String, Any>): String? {
        val html = params["html"] as? String
        return when {
            html.isNullOrBlank() -> "HTML parameter is required"
            html.length > 500_000 -> "HTML content too large (max 500KB)"
            else -> null
        }
    }
}