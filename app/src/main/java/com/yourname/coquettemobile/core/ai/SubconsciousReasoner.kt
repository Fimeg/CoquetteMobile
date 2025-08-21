package com.yourname.coquettemobile.core.ai

import com.yourname.coquettemobile.core.models.ReasoningContext
import com.yourname.coquettemobile.core.models.ReasoningResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class TaskPlan(
    val steps: List<TaskStep>,
    val estimatedComplexity: String,
    val reasoning: String,
    val modelUsed: String
)

data class TaskStep(
    val stepNumber: Int,
    val description: String,
    val toolsRequired: List<String>,
    val expectedOutput: String,
    val dependencies: List<Int> = emptyList()
)

class SubconsciousReasoner(private val ollamaService: OllamaService) {
    
    suspend fun analyzeAndRefine(
        query: String,
        initialResponse: String,
        modelToUse: String
    ): String = withContext(Dispatchers.Default) {
        try {
            val refinementPrompt = createRefinementPrompt(query, initialResponse)
            val result = ollamaService.generateResponse(modelToUse, refinementPrompt)
            
            if (result.isSuccess) {
                result.getOrThrow()
            } else {
                initialResponse
            }
        } catch (e: Exception) {
            initialResponse
        }
    }
    
    private fun createRefinementPrompt(query: String, initialResponse: String): String {
        return """
        The user asked: "$query"
        
        Initial response generated: "$initialResponse"
        
        Please analyze and refine this response to make it more helpful, accurate, and engaging.
        Consider:
        1. Completeness - does it fully answer the question?
        2. Clarity - is it easy to understand?
        3. Relevance - does it stay on topic?
        4. Helpfulness - does it provide actionable insights?
        
        Provide an improved version of the response.
        """.trimIndent()
    }
    
    suspend fun performDeepAnalysis(
        userRequest: String,
        context: ReasoningContext
    ): ReasoningResult = withContext(Dispatchers.Default) {
        try {
            // Create a comprehensive analysis prompt
            val analysisPrompt = createAnalysisPrompt(userRequest, context)
            
            // Use DeepSeek for deep analysis if available, otherwise fallback
            val modelToUse = if (context.availableModels.any { it.contains("deepseek", ignoreCase = true) }) {
                context.availableModels.first { it.contains("deepseek", ignoreCase = true) }
            } else {
                context.availableModels.firstOrNull() ?: "gemma"
            }
            
            val result = ollamaService.generateResponse(modelToUse, analysisPrompt)
            
            if (result.isSuccess) {
                parseReasoningResponse(result.getOrThrow(), modelToUse)
            } else {
                createFallbackReasoning(context, userRequest)
            }
        } catch (e: Exception) {
            createFallbackReasoning(context, userRequest)
        }
    }
    
    suspend fun planComplexTask(
        userRequest: String,
        context: ReasoningContext,
        availableTools: List<String>
    ): TaskPlan = withContext(Dispatchers.Default) {
        try {
            val planningPrompt = createTaskPlanningPrompt(userRequest, context, availableTools)
            
            // Use the most capable model for planning
            val modelToUse = selectBestPlanningModel(context.availableModels)
            
            val result = ollamaService.generateResponse(modelToUse, planningPrompt)
            
            if (result.isSuccess) {
                parseTaskPlan(result.getOrThrow(), modelToUse)
            } else {
                createFallbackTaskPlan(userRequest, availableTools)
            }
        } catch (e: Exception) {
            createFallbackTaskPlan(userRequest, availableTools)
        }
    }
    
    private fun createAnalysisPrompt(userRequest: String, context: ReasoningContext): String {
        return """
        Perform a deep analysis of the following user request. Consider the conversation context, 
        available models, and current personality settings.
        
        USER REQUEST: "$userRequest"
        
        CONVERSATION CONTEXT (last 5 messages):
        ${context.conversationHistory.takeLast(5).joinToString("\n") { "${it.type}: ${it.content}" }}
        
        AVAILABLE MODELS: ${context.availableModels.joinToString(", ")}
        CURRENT PERSONALITY: ${context.currentPersonality}
        
        Please provide:
        1. A comprehensive analysis of what the user is asking for
        2. Recommended approach for handling this request
        3. Any potential complexities or edge cases
        4. Suggestions for which model would be most appropriate and why
        5. Any additional context that might be helpful
        
        Format your response as a structured analysis with clear sections.
        """.trimIndent()
    }
    
    private fun parseReasoningResponse(response: String, modelUsed: String): ReasoningResult {
        // Simple parsing - in a real implementation, this would be more sophisticated
        val analysis = extractSection(response, "analysis") ?: "Analysis completed successfully"
        val recommendations = extractRecommendations(response)
        
        return ReasoningResult(
            analysis = analysis,
            recommendations = recommendations,
            confidence = 0.8f, // Default confidence
            modelUsed = modelUsed
        )
    }
    
    private fun extractSection(response: String, sectionName: String): String? {
        val lines = response.lines()
        val sectionStart = lines.indexOfFirst { it.contains(sectionName, ignoreCase = true) }
        if (sectionStart == -1) return null
        
        val sectionEnd = lines.subList(sectionStart + 1, lines.size)
            .indexOfFirst { it.trim().isEmpty() || it.matches("^\\d+\\..*".toRegex()) }
        
        return if (sectionEnd == -1) {
            lines.subList(sectionStart + 1, lines.size).joinToString("\n")
        } else {
            lines.subList(sectionStart + 1, sectionStart + 1 + sectionEnd).joinToString("\n")
        }
    }
    
