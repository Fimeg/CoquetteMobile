package com.yourname.coquettemobile.core.orchestration

import com.yourname.coquettemobile.core.ai.OllamaService
import com.yourname.coquettemobile.core.ai.UnifiedReasoningAgent
import com.yourname.coquettemobile.core.database.entities.Personality
import com.yourname.coquettemobile.core.logging.CoquetteLogger
import com.yourname.coquettemobile.core.models.ChatMessage
import com.yourname.coquettemobile.core.preferences.AppPreferences
import com.yourname.coquettemobile.core.repository.PersonalityRepository
import com.yourname.coquettemobile.core.tools.RiskLevel
import com.yourname.coquettemobile.utils.ThinkingParser
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.ProducerScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Bridges conversational AI personalities with the specialist router system.
 * This orchestrator translates user requests into actionable, multi-step operations
 * that are executed by the OrchestratorAgent, and streams the results back to the UI.
 */
@Singleton
class PersonalityOrchestrator @Inject constructor(
    private val orchestratorAgent: OrchestratorAgent, // The refactored, streaming-capable agent
    private val unifiedReasoningAgent: UnifiedReasoningAgent,
    private val personalityRepository: PersonalityRepository,
    private val ollamaService: OllamaService,
    private val appPreferences: AppPreferences,
    private val logger: CoquetteLogger
) {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true; coerceInputValues = true }

    /**
     * The primary, unified entry point for processing all user requests.
     * It determines the request complexity and routes to the appropriate handler,
     * providing a real-time stream of updates.
     */
    fun processRequestStreaming(
        userMessage: String,
        personalityId: String,
        conversationHistory: List<ChatMessage>
    ): Flow<OrchestrationUpdate> = channelFlow {
        logger.i("PersonalityOrchestrator", "Processing request for personality: $personalityId")

        val personality = personalityRepository.getPersonalityById(personalityId)
        if (personality == null) {
            send(OrchestrationUpdate.Error("Personality not found: $personalityId"))
            return@channelFlow
        }

        // Phase 1: Intent Analysis
        val intentAnalysis = analyzeRequestIntentStreaming(userMessage, personality, conversationHistory) { thinkingSteps ->
            launch { send(OrchestrationUpdate.Thinking(thinkingSteps)) }
        }
        send(OrchestrationUpdate.IntentAnalysisComplete(intentAnalysis))

        // Route based on complexity
        if (intentAnalysis.complexity == OperationComplexity.SIMPLE) {
            // Handle simple, conversational requests directly.
            launch {
                val simpleResponse = handleSimpleConversation(userMessage, personality, conversationHistory)
                send(OrchestrationUpdate.Complete(simpleResponse))
            }
        } else {
            // Handle complex, tool-based requests using simple CEO delegation
            val context = OperationContext(originalIntent = userMessage)

            // CEO Decision: Simple delegation to appropriate department
            logger.i("PersonalityOrchestrator", "Delegating complex request to CEO")
            
            launch {
                // Phase 2: CEO selects department
                val selectedRouter = orchestratorAgent.selectDepartment(userMessage, context)
                if (selectedRouter == null) {
                    send(OrchestrationUpdate.Error("No suitable department found"))
                    return@launch
                }

                // Phase 3: Department creates plan
                val subSteps = selectedRouter.planSubSteps(userMessage, context)
                val totalDuration = subSteps.sumOf { selectedRouter.estimateExecutionTime(it) }
                val executionPlan = ExecutionPlan(
                    planId = "dept_plan_${System.currentTimeMillis()}",
                    originalIntent = userMessage,
                    steps = subSteps,
                    estimatedDurationMs = totalDuration,
                    riskLevel = com.yourname.coquettemobile.core.tools.RiskLevel.MEDIUM
                )
                send(OrchestrationUpdate.PlanGenerated(executionPlan))

                // Phase 4: Department executes each step
                val stepResults = mutableListOf<StepResult>()
                for (step in subSteps) {
                    val result = selectedRouter.executeStep(step, context)
                    stepResults.add(result)
                    send(OrchestrationUpdate.StepExecution(result))
                }

                // Phase 5: Synthesis - create final response from all step results
                val finalSummary = synthesizeFinalResponseStreaming(stepResults, context) { thinkingSteps ->
                    launch { send(OrchestrationUpdate.Thinking(thinkingSteps)) }
                }
                
                // Phase 6: Let personality process the consolidated data
                val unityResponse = unifiedReasoningAgent.processRequest(
                    query = "Tool results: $finalSummary\n\nOriginal request: $userMessage",
                    conversationHistory = conversationHistory,
                    personality = personality
                )
                val finalResponse = PersonalityResponse.Simple(
                    personalityName = personality.name,
                    message = unityResponse.directResponse ?: finalSummary,
                    thinkingSteps = unityResponse.reasoning?.let { listOf(it) } ?: emptyList(),
                    toolExecutions = emptyList()
                )
                send(OrchestrationUpdate.Complete(finalResponse))
            }
        }
    }

    private suspend fun handleSimpleConversation(
        userMessage: String,
        personality: Personality,
        conversationHistory: List<ChatMessage>
    ): PersonalityResponse.Simple {
        logger.d("PersonalityOrchestrator", "Handling simple conversation for ${personality.name}")
        val unityResponse = unifiedReasoningAgent.processRequest(
            query = userMessage,
            conversationHistory = conversationHistory,
            personality = personality
        )
        return PersonalityResponse.simple(
            message = unityResponse.directResponse ?: "I'm not sure how to respond to that.",
            personalityName = personality.name,
            thinkingSteps = unityResponse.reasoning?.let { listOf(it) } ?: emptyList()
        )
    }

    private suspend fun analyzeRequestIntentStreaming(
        userMessage: String,
        personality: Personality,
        conversationHistory: List<ChatMessage>,
        onThinking: (String) -> Unit
    ): IntentAnalysis {
        val analysisPrompt = buildIntentAnalysisPrompt(userMessage, personality, conversationHistory)
        val modelToUse = personality.unifiedModel ?: appPreferences.orchestratorModel ?: "hf.co/janhq/Jan-v1-4B-GGUF:Q8_0"

        val responseBuilder = StringBuilder()
        var lastParsedThoughts = emptyList<String>()
        ollamaService.sendMessageStream(
            message = analysisPrompt,
            model = modelToUse,
            systemPrompt = "You are an intent analysis specialist. Respond only with valid JSON."
        ).collect { chunk ->
            responseBuilder.append(chunk)
            val contentSoFar = responseBuilder.toString()
            val parsed = ThinkingParser.parseThinkingTags(contentSoFar)
            if (parsed.thinkingSteps.size > lastParsedThoughts.size) {
                val newSteps = parsed.thinkingSteps.subList(lastParsedThoughts.size, parsed.thinkingSteps.size)
                onThinking(newSteps.joinToString("\n"))
                lastParsedThoughts = parsed.thinkingSteps
            }
        }

        val finalContent = responseBuilder.toString()
        logger.d("PersonalityOrchestrator", "🎯 Raw intent analysis response: $finalContent")
        
        // Extract clean JSON content (without <think> tags) for parsing
        val parsed = ThinkingParser.parseThinkingTags(finalContent)
        val cleanContent = parsed.content.ifBlank { finalContent }
        
        // Enhanced logging for debugging
        logger.d("PersonalityOrchestrator", "🎯 Original response length: ${finalContent.length}")
        logger.d("PersonalityOrchestrator", "🎯 Thinking steps extracted: ${parsed.thinkingSteps.size}")
        logger.d("PersonalityOrchestrator", "🎯 Clean content length: ${cleanContent.length}")
        logger.d("PersonalityOrchestrator", "🎯 Clean content for JSON parsing: $cleanContent")
        
        // Check if clean content still contains thinking tags
        if (cleanContent.contains("<think>") || cleanContent.contains("</think>")) {
            logger.w("PersonalityOrchestrator", "⚠️ Clean content still contains thinking tags - parser may have failed")
        }
        
        val result = parseIntentAnalysis(cleanContent)
        logger.d("PersonalityOrchestrator", "🎯 Parsed intent result: complexity=${result.complexity}, specialists=${result.requiredSpecialists}")
        return result
    }

    private fun parseIntentAnalysis(content: String): IntentAnalysis {
        return try {
            val jsonString = extractFirstCompleteJson(content)
            if (jsonString.isNullOrEmpty()) {
                throw IllegalArgumentException("No complete JSON object found in content.")
            }
            
            logger.d("PersonalityOrchestrator", "🎯 Extracted JSON for parsing: $jsonString")
            json.decodeFromString<IntentAnalysis>(jsonString)
        } catch (e: Exception) {
            logger.e("PersonalityOrchestrator", "Failed to parse intent analysis JSON: ${e.message}")
            logger.e("PersonalityOrchestrator", "Raw content that failed to parse: '$content'")
            IntentAnalysis(
                complexity = OperationComplexity.COMPLEX,
                reasoning = "Parse failure fallback: ${e.message}",
                riskLevel = RiskLevel.MEDIUM,
                requiredSpecialists = listOf("AndroidIntel")
            )
        }
    }
    
    /**
     * Extract the first complete JSON object from content by tracking brace matching.
     * This prevents accidentally grabbing JSON fragments from within thinking tags.
     */
    private fun extractFirstCompleteJson(content: String): String? {
        val startIndex = content.indexOf('{')
        if (startIndex == -1) return null
        
        var braceCount = 0
        var inString = false
        var escapeNext = false
        
        for (i in startIndex until content.length) {
            val char = content[i]
            
            when {
                escapeNext -> {
                    escapeNext = false
                }
                char == '\\' && inString -> {
                    escapeNext = true
                }
                char == '"' -> {
                    inString = !inString
                }
                !inString -> {
                    when (char) {
                        '{' -> braceCount++
                        '}' -> {
                            braceCount--
                            if (braceCount == 0) {
                                // Found the end of the first complete JSON object
                                return content.substring(startIndex, i + 1)
                            }
                        }
                    }
                }
            }
        }
        
        return null // No complete JSON object found
    }

    private suspend fun createExecutionPlanStreaming(
        userIntent: String,
        context: OperationContext,
        onThinking: (String) -> Unit
    ): ExecutionPlan {
        logger.i("PersonalityOrchestrator", "Phase 2: Creating execution plan with thinking capture")
        val planningPrompt = buildPlanningPrompt(userIntent, context)
        val modelToUse = appPreferences.orchestratorModel ?: "deepseek-r1:1.5b"

        val responseBuilder = StringBuilder()
        var lastParsedThoughts = emptyList<String>()
        ollamaService.sendMessageStream(
            message = planningPrompt,
            model = modelToUse,
            systemPrompt = "You are the OrchestratorAgent CEO. Create execution plans."
        ).collect { chunk ->
            responseBuilder.append(chunk)
            val contentSoFar = responseBuilder.toString()
            val parsed = ThinkingParser.parseThinkingTags(contentSoFar)
            if (parsed.thinkingSteps.size > lastParsedThoughts.size) {
                val newSteps = parsed.thinkingSteps.subList(lastParsedThoughts.size, parsed.thinkingSteps.size)
                onThinking(newSteps.joinToString("\n"))
                lastParsedThoughts = parsed.thinkingSteps
            }
        }

        val finalContent = responseBuilder.toString()
        logger.d("PersonalityOrchestrator", "🎯 Raw planning response: $finalContent")
        // PlanParser has been eliminated - this shouldn't be reached
        throw IllegalStateException("Planning methods deprecated - use simple delegation instead")
    }

    private suspend fun synthesizeFinalResponseStreaming(
        results: List<StepResult>,
        context: OperationContext,
        onThinking: (String) -> Unit
    ): String {
        logger.i("PersonalityOrchestrator", "Phase 4: Synthesizing final response with thinking capture")
        val synthesisPrompt = buildSynthesisPrompt(results, context)
        val modelToUse = appPreferences.orchestratorModel ?: "deepseek-r1:1.5b"

        val responseBuilder = StringBuilder()
        var lastParsedThoughts = emptyList<String>()
        ollamaService.sendMessageStream(
            message = synthesisPrompt,
            model = modelToUse,
            systemPrompt = "You are synthesizing operation results."
        ).collect { chunk ->
            responseBuilder.append(chunk)
            val contentSoFar = responseBuilder.toString()
            val parsed = ThinkingParser.parseThinkingTags(contentSoFar)
            if (parsed.thinkingSteps.size > lastParsedThoughts.size) {
                val newSteps = parsed.thinkingSteps.subList(lastParsedThoughts.size, parsed.thinkingSteps.size)
                onThinking(newSteps.joinToString("\n"))
                lastParsedThoughts = parsed.thinkingSteps
            }
        }

        val finalContent = responseBuilder.toString()
        logger.d("PersonalityOrchestrator", "🎯 Raw synthesis response: $finalContent")
        val parsed = ThinkingParser.parseThinkingTags(finalContent)
        return parsed.content.ifBlank { finalContent }
    }

    private fun buildPlanningPrompt(userIntent: String, context: OperationContext): String {
        return """
        **User's Request:** "$userIntent"

        Analyze this request and create the most direct and efficient execution plan to accomplish the goal.
        """.trimIndent()
    }

    private fun buildSynthesisPrompt(results: List<StepResult>, context: OperationContext): String {
        val successfulResults = results.filter { it.success }
        val failedResults = results.filter { !it.success }

        return """
        Operation: ${context.originalIntent}

        Tool Results:
        ${successfulResults.joinToString("\n") { "✅ ${it.stepId}: ${it.data}" }}
        ${if (failedResults.isNotEmpty()) "\nFailed:\n${failedResults.joinToString("\n") { "❌ ${it.stepId}: ${it.error}" }}" else ""}

        Consolidate this raw data into a clean summary.
        Do not create conversational responses - just amalgamate the information.
        The personality will handle final response generation.
        """.trimIndent()
    }

    private fun buildIntentAnalysisPrompt(
        userMessage: String,
        personality: Personality,
        conversationHistory: List<ChatMessage>
    ): String {
        val history = conversationHistory.takeLast(4).joinToString("\n") { "${it.type}: ${it.content}" }
        return """
        You are ${personality.name} running on CoquetteMobile 1.0 Android app, analyzing a user request.
        
        User Request: "$userMessage"
        Recent Conversation: $history

        SYSTEM CAPABILITIES (available in COMPLEX mode only):
        - Desktop control: keyboard/mouse injection, type text on computers, execute scripts
        - Web operations: fetch content, scrape sites, gather online data
        - File operations: read/write files, search, analyze local content  
        - Device operations: Android system access, notifications, hardware control
        
        Can you handle this through conversation alone, or does it require system capabilities?
        
        SIMPLE: Pure conversation using existing knowledge
        COMPLEX: Requires desktop control, web access, file operations, or device actions

        Examples:
        - "explain quantum physics" → SIMPLE
        - "write text on my desktop/computer" → COMPLEX (desktop control)
        - "what's on reddit today" → COMPLEX (web operations)
        - "analyze my photos" → COMPLEX (file operations)

        Respond with ONLY JSON:
        {
          "complexity": "SIMPLE|COMPLEX",
          "reasoning": "Your reasoning here",
          "risk_level": "LOW|MEDIUM|HIGH|CRITICAL"
        }
        """.trimIndent()
    }
}

