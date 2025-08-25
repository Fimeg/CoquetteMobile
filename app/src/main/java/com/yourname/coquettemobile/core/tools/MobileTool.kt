package com.yourname.coquettemobile.core.tools

/**
 * Base interface for all mobile tools
 * Modeled after desktop Coquette's tool architecture
 */
interface MobileTool {
    val name: String
    val description: String
    val requiredPermissions: List<String>
    val riskLevel: RiskLevel
    
    suspend fun execute(parameters: Map<String, Any>): ToolResult
    
    /**
     * Execute tool with streaming support for real-time progress updates
     * @param parameters Tool parameters
     * @param onProgress Callback for streaming progress updates
     */
    suspend fun executeStreaming(
        parameters: Map<String, Any>,
        onProgress: (String) -> Unit
    ): ToolResult
    
    /**
     * Get human-readable description of what this tool execution will do
     */
    fun getDescription(params: Map<String, Any>): String
    
    /**
     * Validate parameters before execution
     * @return null if valid, error message if invalid
     */
    fun validateParams(params: Map<String, Any>): String?
    
    /**
     * Get parameter schema for dynamic prompt generation
     * Inspired by desktop Coquette's function declaration patterns
     */
    fun getParameterSchema(): String
}

/**
 * Risk assessment levels for tool operations
 */
enum class RiskLevel {
    LOW,        // Read-only operations, device info
    MEDIUM,     // File operations, camera, basic sensors
    HIGH,       // Location, contacts, phone, SMS
    CRITICAL    // System modifications, root operations
}

/**
 * Result of tool execution
 */
sealed class ToolResult {
    abstract val success: Boolean
    abstract val output: String
    abstract val metadata: Map<String, Any>
    
    data class Success(
        override val output: String,
        override val metadata: Map<String, Any> = emptyMap()
    ) : ToolResult() {
        override val success = true
    }
    
    data class Error(
        val error: String,
        override val metadata: Map<String, Any> = emptyMap()
    ) : ToolResult() {
        override val success = false
        override val output = "Error: $error"
    }
    
    companion object {
        fun success(output: String, metadata: Map<String, Any> = emptyMap()) = 
            Success(output, metadata)
            
        fun error(error: String, metadata: Map<String, Any> = emptyMap()) = 
            Error(error, metadata)
    }
}

/**
 * Tool execution request
 */
data class ToolRequest(
    val toolName: String,
    val parameters: Map<String, Any> = emptyMap(),
    val context: ToolExecutionContext? = null
)

/**
 * Context for tool execution
 */
data class ToolExecutionContext(
    val conversationId: String?,
    val userId: String?,
    val timestamp: Long = System.currentTimeMillis(),
    val deviceState: Map<String, Any> = emptyMap()
)

/**
 * Tool execution plan step
 */
data class ToolStep(
    val id: String,
    val toolName: String,
    val parameters: Map<String, Any>,
    val description: String,
    val dependsOn: List<String> = emptyList()
)

/**
 * Multi-step tool execution plan
 */
data class ToolPlan(
    val steps: List<ToolStep>,
    val reasoning: String,
    val estimatedTime: Long,
    val riskAssessment: RiskLevel
)
