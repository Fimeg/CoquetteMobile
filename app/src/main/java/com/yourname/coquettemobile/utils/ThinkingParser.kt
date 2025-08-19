package com.yourname.coquettemobile.utils

data class ParsedResponse(
    val content: String,
    val thinkingContent: String?
)

object ThinkingParser {
    
    fun parseThinkingTags(response: String): ParsedResponse {
        val thinkingRegex = """<think>(.*?)</think>""".toRegex(RegexOption.DOT_MATCHES_ALL)
        
        // Extract thinking content
        val thinkingMatches = thinkingRegex.findAll(response)
        val thinkingContent = thinkingMatches.joinToString("\n\n") { it.groupValues[1].trim() }
        
        // Remove thinking tags from main content
        val cleanContent = response
            .replace(thinkingRegex, "")
            .trim()
            .replace(Regex("\n{3,}"), "\n\n") // Clean up multiple newlines
        
        return ParsedResponse(
            content = cleanContent.ifEmpty { response }, // Fallback if parsing fails
            thinkingContent = thinkingContent.takeIf { it.isNotBlank() }
        )
    }
}