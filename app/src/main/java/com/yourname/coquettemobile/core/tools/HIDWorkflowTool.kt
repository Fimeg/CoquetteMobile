package com.yourname.coquettemobile.core.tools

import android.content.Context
import com.yourname.coquettemobile.core.logging.CoquetteLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import javax.inject.Inject
import org.json.JSONArray
import org.json.JSONObject

/**
 * HIDWorkflowTool - AI-powered multi-step device automation
 * Orchestrates complex sequences of keyboard and mouse operations
 * Enables voice-to-automation and cross-device workflows
 */
class HIDWorkflowTool @Inject constructor(
    private val context: Context,
    private val logger: CoquetteLogger,
    private val hidKeyboardTool: HIDKeyboardTool,
    private val hidMouseTool: HIDMouseTool
) : MobileTool {
    
    override val name = "HIDWorkflowTool"
    override val description = "Execute complex multi-step automation workflows combining keyboard and mouse operations"
    override val riskLevel = RiskLevel.CRITICAL // High-level automation control
    override val requiredPermissions = listOf(
        "android.permission.ACCESS_SUPERUSER" // Custom permission indicator
    )
    
    companion object {
        private const val MAX_STEPS = 100 // Prevent infinite workflows
        private const val MAX_LOOP_ITERATIONS = 50 // Prevent infinite loops
        private const val DEFAULT_STEP_DELAY = 100L // milliseconds between steps
    }
    
    data class WorkflowStep(
        val id: String,
        val type: String, // "keyboard", "mouse", "delay", "loop", "condition"
        val action: String,
        val parameters: Map<String, Any>,
        val description: String = "",
        val continueOnError: Boolean = false
    )
    
    data class WorkflowResult(
        val totalSteps: Int,
        val completedSteps: Int,
        val skippedSteps: Int,
        val failedSteps: Int,
        val executionTime: Long,
        val stepResults: List<StepResult>
    )
    
    data class StepResult(
        val stepId: String,
        val success: Boolean,
        val result: String,
        val executionTime: Long,
        val error: String? = null
    )
    
    override suspend fun execute(params: Map<String, Any>): ToolResult = withContext(Dispatchers.IO) {
        try {
            val steps = parseWorkflowSteps(params)
            if (steps.isEmpty()) {
                return@withContext ToolResult.error("No workflow steps provided")
            }
            
            if (steps.size > MAX_STEPS) {
                return@withContext ToolResult.error("Too many steps (max: $MAX_STEPS)")
            }
            
            val stepDelay = ((params["stepDelay"] as? Number)?.toLong() ?: DEFAULT_STEP_DELAY)
                .coerceIn(0L, 5000L)
            
            val result = executeWorkflow(steps, stepDelay)
            
            val metadata = mapOf(
                "totalSteps" to result.totalSteps,
                "completedSteps" to result.completedSteps,
                "failedSteps" to result.failedSteps,
                "skippedSteps" to result.skippedSteps,
                "executionTime" to result.executionTime,
                "successRate" to (result.completedSteps.toFloat() / result.totalSteps * 100).toInt()
            )
            
            val summary = """
Workflow execution completed:
- Total steps: ${result.totalSteps}
- Completed: ${result.completedSteps} 
- Failed: ${result.failedSteps}
- Skipped: ${result.skippedSteps}
- Success rate: ${metadata["successRate"]}%
- Execution time: ${result.executionTime}ms
            """.trimIndent()
            
            ToolResult.success(summary, metadata)
            
        } catch (e: Exception) {
            logger.e("HIDWorkflowTool", "Error executing workflow: ${e.message}")
            ToolResult.error("Failed to execute workflow: ${e.message}")
        }
    }
    
    override suspend fun executeStreaming(
        params: Map<String, Any>,
        onProgress: (String) -> Unit
    ): ToolResult = withContext(Dispatchers.IO) {
        try {
            onProgress("Parsing workflow steps...")
            
            val steps = parseWorkflowSteps(params)
            if (steps.isEmpty()) {
                onProgress("No workflow steps found")
                return@withContext ToolResult.error("No workflow steps provided")
            }
            
            onProgress("Loaded ${steps.size} workflow steps")
            
            if (steps.size > MAX_STEPS) {
                onProgress("Too many steps provided")
                return@withContext ToolResult.error("Too many steps (max: $MAX_STEPS)")
            }
            
            val stepDelay = ((params["stepDelay"] as? Number)?.toLong() ?: DEFAULT_STEP_DELAY)
                .coerceIn(0L, 5000L)
            
            onProgress("Starting workflow execution...")
            
            val result = executeWorkflowWithProgress(steps, stepDelay, onProgress)
            
            val metadata = mapOf(
                "totalSteps" to result.totalSteps,
                "completedSteps" to result.completedSteps,
                "failedSteps" to result.failedSteps,
                "skippedSteps" to result.skippedSteps,
                "executionTime" to result.executionTime,
                "successRate" to (result.completedSteps.toFloat() / result.totalSteps * 100).toInt()
            )
            
            onProgress("Workflow completed: ${result.completedSteps}/${result.totalSteps} steps successful")
            
            val summary = """
Workflow execution completed:
- Total steps: ${result.totalSteps}
- Completed: ${result.completedSteps} 
- Failed: ${result.failedSteps}
- Skipped: ${result.skippedSteps}
- Success rate: ${metadata["successRate"]}%
- Execution time: ${result.executionTime}ms
            """.trimIndent()
            
            ToolResult.success(summary, metadata)
            
        } catch (e: Exception) {
            val error = "Failed to execute workflow: ${e.message}"
            onProgress(error)
            ToolResult.error(error)
        }
    }
    
    /**
     * Parse workflow steps from various input formats
     */
    private fun parseWorkflowSteps(params: Map<String, Any>): List<WorkflowStep> {
        val steps = mutableListOf<WorkflowStep>()
        
        // Handle different input formats
        when {
            params.containsKey("steps") -> {
                // Array of step objects
                val stepsArray = params["steps"] as? List<*>
                stepsArray?.forEachIndexed { index, stepData ->
                    val stepMap = stepData as? Map<*, *>
                    if (stepMap != null) {
                        steps.add(parseStepFromMap(stepMap, index))
                    }
                }
            }
            params.containsKey("script") -> {
                // Simple script format: "type:Hello World|key:ctrl+c|wait:1000|click:500,300"
                val script = params["script"] as? String
                if (script != null) {
                    steps.addAll(parseScriptFormat(script))
                }
            }
            params.containsKey("commands") -> {
                // Command array format
                val commands = params["commands"] as? List<*>
                commands?.forEachIndexed { index, command ->
                    steps.add(parseCommandFormat(command.toString(), index))
                }
            }
        }
        
        return steps
    }
    
    /**
     * Parse step from map format
     */
    private fun parseStepFromMap(stepMap: Map<*, *>, index: Int): WorkflowStep {
        val id = stepMap["id"]?.toString() ?: "step_$index"
        val type = stepMap["type"]?.toString() ?: "keyboard"
        val action = stepMap["action"]?.toString() ?: ""
        val description = stepMap["description"]?.toString() ?: ""
        val continueOnError = stepMap["continueOnError"] as? Boolean ?: false
        
        // Extract parameters
        val parameters = mutableMapOf<String, Any>()
        stepMap.forEach { (key, value) ->
            if (key !in listOf("id", "type", "action", "description", "continueOnError")) {
                parameters[key.toString()] = value ?: ""
            }
        }
        
        return WorkflowStep(
            id = id,
            type = type,
            action = action,
            parameters = parameters,
            description = description,
            continueOnError = continueOnError
        )
    }
    
    /**
     * Parse simple script format: "type:Hello|key:ctrl+c|wait:1000"
     */
    private fun parseScriptFormat(script: String): List<WorkflowStep> {
        val steps = mutableListOf<WorkflowStep>()
        val commands = script.split("|")
        
        commands.forEachIndexed { index, command ->
            val parts = command.split(":", limit = 2)
            if (parts.size == 2) {
                val action = parts[0].trim()
                val params = parts[1].trim()
                
                steps.add(parseCommandFormat("$action:$params", index))
            }
        }
        
        return steps
    }
    
    /**
     * Parse individual command format
     */
    private fun parseCommandFormat(command: String, index: Int): WorkflowStep {
        val parts = command.split(":", limit = 2)
        if (parts.size < 2) {
            return WorkflowStep("step_$index", "delay", "wait", mapOf("duration" to 100))
        }
        
        val action = parts[0].trim().lowercase()
        val params = parts[1].trim()
        
        return when (action) {
            "type", "text" -> WorkflowStep(
                id = "step_$index",
                type = "keyboard",
                action = "type",
                parameters = mapOf("text" to params),
                description = "Type: ${params.take(50)}"
            )
            "key", "shortcut" -> {
                val keys = params.split("+").map { it.trim() }
                WorkflowStep(
                    id = "step_$index",
                    type = "keyboard",
                    action = "key",
                    parameters = mapOf("keys" to keys),
                    description = "Key: $params"
                )
            }
            "click" -> {
                val coords = params.split(",").map { it.trim().toIntOrNull() ?: 0 }
                val parameters = mutableMapOf<String, Any>("action" to "click")
                if (coords.size >= 2) {
                    parameters["x"] = coords[0]
                    parameters["y"] = coords[1]
                }
                WorkflowStep(
                    id = "step_$index",
                    type = "mouse",
                    action = "click",
                    parameters = parameters,
                    description = "Click: ${coords.joinToString(",")}"
                )
            }
            "move" -> {
                val coords = params.split(",").map { it.trim().toIntOrNull() ?: 0 }
                val parameters = mutableMapOf<String, Any>("action" to "move_to")
                if (coords.size >= 2) {
                    parameters["x"] = coords[0]
                    parameters["y"] = coords[1]
                }
                WorkflowStep(
                    id = "step_$index",
                    type = "mouse",
                    action = "move_to",
                    parameters = parameters,
                    description = "Move to: ${coords.joinToString(",")}"
                )
            }
            "wait", "delay", "sleep" -> WorkflowStep(
                id = "step_$index",
                type = "delay",
                action = "wait",
                parameters = mapOf("duration" to (params.toLongOrNull() ?: 1000L)),
                description = "Wait: ${params}ms"
            )
            "scroll" -> {
                val scrollParams = params.split(",")
                val direction = scrollParams.getOrNull(0)?.trim() ?: "up"
                val amount = scrollParams.getOrNull(1)?.trim()?.toIntOrNull() ?: 3
                WorkflowStep(
                    id = "step_$index",
                    type = "mouse",
                    action = "scroll",
                    parameters = mapOf(
                        "action" to "scroll",
                        "direction" to direction,
                        "amount" to amount
                    ),
                    description = "Scroll: $direction $amount"
                )
            }
            else -> WorkflowStep(
                id = "step_$index",
                type = "delay",
                action = "wait",
                parameters = mapOf("duration" to 100L),
                description = "Unknown command: $action"
            )
        }
    }
    
    /**
     * Execute workflow steps
     */
    private suspend fun executeWorkflow(steps: List<WorkflowStep>, stepDelay: Long): WorkflowResult {
        val startTime = System.currentTimeMillis()
        val stepResults = mutableListOf<StepResult>()
        var completedSteps = 0
        var failedSteps = 0
        var skippedSteps = 0
        
        for (step in steps) {
            val stepStartTime = System.currentTimeMillis()
            
            try {
                val success = executeStep(step)
                val stepTime = System.currentTimeMillis() - stepStartTime
                
                if (success) {
                    completedSteps++
                    stepResults.add(StepResult(step.id, true, "Success", stepTime))
                } else {
                    if (step.continueOnError) {
                        skippedSteps++
                        stepResults.add(StepResult(step.id, false, "Failed but continued", stepTime))
                    } else {
                        failedSteps++
                        stepResults.add(StepResult(step.id, false, "Failed", stepTime))
                        break // Stop execution on failure
                    }
                }
                
                if (stepDelay > 0) {
                    delay(stepDelay)
                }
                
            } catch (e: Exception) {
                val stepTime = System.currentTimeMillis() - stepStartTime
                failedSteps++
                stepResults.add(StepResult(step.id, false, "Error", stepTime, e.message))
                
                if (!step.continueOnError) {
                    break
                }
            }
        }
        
        val totalTime = System.currentTimeMillis() - startTime
        
        return WorkflowResult(
            totalSteps = steps.size,
            completedSteps = completedSteps,
            skippedSteps = skippedSteps,
            failedSteps = failedSteps,
            executionTime = totalTime,
            stepResults = stepResults
        )
    }
    
    /**
     * Execute workflow with progress updates
     */
    private suspend fun executeWorkflowWithProgress(
        steps: List<WorkflowStep>, 
        stepDelay: Long,
        onProgress: (String) -> Unit
    ): WorkflowResult {
        val startTime = System.currentTimeMillis()
        val stepResults = mutableListOf<StepResult>()
        var completedSteps = 0
        var failedSteps = 0
        var skippedSteps = 0
        
        for ((index, step) in steps.withIndex()) {
            val stepNum = index + 1
            onProgress("[$stepNum/${steps.size}] ${step.description.takeIf { it.isNotEmpty() } ?: step.action}")
            
            val stepStartTime = System.currentTimeMillis()
            
            try {
                val success = executeStep(step)
                val stepTime = System.currentTimeMillis() - stepStartTime
                
                if (success) {
                    completedSteps++
                    stepResults.add(StepResult(step.id, true, "Success", stepTime))
                    onProgress("[$stepNum/${steps.size}] ✓ Completed in ${stepTime}ms")
                } else {
                    if (step.continueOnError) {
                        skippedSteps++
                        stepResults.add(StepResult(step.id, false, "Failed but continued", stepTime))
                        onProgress("[$stepNum/${steps.size}] ⚠ Failed but continuing...")
                    } else {
                        failedSteps++
                        stepResults.add(StepResult(step.id, false, "Failed", stepTime))
                        onProgress("[$stepNum/${steps.size}] ✗ Failed - stopping workflow")
                        break
                    }
                }
                
                if (stepDelay > 0) {
                    delay(stepDelay)
                }
                
            } catch (e: Exception) {
                val stepTime = System.currentTimeMillis() - stepStartTime
                failedSteps++
                stepResults.add(StepResult(step.id, false, "Error", stepTime, e.message))
                onProgress("[$stepNum/${steps.size}] ✗ Error: ${e.message}")
                
                if (!step.continueOnError) {
                    break
                }
            }
        }
        
        val totalTime = System.currentTimeMillis() - startTime
        
        return WorkflowResult(
            totalSteps = steps.size,
            completedSteps = completedSteps,
            skippedSteps = skippedSteps,
            failedSteps = failedSteps,
            executionTime = totalTime,
            stepResults = stepResults
        )
    }
    
    /**
     * Execute individual workflow step
     */
    private suspend fun executeStep(step: WorkflowStep): Boolean {
        return try {
            when (step.type.lowercase()) {
                "keyboard" -> {
                    val result = hidKeyboardTool.execute(step.parameters + ("action" to step.action))
                    result.success
                }
                "mouse" -> {
                    val result = hidMouseTool.execute(step.parameters)
                    result.success
                }
                "delay", "wait", "sleep" -> {
                    val duration = (step.parameters["duration"] as? Number)?.toLong() ?: 1000L
                    delay(duration)
                    true
                }
                else -> {
                    logger.w("HIDWorkflowTool", "Unknown step type: ${step.type}")
                    false
                }
            }
        } catch (e: Exception) {
            logger.e("HIDWorkflowTool", "Error executing step ${step.id}: ${e.message}")
            false
        }
    }
    
    override fun getDescription(params: Map<String, Any>): String {
        val steps = parseWorkflowSteps(params)
        return "Executing workflow with ${steps.size} steps"
    }
    
    override fun validateParams(params: Map<String, Any>): String? {
        return when {
            !params.containsKey("steps") && !params.containsKey("script") && !params.containsKey("commands") -> 
                "Must provide 'steps', 'script', or 'commands' parameter"
            else -> {
                try {
                    val steps = parseWorkflowSteps(params)
                    when {
                        steps.isEmpty() -> "No valid workflow steps found"
                        steps.size > MAX_STEPS -> "Too many steps (max: $MAX_STEPS)"
                        else -> null
                    }
                } catch (e: Exception) {
                    "Error parsing workflow steps: ${e.message}"
                }
            }
        }
    }
    
    override fun getParameterSchema(): String {
        return """
            Parameters:
            - steps (option 1, array): Array of detailed step objects
            - script (option 2, string): Simple script format with pipe separators
            - commands (option 3, array): Array of simple command strings
            - stepDelay (optional, number): Delay between steps in ms (0-5000, default: 100)
            
            Step Object Format:
            {
              "id": "unique_step_id",
              "type": "keyboard|mouse|delay",
              "action": "type|key|click|move|scroll|wait",
              "parameters": {...},
              "description": "Human readable description",
              "continueOnError": false
            }
            
            Script Format Examples:
            "type:Hello World|key:ctrl+c|wait:1000|click:500,300|scroll:down,3"
            
            Command Format Examples:
            ["type:Hello", "key:ctrl+a", "key:ctrl+c", "wait:500", "click:100,200"]
            
            Available Actions:
            Keyboard:
              - type:text → Type text
              - key:ctrl+c → Key combinations
              
            Mouse:
              - click:x,y → Left click at coordinates
              - move:x,y → Move cursor to position
              - scroll:direction,amount → Scroll (up/down)
              
            Other:
              - wait:ms → Delay in milliseconds
              
            Complex Workflow Example:
            {
              "steps": [
                {
                  "id": "open_app",
                  "type": "keyboard", 
                  "action": "key",
                  "keys": ["ctrl", "alt", "t"],
                  "description": "Open terminal"
                },
                {
                  "id": "wait_load",
                  "type": "delay",
                  "action": "wait", 
                  "duration": 2000,
                  "description": "Wait for terminal to load"
                },
                {
                  "id": "run_command",
                  "type": "keyboard",
                  "action": "type",
                  "text": "ls -la && echo 'Done'",
                  "description": "Execute command"
                },
                {
                  "id": "press_enter", 
                  "type": "keyboard",
                  "action": "key",
                  "keys": ["enter"],
                  "description": "Execute command"
                }
              ],
              "stepDelay": 200
            }
            
            Simple Script Example:
            {
              "script": "key:ctrl+alt+t|wait:2000|type:ls -la|key:enter|wait:1000",
              "stepDelay": 100
            }
            
            Requirements:
            - Rooted Android device with HID gadgets configured
            - Target device connected via USB
        """.trimIndent()
    }
}