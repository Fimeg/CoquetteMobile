package com.yourname.coquettemobile.core.ai

import com.yourname.coquettemobile.core.tools.ToolResult
import com.yourname.coquettemobile.core.models.ChatMessage
import com.yourname.coquettemobile.core.database.entities.Personality
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Mobile Error Recovery Agent - Inspired by desktop Coquette's ErrorRecoveryAgent
 * 
 * Analyzes tool failures and generates recovery strategies with:
 * - JSON-structured recovery attempts with reasoning chains
 * - Alternative tool strategies for different failure types
 * - User clarification generation when recovery isn't possible
 * - Mobile-specific constraints (battery, network, permissions)
 */
@Singleton
class MobileErrorRecoveryAgent @Inject constructor(
    private val ollamaService: OllamaService,
    private val appPreferences: com.yourname.coquettemobile.core.preferences.AppPreferences,
    private val coquetteLogger: com.yourname.coquettemobile.core.logging.CoquetteLogger
) {
    
    @Serializable
    data class RecoveryStrategy(
        val recoveryPossible: Boolean,
        val reasoning: String,
        val alternativeTools: List<AlternativeToolSuggestion> = emptyList(),
        val userQuestion: String? = null,
        val confidence: Float = 0.0f
    )
    
    @Serializable  
    data class AlternativeToolSuggestion(
        val toolName: String,
        val args: Map<String, String>,
        val reasoning: String,
        val priority: Int = 1
    )
    
    @Serializable
    data class RecoveryResult(
        val success: Boolean,
        val newToolResults: List<ToolResult> = emptyList(),
        val userMessage: String? = null
    )
    
    /**
     * Analyzes why a tool chain failed and suggests recovery strategies
     * Uses desktop Coquette's pattern with mobile adaptations
     */
    suspend fun analyzeFailure(
        failedTool: String,
        toolResult: ToolResult,
        originalRequest: String
    ): RecoveryStrategy {
        coquetteLogger.d("MobileErrorRecovery", "Analyzing failure: $failedTool -> ${toolResult.output.take(100)}")
        
        val recoveryPrompt = buildRecoveryPrompt(failedTool, toolResult, originalRequest)
        
        return try {
            val result = ollamaService.generateResponse(
                model = appPreferences.errorRecoveryModel, // Configurable model, defaults to Jan 4B
                prompt = recoveryPrompt,
                options = mapOf(
                    "temperature" to 0.4f, // Lower temperature for focused analysis
                    "num_ctx" to 8192
                )
            )
            
            parseRecoveryResponse(result.getOrThrow())
            
        } catch (e: Exception) {
            coquetteLogger.e("MobileErrorRecovery", "Failed to analyze: ${e.message}")
            RecoveryStrategy(
                recoveryPossible = false,
                reasoning = "Analysis failed: ${e.message}",
                userQuestion = "I encountered an issue analyzing the failure. Could you rephrase your request?"
            )
        }
    }
    
    /**
     * Detects if tool results are actually useful content vs JavaScript/headers/noise
     * Critical for Slashdot-type failures where extraction gets only JS code
     */
    suspend fun validateToolResults(
        toolName: String,
        toolResult: ToolResult
    ): Boolean {
        // Quick heuristic checks first
        when (toolName) {
            "ExtractorTool" -> {
                val content = toolResult.output
                
                // Check for JavaScript-heavy content (Slashdot issue)
                val jsIndicators = listOf(
                    "window.", "document.", "function(", "var ", ".js",
                    "script", "bizx.cmp", "googletag", "addEventListener"
                )
                val jsMatches = jsIndicators.count { content.contains(it, ignoreCase = true) }
                
                // Check for actual content indicators
                val contentIndicators = listOf(
                    "article", "news", "story", "headline", "paragraph", 
                    "content", "text", "post", "comment"
                )
                val contentMatches = contentIndicators.count { content.contains(it, ignoreCase = true) }
                
                // If mostly JavaScript with little content, it's likely a failure
                if (jsMatches > 5 && contentMatches < 2 && content.length > 1000) {
                    coquetteLogger.w("MobileErrorRecovery", "ExtractorTool got JavaScript-heavy content, likely SPA failure")
                    return false
                }
                
                // Check for very short extractions (likely failed)
                if (content.trim().length < 50) {
                    coquetteLogger.w("MobileErrorRecovery", "ExtractorTool got very short content")
                    return false
                }
            }
            
            "WebFetchTool" -> {
                // Check if we got actual HTML vs error pages
                if (toolResult.output.contains("404", ignoreCase = true) ||
                    toolResult.output.contains("error", ignoreCase = true) ||
                    toolResult.output.contains("not found", ignoreCase = true)) {
                    return false
                }
            }
        }
        
        return true // Passed basic validation
    }
    
    private fun buildRecoveryPrompt(
        failedTool: String,
        toolResult: ToolResult,
        originalRequest: String
    ): String {
        return """
You are a mobile AI error recovery agent. A tool execution failed or returned poor results.

FAILURE ANALYSIS:
- Tool: $failedTool
- Original request: "$originalRequest"  
- Tool result: "${toolResult.output.take(500)}"
- Success: ${toolResult.success}

AVAILABLE ALTERNATIVE TOOLS:
- WebFetchTool: Fetch web content (try desktop URLs for mobile sites)
- ExtractorTool: Extract readable text from HTML
- SummarizerTool: Summarize and clean up messy content
- DeviceContextTool: Get device info if needed
- NotificationTool: Notify user of issues

MOBILE CONSTRAINTS:
- Battery usage (prefer fewer, more targeted requests)
- Network data usage (avoid large fetches on cellular)
- Processing power (mobile-optimized approaches)

Analyze the failure and respond with ONLY JSON:

{
  "recoveryPossible": true/false,
  "reasoning": "Why did this fail and what should we try instead?",
  "alternativeTools": [
    {
      "toolName": "WebFetchTool", 
      "args": {"url": "alternative_url"},
      "reasoning": "Why this tool and these args",
      "priority": 1
    }
  ],
  "userQuestion": "Question to ask user if recovery not possible",
  "confidence": 0.8
}

FOCUS: If the content looks like JavaScript code instead of readable text, suggest trying the desktop version of the site or an alternative approach.
        """.trimIndent()
    }
    
    private fun parseRecoveryResponse(response: String): RecoveryStrategy {
        return try {
            // Clean JSON response like Unity agent does
            val cleaned = cleanJsonResponse(response)
            coquetteLogger.d("MobileErrorRecovery", "Attempting to parse JSON: ${cleaned.take(200)}...")
            
            val json = Json { 
                ignoreUnknownKeys = true
                isLenient = true // More forgiving parsing
            }
            json.decodeFromString<RecoveryStrategy>(cleaned)
        } catch (e: Exception) {
            coquetteLogger.w("MobileErrorRecovery", "JSON parsing failed: ${e.message}")
            coquetteLogger.w("MobileErrorRecovery", "Raw response: ${response.take(300)}...")
            
            // Smart fallback parsing for common patterns
            val recoveryPossible = response.contains("\"recoveryPossible\"\\s*:\\s*true".toRegex(RegexOption.IGNORE_CASE)) ||
                                 response.contains("recovery.*possible", ignoreCase = true)
            
            val reasoningMatch = "\"reasoning\"\\s*:\\s*\"([^\"]+)\"".toRegex(RegexOption.IGNORE_CASE).find(response)
            val reasoning = reasoningMatch?.groupValues?.get(1) ?: "Error parsing recovery analysis: ${e.message}"
            
            RecoveryStrategy(
                recoveryPossible = recoveryPossible,
                reasoning = reasoning,
                userQuestion = "I had trouble analyzing the failure. Could you try rephrasing your request?",
                confidence = 0.3f // Low confidence due to parsing failure
            )
        }
    }
    
    private fun cleanJsonResponse(response: String): String {
        var cleaned = response.trim()
        
        if (cleaned.startsWith("```json")) {
            cleaned = cleaned.removePrefix("```json").removeSuffix("```").trim()
        } else if (cleaned.startsWith("```")) {
            cleaned = cleaned.removePrefix("```").removeSuffix("```").trim()
        }
        
        val startIndex = cleaned.indexOf("{")
        val endIndex = cleaned.lastIndexOf("}")
        
        return if (startIndex != -1 && endIndex != -1 && endIndex > startIndex) {
            cleaned.substring(startIndex, endIndex + 1)
        } else {
            cleaned
        }
    }
    
    /**
     * Generate user-friendly explanation when recovery isn't possible
     */
    fun generateUserClarification(failure: RecoveryStrategy): String {
        return failure.userQuestion ?: when {
            failure.reasoning.contains("javascript", ignoreCase = true) -> 
                "The website you asked about uses heavy JavaScript that makes it hard to read. Could you try asking about a specific article or topic instead?"
                
            failure.reasoning.contains("network", ignoreCase = true) ->
                "I'm having trouble accessing that website. Could you check your internet connection or try a different site?"
                
            else -> 
                "I encountered an issue processing your request: ${failure.reasoning}. Could you try rephrasing or provide more details?"
        }
    }
}