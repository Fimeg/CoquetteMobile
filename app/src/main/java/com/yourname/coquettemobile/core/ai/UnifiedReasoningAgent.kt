package com.yourname.coquettemobile.core.ai

import com.yourname.coquettemobile.core.database.entities.Personality
import com.yourname.coquettemobile.core.logging.CoquetteLogger
import com.yourname.coquettemobile.core.models.ChatMessage
import com.yourname.coquettemobile.core.models.MessageType
import com.yourname.coquettemobile.core.preferences.AppPreferences
import com.yourname.coquettemobile.utils.ContextSizer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class UnifiedReasoningAgent @Inject constructor(
    private val ollamaService: OllamaService,
            private val coquetteLogger: CoquetteLogger,
                private val contextSizer: ContextSizer,
                    private val appPreferences: AppPreferences
) {

    data class UnifiedResponse(
        val reasoning: String? = null,
                               val directResponse: String? = null
    )

    suspend fun processRequest(
        query: String,
        conversationHistory: List<ChatMessage>,
        personality: Personality
    ): UnifiedResponse {
        coquetteLogger.i("UnifiedReasoningAgent", "Processing: $query")

        try {
            val systemPrompt = buildUnifiedSystemPrompt(personality, conversationHistory)
            val historyText = formatConversationContext(conversationHistory)

            val optimalContext = contextSizer.calculateUnityContext(
                personality = personality,
                systemPrompt = systemPrompt,
                userPrompt = query,
                conversationHistory = historyText
            )

            val combinedPrompt = "$systemPrompt\n\nUser: $query"
            val result = ollamaService.generateResponse(
                model = personality.unifiedModel ?: getDefaultUnifiedModel(),
                                                        prompt = combinedPrompt,
                                                        options = mapOf(
                                                            "temperature" to 0.7f,
                                                            "num_ctx" to optimalContext
                                                        )
            )

            val response = result.getOrElse {
                throw it
            }

            coquetteLogger.d("UnifiedReasoningAgent", "Raw model response: $response")
            return parseUnifiedResponse(response)

        } catch (e: Exception) {
            coquetteLogger.e("UnifiedReasoningAgent", "Error: ${e.message}")
            return UnifiedResponse(
                directResponse = "I apologize, but I encountered an error processing your request. Please try again."
            )
        }
    }

    private fun buildUnifiedSystemPrompt(
        personality: Personality,
        conversationHistory: List<ChatMessage>
    ): String {
        val enhancedSystemPrompt = personality.customSystemPrompt ?: personality.description
        
        return """
        $enhancedSystemPrompt

        CONVERSATION CONTEXT:
        ${formatConversationContext(conversationHistory)}

        Remember: Respond directly to the user.
        """.trimIndent()
    }

    

    private fun formatConversationContext(history: List<ChatMessage>): String {
        if (history.isEmpty()) return "No previous conversation."

        return history.takeLast(3).joinToString("\n") { message ->
                when (message.type) {
                    MessageType.USER -> "User: ${message.content}"
                    MessageType.AI -> "Assistant: ${message.content.take(100)}..."
                    else -> "System: ${message.content.take(100)}..."
                }
            }
    }

    private fun parseUnifiedResponse(response: String): UnifiedResponse {
        coquetteLogger.d("UnifiedReasoningAgent", "Parsing response: $response")

        val thinkRegex = """<think>(.*?)</think>""".toRegex(RegexOption.DOT_MATCHES_ALL)
        val thinkingContent = thinkRegex.find(response)?.groupValues?.get(1)?.trim()
        val directResponse = thinkRegex.replace(response, "").trim()

        return UnifiedResponse(
            reasoning = thinkingContent,
            directResponse = directResponse
        )
    }

    

    private fun getDefaultUnifiedModel(): String {
        return "hf.co/janhq/Jan-v1-4B-GGUF:Q8_0"
    }

}
