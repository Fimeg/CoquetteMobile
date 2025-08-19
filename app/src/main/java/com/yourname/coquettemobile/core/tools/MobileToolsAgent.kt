package com.yourname.coquettemobile.core.tools

import com.yourname.coquettemobile.core.ai.OllamaService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Mobile Tools Agent - Orchestrates tool execution with AI planning
 * Modeled after desktop Coquette's ToolsAgent architecture
 */
@Singleton
class MobileToolsAgent @Inject constructor(
    private val toolRegistry: MobileToolRegistry,
    private val ollamaService: OllamaService
) {
    
    /**
     * Execute tools based on natural language request
     * Uses gemma3n:e4b for planning (fast, lightweight)
     */
    suspend fun executeTools(request: String): ToolExecutionResult = withContext(Dispatchers.IO) {
        val startTime = System.currentTimeMillis()
        
        try {
            // Step 1: Analyze request and create execution plan
            val plan = createToolPlan(request)
            
            if (plan.steps.isEmpty()) {
                return@withContext ToolExecutionResult.noTools(request)
            }
            
            // Step 2: Execute plan steps
            val results = executePlan(plan)
            
            // Step 3: Create summary
            val executionTime = System.currentTimeMillis() - startTime
            val successCount = results.count { it.success }
            
            ToolExecutionResult(
                success = successCount > 0,
                request = request,
                plan = plan,
                stepResults = results,
                summary = createSummary(results, plan),
                executionTime = executionTime
            )
            
        } catch (e: Exception) {
            ToolExecutionResult.error(request, e.message ?: "Unknown error", System.currentTimeMillis() - startTime)
        }
    }
    
    /**
     * Create tool execution plan using AI
     */
    private suspend fun createToolPlan(request: String): ToolPlan {
        val availableTools = toolRegistry.getToolDescriptions()
        
        val planningPrompt = """
            You are a mobile AI assistant tool planner. Analyze the user request and create a JSON execution plan.
            
            Available Tools:
            $availableTools
            
            User Request: "$request"
            
            Create a JSON plan with this structure:
            {
                "reasoning": "Brief explanation of why these tools are needed",
                "steps": [
                    {
                        "id": "step1",
                        "tool": "tool_name",
                        "parameters": {"param": "value"},
                        "description": "What this step does"
                    }
                ],
                "risk_level": "LOW|MEDIUM|HIGH|CRITICAL",
                "estimated_time": 2000
            }
            
            Rules:
            - Only use tools from the available list
            - Use device_context tool for system information requests
            - Keep plans simple and focused
            - Estimate time in milliseconds
            - Set appropriate risk level
            
            Return only valid JSON, no other text.
        """.trimIndent()
        
        try {
            val response = ollamaService.generateResponse(
                model = "gemma3n:e4b",
                prompt = planningPrompt,
                options = mapOf(
                    "num_ctx" to 2048,  // Small context for planning
                    "temperature" to 0.1
                )
            )
            
            if (response.isFailure) {
                return createFallbackPlan(request)
            }
            
            val planJson = JSONObject(response.getOrThrow())
            return parseToolPlan(planJson)
            
        } catch (e: Exception) {
            return createFallbackPlan(request)
        }
    }
    
    /**
     * Parse JSON tool plan from AI response
     */
    private fun parseToolPlan(planJson: JSONObject): ToolPlan {
        val reasoning = planJson.optString("reasoning", "AI-generated tool plan")
        val estimatedTime = planJson.optLong("estimated_time", 5000)
        val riskLevel = try {
            RiskLevel.valueOf(planJson.optString("risk_level", "LOW"))
        } catch (e: Exception) {
            RiskLevel.LOW
        }
        
        val steps = mutableListOf<ToolStep>()
        val stepsArray = planJson.optJSONArray("steps") ?: JSONArray()
        
        for (i in 0 until stepsArray.length()) {
            val stepJson = stepsArray.getJSONObject(i)
            val step = ToolStep(
                id = stepJson.optString("id", "step_$i"),
                toolName = stepJson.optString("tool", ""),
                parameters = parseParameters(stepJson.optJSONObject("parameters")),
                description = stepJson.optString("description", "Tool execution step")
            )
            
            // Only add steps for tools that actually exist
            if (toolRegistry.hasTool(step.toolName)) {
                steps.add(step)
            }
        }
        
        return ToolPlan(
            steps = steps,
            reasoning = reasoning,
            estimatedTime = estimatedTime,
            riskAssessment = riskLevel
        )
    }
    
    /**
     * Execute the tool plan steps
     */
    private suspend fun executePlan(plan: ToolPlan): List<ToolResult> {
        val results = mutableListOf<ToolResult>()
        
        for (step in plan.steps) {
            try {
                val tool = toolRegistry.getTool(step.toolName)
                if (tool == null) {
                    results.add(ToolResult.error("Tool '${step.toolName}' not found"))
                    continue
                }
                
                val result = tool.execute(step.parameters)
                results.add(result)
                
            } catch (e: Exception) {
                results.add(ToolResult.error("Step '${step.id}' failed: ${e.message}"))
            }
        }
        
        return results
    }
    
    /**
     * Create fallback plan for simple requests
     */
    private fun createFallbackPlan(request: String): ToolPlan {
        val lowerRequest = request.lowercase()
        
        return when {
            lowerRequest.contains("battery") || lowerRequest.contains("device") || 
            lowerRequest.contains("system") || lowerRequest.contains("status") -> {
                ToolPlan(
                    steps = listOf(
                        ToolStep(
                            id = "device_info",
                            toolName = "device_context",
                            parameters = mapOf("action" to "all"),
                            description = "Get complete device information"
                        )
                    ),
                    reasoning = "Device information request detected",
                    estimatedTime = 2000,
                    riskAssessment = RiskLevel.LOW
                )
            }
            
            else -> ToolPlan(
                steps = emptyList(),
                reasoning = "No specific tools identified for this request",
                estimatedTime = 0,
                riskAssessment = RiskLevel.LOW
            )
        }
    }
    
    /**
     * Parse JSON parameters to Map
     */
    private fun parseParameters(parametersJson: JSONObject?): Map<String, Any> {
        if (parametersJson == null) return emptyMap()
        
        val parameters = mutableMapOf<String, Any>()
        val keys = parametersJson.keys()
        
        while (keys.hasNext()) {
            val key = keys.next()
            val value = parametersJson.opt(key)
            if (value != null) {
                parameters[key] = value
            }
        }
        
        return parameters
    }
    
    /**
     * Create execution summary
     */
    private fun createSummary(results: List<ToolResult>, plan: ToolPlan): String {
        val successCount = results.count { it.success }
        val totalCount = results.size
        
        if (successCount == 0) {
            return "Tool execution failed. No steps completed successfully."
        }
        
        val summary = StringBuilder()
        summary.append("Executed $successCount/$totalCount tool operations successfully.\n\n")
        
        // Add key results
        results.forEachIndexed { index, result ->
            if (result.success && result.output.isNotBlank()) {
                val stepDescription = if (index < plan.steps.size) {
                    plan.steps[index].description
                } else {
                    "Step ${index + 1}"
                }
                
                // Truncate long outputs for summary
                val preview = if (result.output.length > 300) {
                    result.output.take(300) + "..."
                } else {
                    result.output
                }
                
                summary.append("**$stepDescription**\n$preview\n\n")
            }
        }
        
        return summary.toString().trim()
    }
}

/**
 * Result of tool execution
 */
data class ToolExecutionResult(
    val success: Boolean,
    val request: String,
    val plan: ToolPlan?,
    val stepResults: List<ToolResult>,
    val summary: String,
    val executionTime: Long
) {
    companion object {
        fun noTools(request: String) = ToolExecutionResult(
            success = false,
            request = request,
            plan = null,
            stepResults = emptyList(),
            summary = "No tools were identified for this request.",
            executionTime = 0
        )
        
        fun error(request: String, error: String, executionTime: Long) = ToolExecutionResult(
            success = false,
            request = request,
            plan = null,
            stepResults = listOf(ToolResult.error(error)),
            summary = "Tool execution failed: $error",
            executionTime = executionTime
        )
    }
}