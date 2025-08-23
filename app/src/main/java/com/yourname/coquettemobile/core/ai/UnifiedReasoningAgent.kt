package com.yourname.coquettemobile.core.ai

import com.google.gson.Gson
import com.google.gson.JsonSyntaxException
import com.yourname.coquettemobile.core.tools.MobileToolRegistry
import com.yourname.coquettemobile.core.tools.MobileTool
import com.yourname.coquettemobile.data.local.entities.ChatMessage
import com.yourname.coquettemobile.data.local.entities.Personality
import com.yourname.coquettemobile.services.OllamaService
import com.yourname.coquettemobile.utils.CoquetteLogger
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class UnifiedReasoningAgent @Inject constructor(
    private val ollamaService: OllamaService,
    private val mobileToolRegistry: MobileToolRegistry
) {
    
    data class UnifiedResponse(
        val reasoning: String? = null,
        val toolsToUse: List<ToolSelection> = emptyList(),
        val directResponse: String? = null,
        val hasTools: Boolean = false
    )
    
    data class ToolSelection(
        val toolName: String,
        val args: Map<String, Any>,
        val reasoning: String? = null
    )
    
    suspend fun processRequest(
        query: String,
        conversationHistory: List<ChatMessage>,
        personality: Personality
    ): UnifiedResponse {
        CoquetteLogger.info("UnifiedReasoningAgent processing: $query")
        
        try {
            val systemPrompt = buildUnifiedSystemPrompt(personality, conversationHistory)
            
            val response = ollamaService.generateCompletion(
                model = personality.unifiedModel ?: getDefaultUnifiedModel(),
                systemPrompt = systemPrompt,
                userPrompt = query,
                temperature = 0.7f
            )
            
            return parseUnifiedResponse(response)
            
        } catch (e: Exception) {
            CoquetteLogger.error("UnifiedReasoningAgent error: ${e.message}")
            return UnifiedResponse(
                directResponse = "I apologize, but I encountered an error processing your request. Please try again.",
                hasTools = false
            )
        }
    }
    
    private fun buildUnifiedSystemPrompt(
        personality: Personality, 
        conversationHistory: List<ChatMessage>
    ): String {
        val availableTools = mobileToolRegistry.getAllTools()
        val toolDescriptions = buildToolDescriptions(availableTools)
        
        return """
${personality.systemPrompt}

You are an intelligent mobile assistant. You can either respond directly or use tools to help the user.

AVAILABLE TOOLS:
$toolDescriptions

RESPONSE FORMAT:
You must respond in one of two ways:

1. DIRECT RESPONSE (no tools needed):
```json
{
  "type": "direct",
  "response": "your direct response here"
}
```

2. TOOL USE (tools needed):
```json
{
  "type": "tools",
  "reasoning": "why you need these tools",
  "tools": [
    {
      "name": "ToolName",
      "args": {"key": "value"},
      "reasoning": "why this specific tool"
    }
  ]
}
```

IMPORTANT:
- Always respond with valid JSON
- Choose tools carefully based on the user's actual need
- Don't default to DeviceContextTool unless specifically asking about device info
- Use your personality while being helpful
- Consider conversation context

CONVERSATION CONTEXT:
${formatConversationContext(conversationHistory)}

Respond to the user's request thoughtfully.
        """.trimIndent()
    }
    
    private fun buildToolDescriptions(tools: List<MobileTool>): String {
        return tools.joinToString("\n") { tool ->
            "- ${tool::class.simpleName}: ${tool.description}"
        }
    }
    
    private fun formatConversationContext(history: List<ChatMessage>): String {
        if (history.isEmpty()) return "No previous conversation."
        
        return history.takeLast(3).joinToString("\n") { message ->
            when (message.isUser) {
                true -> "User: ${message.content}"
                false -> "Assistant: ${message.content.take(100)}..."
            }
        }
    }
    
    private fun parseUnifiedResponse(response: String): UnifiedResponse {
        CoquetteLogger.debug("Parsing unified response: $response")
        
        // Clean up response to find JSON
        val cleanedResponse = cleanJsonResponse(response)
        
        return try {
            val gson = Gson()
            val jsonResponse = gson.fromJson(cleanedResponse, Map::class.java) as Map<String, Any>
            
            when (jsonResponse["type"]) {
                "direct" -> {
                    UnifiedResponse(
                        directResponse = jsonResponse["response"] as? String ?: "No response provided",
                        hasTools = false
                    )
                }
                "tools" -> {
                    val toolsList = jsonResponse["tools"] as? List<Map<String, Any>> ?: emptyList()
                    val toolSelections = toolsList.map { toolMap ->
                        ToolSelection(
                            toolName = toolMap["name"] as? String ?: "",
                            args = toolMap["args"] as? Map<String, Any> ?: emptyMap(),
                            reasoning = toolMap["reasoning"] as? String
                        )
                    }
                    
                    UnifiedResponse(
                        reasoning = jsonResponse["reasoning"] as? String,
                        toolsToUse = toolSelections,
                        hasTools = true
                    )
                }
                else -> {
                    CoquetteLogger.warning("Unknown response type: ${jsonResponse["type"]}")
                    fallbackToDirectResponse(response)
                }
            }
        } catch (e: JsonSyntaxException) {
            CoquetteLogger.warning("JSON parsing failed, using fallback: ${e.message}")
            fallbackToDirectResponse(response)
        }
    }
    
    private fun cleanJsonResponse(response: String): String {
        // Remove markdown code blocks if present
        var cleaned = response.trim()
        
        if (cleaned.startsWith("```json")) {
            cleaned = cleaned.removePrefix("```json").removeSuffix("```").trim()
        } else if (cleaned.startsWith("```")) {
            cleaned = cleaned.removePrefix("```").removeSuffix("```").trim()
        }
        
        // Find JSON object boundaries
        val startIndex = cleaned.indexOf("{")
        val endIndex = cleaned.lastIndexOf("}")
        
        return if (startIndex != -1 && endIndex != -1 && endIndex > startIndex) {
            cleaned.substring(startIndex, endIndex + 1)
        } else {
            cleaned
        }
    }
    
    private fun fallbackToDirectResponse(response: String): UnifiedResponse {
        return UnifiedResponse(
            directResponse = response.take(500) + if (response.length > 500) "..." else "",
            hasTools = false
        )
    }
    
    private fun getDefaultUnifiedModel(): String {
        // Prefer models in order of capability for unified reasoning
        return "hf.co/janhq/Jan-v1-4B-GGUF:Q8_0" // Jan model is designed for this
    }
}