package com.yourname.coquettemobile.core.tools

import com.yourname.coquettemobile.core.preferences.AppPreferences
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Device context data class for tool scoring
 */
data class DeviceContext(
    val batteryLevel: Int,
    val networkState: NetworkState,
    val grantedPermissions: List<String>
)

/**
 * Network connectivity states
 */
enum class NetworkState {
    WIFI, MOBILE, DISCONNECTED, UNKNOWN
}

/**
 * Tool with relevance score for ranking
 */
data class ScoredTool(
    val tool: MobileTool,
    val score: Float,
    val reasoning: String,
    val constraints: List<String>
)

/**
 * Registry for all mobile tools
 * Modeled after desktop Coquette's ToolRegistry pattern
 */
@Singleton
class MobileToolRegistry @Inject constructor(
    private val deviceContextTool: DeviceContextTool,
    private val webFetchTool: WebFetchTool,
    private val extractorTool: ExtractorTool,
    private val summarizerTool: SummarizerTool,
    private val notificationTool: NotificationTool,
    private val webImageTool: WebImageTool,
    private val fileTool: FileTool,
    private val writeFileTool: WriteFileTool,
    private val listTool: ListTool,
    private val globTool: GlobTool,
    private val grepTool: GrepTool,
    private val hidKeyboardTool: HIDKeyboardTool,
    private val hidMouseTool: HIDMouseTool,
    private val hidWorkflowTool: HIDWorkflowTool,
    private val appPreferences: AppPreferences
) {
    private val tools = mutableMapOf<String, MobileTool>()
    
    init {
        registerTools()
    }
    
    private fun registerTools() {
        // Register core tools
        registerTool(deviceContextTool)
        registerTool(webFetchTool)
        registerTool(extractorTool)
        registerTool(summarizerTool)
        registerTool(notificationTool)
        registerTool(webImageTool)
        
        // File system tools
        registerTool(fileTool)
        registerTool(writeFileTool)
        registerTool(listTool)
        registerTool(globTool)
        registerTool(grepTool)
        
        // HID Device Control Tools (requires root)
        registerTool(hidKeyboardTool)
        registerTool(hidMouseTool)
        registerTool(hidWorkflowTool)
        
        // Future tools will be registered here:
        // registerTool(editTool)
        // registerTool(cameraTool)
        // registerTool(locationTool)
        // registerTool(shellTool)
    }
    
    /**
     * Register a tool in the registry
     */
    fun registerTool(tool: MobileTool) {
        tools[tool.name] = tool
    }
    
    /**
     * Get a specific tool by name
     */
    fun getTool(name: String): MobileTool? {
        return tools[name]
    }
    
    /**
     * Get all available tools
     */
    fun getAllTools(): List<MobileTool> {
        return tools.values.toList()
    }
    
    /**
     * Get tools by risk level
     */
    fun getToolsByRiskLevel(riskLevel: RiskLevel): List<MobileTool> {
        return tools.values.filter { it.riskLevel == riskLevel }
    }
    
    /**
     * Get tools that require specific permissions
     */
    fun getToolsRequiringPermissions(permissions: List<String>): List<MobileTool> {
        return tools.values.filter { tool ->
            tool.requiredPermissions.any { permission -> 
                permissions.contains(permission) 
            }
        }
    }
    
    /**
     * Get tool descriptions for LLM context
     */
    fun getToolDescriptions(): String {
        return tools.values.joinToString("\n") { tool ->
            "• ${tool.name}: ${tool.description} [${tool.riskLevel.name} risk]"
        }
    }
    
    /**
     * Generate dynamic tool prompt for planner (DESKTOP COQUETTE PATTERN)
     * Replaces hardcoded tool lists with real-time discovery
     */
    fun generatePlannerToolPrompt(): String {
        val toolDescriptions = tools.values.joinToString("\n") { tool ->
            val params = tool.getParameterSchema()
            "- ${tool.name}(${params})"
        }
        
        return """
You have access to these tools:
$toolDescriptions

Rules:
- For complex requests, create multi-step tool chains
- Chain tools strategically: fetch → extract → summarize, or multiple fetches for comparison
- For research tasks, plan multiple sources and synthesis
- For analysis tasks, break down into discrete data gathering steps
- If no tools needed, return {"decision":"respond","reason":"..."}
        """.trimIndent()
    }
    
    /**
     * Generate function calling tool prompt for unified models
     */
    fun generateFunctionCallingPrompt(): String {
        val functionDeclarations = tools.values.joinToString("\n") { tool ->
            val params = tool.getParameterSchema()
            """
Function: ${tool.name}
Description: ${tool.description}
Parameters: ${params}
Risk Level: ${tool.riskLevel.name}
            """.trimIndent()
        }
        
        val customPrompt = appPreferences.functionCallingPrompt
        
        return if (customPrompt != null) {
            // Replace placeholder with actual function declarations
            customPrompt.replace("{function_declarations}", functionDeclarations)
        } else {
            """
Available Tools:
$functionDeclarations

Use function calling to invoke tools when needed. Consider mobile context, permissions, and battery constraints.
            """.trimIndent()
        }
    }
    
    /**
     * Check if a tool exists
     */
    fun hasTool(name: String): Boolean {
        return tools.containsKey(name)
    }
    
    /**
     * Get tools available based on granted permissions
     */
    fun getAvailableTools(grantedPermissions: List<String>): List<MobileTool> {
        return tools.values.filter { tool ->
            tool.requiredPermissions.isEmpty() || 
            tool.requiredPermissions.all { permission ->
                grantedPermissions.contains(permission)
            }
        }
    }
    
    /**
     * Discover tool for request using advanced pattern matching
     * Inspired by desktop Coquette's sophisticated tool discovery
     */
    fun discoverToolForRequest(request: String, deviceContext: DeviceContext? = null): MobileTool? {
        // Use a scoring system instead of first-match
        val scores = tools.values.map { tool ->
            tool to calculateRelevanceScore(tool, request, deviceContext)
        }
        
        return scores
            .filter { it.second > 0.3f } // Minimum threshold
            .maxByOrNull { it.second }
            ?.first
    }
    
    /**
     * Get tools scored and ranked for specific request
     * Advanced pattern from desktop Coquette adapted for mobile
     */
    fun getToolsRankedForRequest(
        request: String, 
        deviceContext: DeviceContext? = null,
        limit: Int = 5
    ): List<ScoredTool> {
        return tools.values.mapNotNull { tool ->
            val score = calculateRelevanceScore(tool, request, deviceContext)
            if (score > 0.1f) {
                ScoredTool(
                    tool = tool,
                    score = score,
                    reasoning = generateReasoningForScore(tool, request, score),
                    constraints = calculateConstraints(tool, deviceContext)
                )
            } else null
        }.sortedByDescending { it.score }
         .take(limit)
    }
    
    /**
     * Calculate sophisticated relevance score for tool selection
     * Combines multiple factors: keywords, context, constraints
     */
    private fun calculateRelevanceScore(
        tool: MobileTool, 
        request: String, 
        deviceContext: DeviceContext?
    ): Float {
        val requestLower = request.lowercase()
        var score = 0f
        
        // 1. Direct keyword matching (base score)
        val keywordScore = if (tool is NotificationTool) {
            val keywords = tool.getKeywords()
            val requestWords = requestLower.split(" ")
            val matches = keywords.count { keyword ->
                requestWords.any { it.contains(keyword.lowercase()) }
            }
            val exactMatches = keywords.count { keyword ->
                requestLower.contains(keyword.lowercase())
            }
            ((matches + exactMatches * 2).toFloat() / (keywords.size + 2)).coerceIn(0.0f, 1.0f)
        } else {
            // Generic keyword scoring for other tools
            calculateGenericKeywordScore(tool, requestLower)
        }
        
        score += keywordScore * 0.6f // 60% weight for keyword relevance
        
        // 2. Tool capability matching
        val capabilityScore = calculateCapabilityScore(tool, request)
        score += capabilityScore * 0.25f // 25% weight for capability match
        
        // 3. Device context compatibility
        deviceContext?.let { context ->
            val contextScore = calculateDeviceContextScore(tool, context)
            score += contextScore * 0.15f // 15% weight for context compatibility
        }
        
        return score.coerceIn(0.0f, 1.0f)
    }
    
    private fun calculateGenericKeywordScore(tool: MobileTool, requestLower: String): Float {
        // Tool-specific keyword patterns
        val toolKeywords = when (tool.name) {
            "device_context" -> listOf("battery", "device", "system", "status", "phone", "info", "storage", "memory", "network", "wifi")
            "WebFetchTool" -> listOf("web", "fetch", "url", "website", "download", "get", "check", "http", "https", "site")
            "ExtractorTool" -> listOf("extract", "parse", "content", "html", "text", "readable")
            "SummarizerTool" -> listOf("summarize", "summary", "brief", "overview", "tldr", "short")
            "FileTool" -> listOf("file", "read", "open", "show", "view", "content", "text", "document", "config", "log", "code", "script")
            "WriteFileTool" -> listOf("write", "create", "save", "make", "new", "file", "document", "note", "config", "script", "export")
            "ListTool" -> listOf("list", "ls", "dir", "directory", "browse", "show", "files", "folders", "contents", "what's", "whats")
            "GlobTool" -> listOf("find", "search", "glob", "pattern", "match", "locate", "*.kt", "*.java", "files", "where")
            "GrepTool" -> listOf("grep", "search", "find", "text", "content", "inside", "contains", "look", "rg", "ripgrep")
            else -> listOf(tool.name.lowercase())
        }
        
        val requestWords = requestLower.split(" ")
        val matches = toolKeywords.count { keyword ->
            requestWords.any { it.contains(keyword) } || requestLower.contains(keyword)
        }
        
        return if (toolKeywords.isNotEmpty()) {
            (matches.toFloat() / toolKeywords.size).coerceIn(0.0f, 1.0f)
        } else 0f
    }
    
    private fun calculateCapabilityScore(tool: MobileTool, request: String): Float {
        // Analyze request for action patterns that match tool capabilities
        val requestLower = request.lowercase()
        
        return when {
            // Read/information requests
            (requestLower.contains("what") || requestLower.contains("check") || requestLower.contains("show")) 
                && tool.riskLevel == RiskLevel.LOW -> 0.8f
            
            // Action/modification requests
            (requestLower.contains("set") || requestLower.contains("change") || requestLower.contains("update"))
                && tool.riskLevel >= RiskLevel.MEDIUM -> 0.7f
            
            // Urgent/priority requests
            (requestLower.contains("urgent") || requestLower.contains("important") || requestLower.contains("now"))
                && tool.riskLevel <= RiskLevel.MEDIUM -> 0.9f
            
            else -> 0.5f
        }
    }
    
    private fun calculateDeviceContextScore(tool: MobileTool, context: DeviceContext): Float {
        var score = 1.0f
        
        // Battery considerations
        when {
            context.batteryLevel < 15 && tool.riskLevel >= RiskLevel.HIGH -> score -= 0.6f
            context.batteryLevel < 30 && tool.riskLevel >= RiskLevel.MEDIUM -> score -= 0.3f
        }
        
        // Network requirements
        val requiresNetwork = tool.name.contains("Web", ignoreCase = true) || 
                             tool.name.contains("fetch", ignoreCase = true)
        if (requiresNetwork && context.networkState == NetworkState.DISCONNECTED) {
            score -= 0.8f
        }
        
        // Permission availability
        val hasRequiredPermissions = tool.requiredPermissions.all { permission ->
            context.grantedPermissions.any { it.contains(permission) }
        }
        if (!hasRequiredPermissions) {
            score -= 0.4f
        }
        
        return score.coerceIn(0.0f, 1.0f)
    }
    
    private fun generateReasoningForScore(tool: MobileTool, request: String, score: Float): String {
        return when {
            score >= 0.8f -> "Excellent match: ${tool.name} directly addresses '${request.take(50)}'"
            score >= 0.6f -> "Good match: ${tool.name} can help with '${request.take(50)}'"
            score >= 0.4f -> "Partial match: ${tool.name} might be useful for '${request.take(50)}'"
            else -> "Low relevance: ${tool.name} has limited applicability"
        }
    }
    
    private fun calculateConstraints(tool: MobileTool, deviceContext: DeviceContext?): List<String> {
        val constraints = mutableListOf<String>()
        
        deviceContext?.let { context ->
            // Permission constraints
            val missingPermissions = tool.requiredPermissions.filter { permission ->
                !context.grantedPermissions.any { it.contains(permission) }
            }
            if (missingPermissions.isNotEmpty()) {
                constraints.add("Requires permissions: ${missingPermissions.joinToString()}")
            }
            
            // Battery constraints
            if (context.batteryLevel < 20 && tool.riskLevel >= RiskLevel.MEDIUM) {
                constraints.add("High battery usage on low battery (${context.batteryLevel}%)")
            }
            
            // Network constraints
            val requiresNetwork = tool.name.contains("Web", ignoreCase = true)
            if (requiresNetwork && context.networkState != NetworkState.WIFI) {
                when (context.networkState) {
                    NetworkState.MOBILE -> constraints.add("Uses mobile data")
                    NetworkState.DISCONNECTED -> constraints.add("Requires internet connection")
                    else -> {}
                }
            }
        }
        
        return constraints
    }
    
    /**
     * Get tool statistics
     */
    fun getToolStats(): ToolRegistryStats {
        val riskCounts = RiskLevel.values().associateWith { riskLevel ->
            tools.values.count { it.riskLevel == riskLevel }
        }
        
        return ToolRegistryStats(
            totalTools = tools.size,
            riskLevelCounts = riskCounts,
            totalPermissions = tools.values.flatMap { it.requiredPermissions }.distinct().size
        )
    }
}

/**
 * Statistics about the tool registry
 */
data class ToolRegistryStats(
    val totalTools: Int,
    val riskLevelCounts: Map<RiskLevel, Int>,
    val totalPermissions: Int
)