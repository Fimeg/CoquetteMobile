package com.yourname.coquettemobile.core.ai

import com.yourname.coquettemobile.core.database.entities.Personality
import com.yourname.coquettemobile.core.logging.CoquetteLogger
import com.yourname.coquettemobile.core.models.ChatMessage
import com.yourname.coquettemobile.core.models.MessageType
import com.yourname.coquettemobile.core.tools.MobileTool
import com.yourname.coquettemobile.core.tools.MobileToolRegistry
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
        private val mobileToolRegistry: MobileToolRegistry,
            private val coquetteLogger: CoquetteLogger,
                private val contextSizer: ContextSizer
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
        val toolDescriptions = buildToolDescriptions(availableTools, personality)

        return """
        ${personality.systemPrompt}

        You are an intelligent mobile assistant. Your primary goal is to respond directly to the user in a helpful, conversational manner.

        If and only if you determine that you need to use a tool to fulfill the user's request, you MUST respond with a valid JSON object in the following format. Do not include any other text outside of the JSON block.

        ```json
        {
        "type": "tools",
        "reasoning": "A brief explanation of why you need to use tools.",
        "tools": [
        {
        "name": "ToolName",
        "args": {"key": "value"},
        "reasoning": "A brief explanation of why you are using this specific tool."
    }
    ]
    }

    If you do not need to use a tool, respond directly to the user as a normal conversational AI. Do NOT use JSON for direct responses.

    AVAILABLE TOOLS:
    $toolDescriptions

    TOOL CHAINING EXAMPLES:

    Check website + extract readable content: [WebFetchTool, ExtractorTool]

    Check website + summarize: [WebFetchTool, ExtractorTool, SummarizerTool]

    For ExtractorTool after WebFetchTool: use empty string "" for html arg - it gets populated automatically

    For SummarizerTool after ExtractorTool: text arg gets populated automatically from extracted content

    TOOL SELECTION RULES:

    Websites/URLs/checking sites → WebFetchTool (then usually ExtractorTool)

    Device info/battery/storage → DeviceContextTool

    Long content summarization → SummarizerTool

    Notifications → NotificationTool

    Always chain WebFetch → Extractor for readable content

    Consider SummarizerTool for very long extracted content

    CONVERSATION CONTEXT:
    ${formatConversationContext(conversationHistory)}

    Remember: Respond with JSON ONLY for tool use. Otherwise, respond directly.
    """.trimIndent()
    }

    private fun buildToolDescriptions(tools: List<MobileTool>, personality: Personality): String {
        return tools.joinToString("\n") { tool ->
            val toolDescription = personality.toolPrompts[tool.name] ?: tool.description
            "- ${tool.name}: $toolDescription"
        }
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
        val jsonString = cleanJsonResponse(response)

        try {
            val json = Json { ignoreUnknownKeys = true; isLenient = true }
            val jsonElement = json.parseToJsonElement(jsonString)
            val jsonResponse = jsonElement.jsonObject

            if (jsonResponse["type"]?.jsonPrimitive?.content == "tools") {
                val toolsArray = jsonResponse["tools"]?.jsonArray
                val toolSelections = toolsArray?.map { toolElement ->
                    val toolObj = toolElement.jsonObject
                    val argsObj = toolObj["args"]?.jsonObject
                    val argsMap = argsObj?.mapValues { (_, value) ->
                        value.jsonPrimitive.content
                    } ?: emptyMap()

                    ToolSelection(
                        toolName = toolObj["name"]?.jsonPrimitive?.content ?: "",
                        args = argsMap,
                        reasoning = toolObj["reasoning"]?.jsonPrimitive?.content
                    )
                } ?: emptyList()

                return UnifiedResponse(
                    reasoning = jsonResponse["reasoning"]?.jsonPrimitive?.content,
                    toolsToUse = toolSelections,
                    hasTools = toolSelections.isNotEmpty()
                )
            }
        } catch (e: Exception) {
            coquetteLogger.d("UnifiedReasoningAgent", "Response is not a tool request, treating as direct response.")
        }

        val thinkRegex = """<think>(.*?)</think>""".toRegex(RegexOption.DOT_MATCHES_ALL)
        val thinkingContent = thinkRegex.find(response)?.groupValues?.get(1)?.trim()
        val directResponse = thinkRegex.replace(response, "").trim()

        return UnifiedResponse(
            reasoning = thinkingContent,
            directResponse = directResponse,
            hasTools = false
        )
    }

    private fun cleanJsonResponse(response: String): String {
        val textWithoutMarkdown = if (response.startsWith("```json")) {
            response.removePrefix("```json").removeSuffix("```").trim()
        } else {
            response
        }
        val startIndex = textWithoutMarkdown.indexOf('{')
        val endIndex = textWithoutMarkdown.lastIndexOf('}')

        return if (startIndex != -1 && endIndex != -1 && endIndex > startIndex) {
            textWithoutMarkdown.substring(startIndex, endIndex + 1)
        } else {
            coquetteLogger.w("UnifiedReasoningAgent", "Could not find JSON in response: $response")
            "{}"
        }
    }

    private fun getDefaultUnifiedModel(): String {
        return "hf.co/janhq/Jan-v1-4B-GGUF:Q8_0"
    }

}