    private fun extractRecommendations(response: String): List<String> {
        return response.lines()
            .filter { it.trim().matches("^\\d+\\..*".toRegex()) || it.trim().startsWith("- ") }
            .map { it.trim().removePrefix("- ").replaceFirst("^\\d+\\.\\s*".toRegex(), "") }
            .filter { it.isNotBlank() }
            .take(5) // Limit to top 5 recommendations
    }
    
    private fun createFallbackReasoning(context: ReasoningContext, userRequest: String): ReasoningResult {
        return ReasoningResult(
            analysis = "Basic analysis: User requested '$userRequest'. Using available models: ${context.availableModels.joinToString(", ")}",
            recommendations = listOf(
                "Proceed with standard response generation",
                "Consider conversation context for better relevance",
                "Apply personality filtering for appropriate tone"
            ),
            confidence = 0.5f,
            modelUsed = context.availableModels.firstOrNull() ?: "unknown"
        )
    }
    
    private fun createTaskPlanningPrompt(
        userRequest: String,
        context: ReasoningContext,
        availableTools: List<String>
    ): String {
        return """
        You are an expert task planner. Break down the user's request into a detailed, step-by-step plan.
        
        USER REQUEST: "$userRequest"
        
        AVAILABLE TOOLS: ${availableTools.joinToString(", ")}
        
        CONVERSATION CONTEXT (recent messages):
        ${context.conversationHistory.takeLast(3).joinToString("\n") { "${it.type}: ${it.content}" }}
        
        Create a detailed execution plan that:
        1. Breaks the task into logical steps
        2. Identifies which tools are needed for each step
        3. Specifies expected outputs
        4. Notes dependencies between steps
        5. Estimates task complexity (Low/Medium/High)
        
        Format your response as JSON:
        {
            "complexity": "Low|Medium|High",
            "reasoning": "Brief explanation of the approach",
            "steps": [
                {
                    "step": 1,
                    "description": "What to do",
                    "tools": ["ToolName"],
                    "expected_output": "What this step produces",
                    "dependencies": []
                }
            ]
        }
        
        For complex tasks requiring multiple web searches, data analysis, or content synthesis, create comprehensive multi-step plans.
        """.trimIndent()
    }
    
    private fun selectBestPlanningModel(availableModels: List<String>): String {
        // Prefer larger, more capable models for complex planning
        return when {
            availableModels.any { it.contains("deepseek-r1:32b", ignoreCase = true) } ->
                availableModels.first { it.contains("deepseek-r1:32b", ignoreCase = true) }
            availableModels.any { it.contains("qwen3:30b", ignoreCase = true) } ->
                availableModels.first { it.contains("qwen3:30b", ignoreCase = true) }
            availableModels.any { it.contains("deepseek", ignoreCase = true) } ->
                availableModels.first { it.contains("deepseek", ignoreCase = true) }
            else -> availableModels.firstOrNull() ?: "gemma"
        }
    }
    
    private fun parseTaskPlan(response: String, modelUsed: String): TaskPlan {
        return try {
            // Parse JSON response
            val jsonRegex = """\{.*\}""".toRegex(RegexOption.DOT_MATCHES_ALL)
            val jsonMatch = jsonRegex.find(response)
            
            if (jsonMatch != null) {
                val jsonText = jsonMatch.value
                val json = org.json.JSONObject(jsonText)
                
                val complexity = json.optString("complexity", "Medium")
                val reasoning = json.optString("reasoning", "Task breakdown completed")
                val stepsArray = json.optJSONArray("steps")
                
                val steps = mutableListOf<TaskStep>()
                if (stepsArray != null) {
                    for (i in 0 until stepsArray.length()) {
                        val stepObj = stepsArray.getJSONObject(i)
                        val toolsArray = stepObj.optJSONArray("tools")
                        val tools = if (toolsArray != null) {
                            (0 until toolsArray.length()).map { toolsArray.getString(it) }
                        } else emptyList()
                        
                        val depsArray = stepObj.optJSONArray("dependencies")
                        val dependencies = if (depsArray != null) {
                            (0 until depsArray.length()).map { depsArray.getInt(it) }
                        } else emptyList()
                        
                        steps.add(TaskStep(
                            stepNumber = stepObj.optInt("step", i + 1),
                            description = stepObj.optString("description", "Execute step ${i + 1}"),
                            toolsRequired = tools,
                            expectedOutput = stepObj.optString("expected_output", "Step completion"),
                            dependencies = dependencies
                        ))
                    }
                }
                
                TaskPlan(
                    steps = steps,
                    estimatedComplexity = complexity,
                    reasoning = reasoning,
                    modelUsed = modelUsed
                )
            } else {
                createFallbackTaskPlan(response, emptyList())
            }
        } catch (e: Exception) {
            createFallbackTaskPlan("Parse error: ${e.message}", emptyList())
        }
    }
    
    private fun createFallbackTaskPlan(userRequest: String, availableTools: List<String>): TaskPlan {
        return TaskPlan(
            steps = listOf(
                TaskStep(
                    stepNumber = 1,
                    description = "Handle user request: $userRequest",
                    toolsRequired = availableTools.take(1),
                    expectedOutput = "Basic response to user request"
                )
            ),
            estimatedComplexity = "Low",
            reasoning = "Fallback plan due to parsing or model error",
            modelUsed = "fallback"
        )
    }
}
