package com.yourname.coquettemobile.utils

import com.yourname.coquettemobile.core.preferences.AppPreferences
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ContextSizer @Inject constructor(private val appPreferences: AppPreferences) {
    
    /**
     * Rough token estimation - more accurate than character count
     * Based on: ~4 chars per token average for English text
     */
    fun estimateTokens(text: String): Int {
        // More sophisticated estimation:
        // - Whitespace and punctuation are often separate tokens
        // - Code/technical text has more tokens per character
        val words = text.split("\\s+".toRegex()).size
        val specialChars = text.count { it in "{}[]().,;:!?\"'`-_=+*&^%$#@~|\\/" }
        
        return (words * 1.3f + specialChars * 0.3f).toInt()
    }
    
    /**
     * Calculate optimal context size for a request
     * @param systemPrompt The system prompt
     * @param userPrompt The user input
     * @param conversationHistory Recent conversation context
     * @param warningThreshold Context size that triggers a warning (default 32768)
     * @param bufferRatio Buffer ratio for response generation (default 0.3 = 30%)
     */
    fun calculateOptimalContext(
        systemPrompt: String = "",
        userPrompt: String = "",
        conversationHistory: String = "",
        warningThreshold: Int = 32768,
        bufferRatio: Float = 0.3f
    ): Int {
        val systemTokens = estimateTokens(systemPrompt)
        val userTokens = estimateTokens(userPrompt)
        val historyTokens = estimateTokens(conversationHistory)
        
        val inputTokens = systemTokens + userTokens + historyTokens
        val bufferTokens = (warningThreshold * bufferRatio).toInt()
        val neededContext = inputTokens + bufferTokens
        
        // Round up to nearest power of 2 for efficiency - no hard limits!
        val contextSize = when {
            neededContext <= 1024 -> 1024
            neededContext <= 2048 -> 2048
            neededContext <= 4096 -> 4096
            neededContext <= 8192 -> 8192
            neededContext <= 16384 -> 16384
            neededContext <= 32768 -> 32768
            neededContext <= 65536 -> 65536
            neededContext <= 131072 -> 131072
            else -> neededContext // No arbitrary limit - let the model handle it
        }
        
        val logLevel = if (contextSize > warningThreshold) "W" else "D"
        android.util.Log.println(
            if (logLevel == "W") android.util.Log.WARN else android.util.Log.DEBUG,
            "ContextSizer", 
            """
            Context calculation${if (contextSize > warningThreshold) " ⚠️ LARGE CONTEXT" else ""}:
            - System: $systemTokens tokens
            - User: $userTokens tokens  
            - History: $historyTokens tokens
            - Input total: $inputTokens tokens
            - Buffer needed: $bufferTokens tokens
            - Optimal context: $contextSize tokens${if (contextSize > warningThreshold) " (exceeds warning threshold)" else ""}
            """.trimIndent()
        )
        
        return contextSize
    }
    
    /**
     * Get model-specific context limits
     */
    fun getModelContextLimit(model: String): Int {
        appPreferences.developerContextLimit?.let {
            android.util.Log.w("ContextSizer", "⚠️ Using developer override for context limit: $it")
            return it
        }
        return when {
            model.contains("jan", ignoreCase = true) -> 262144
            model.contains("deepseek-r1:32b", ignoreCase = true) -> 65536
            model.contains("deepseek-r1:8b", ignoreCase = true) -> 32768
            model.contains("qwen3:30b", ignoreCase = true) -> 32768
            model.contains("qwen3:8b", ignoreCase = true) -> 32768
            model.contains("gemma3n", ignoreCase = true) -> 8192
            model.contains("llama3", ignoreCase = true) -> 8192
            else -> 32768 // Safe default
        }
    }
    
    /**
     * Calculate context for Unity reasoning with model awareness
     */
    fun calculateUnityContext(
        personality: com.yourname.coquettemobile.core.database.entities.Personality,
        systemPrompt: String,
        userPrompt: String,
        conversationHistory: String
    ): Int {
        val model = personality.unifiedModel ?: "hf.co/janhq/Jan-v1-4B-GGUF:Q8_0"
        val maxContext = getModelContextLimit(model)
        
        // Use GPU-friendly warning threshold instead of model's theoretical max
        // This prevents massive buffer allocation that causes GPU OOM
        val practicalThreshold = 32768
        
        return calculateOptimalContext(
            systemPrompt = systemPrompt,
            userPrompt = userPrompt,
            conversationHistory = conversationHistory,
            warningThreshold = practicalThreshold,
            bufferRatio = 0.4f // Extra buffer for tool responses
        )
    }
}