// --- DATA MODELS FOR ORCHESTRATION ---

/**
 * Represents the real-time state of an ongoing operation, streamed to the UI.
 */
sealed class OrchestrationUpdate {
    /** Emitted whenever an AI model is thinking, providing a stream of consciousness. */
    data class Thinking(val thoughts: String) : OrchestrationUpdate()

    /** Emitted after the initial intent analysis is complete. */
    data class IntentAnalysisComplete(val analysis: IntentAnalysis) : OrchestrationUpdate()

    /** Emitted once the strategic plan for a complex operation has been generated. */
    data class PlanGenerated(val plan: ExecutionPlan) : OrchestrationUpdate()

    /** Emitted in real-time as each step in the execution plan is completed. */
    data class StepExecution(val stepResult: StepResult) : OrchestrationUpdate()

    /** Emitted when the entire operation is finished, containing the final response. */
    data class Complete(val response: PersonalityResponse.Simple) : OrchestrationUpdate()

    /** Emitted if any unrecoverable error occurs during orchestration. */
    data class Error(val message: String) : OrchestrationUpdate()
}

@kotlinx.serialization.Serializable
data class IntentAnalysis(
    val complexity: OperationComplexity = OperationComplexity.SIMPLE,
    val reasoning: String = "",
    @kotlinx.serialization.SerialName("risk_level") val riskLevel: RiskLevel = RiskLevel.LOW,
    @kotlinx.serialization.SerialName("required_specialists") val requiredSpecialists: List<String> = emptyList()
)

@kotlinx.serialization.Serializable
enum class OperationComplexity {
    SIMPLE, COMPLEX
}

sealed class PersonalityResponse {
    abstract val personalityName: String

    data class Simple(
        override val personalityName: String,
        val message: String,
        val thinkingSteps: List<String> = emptyList(),
        val toolExecutions: List<com.yourname.coquettemobile.core.models.ToolExecution> = emptyList()
    ) : PersonalityResponse() 

    companion object {
        fun simple(message: String, personalityName: String, thinkingSteps: List<String> = emptyList()) =
            Simple(personalityName, message, thinkingSteps)
    }
}
