package com.yourname.coquettemobile.core.tools

import javax.inject.Inject
import javax.inject.Singleton

/**
 * Registry for all mobile tools
 * Modeled after desktop Coquette's ToolRegistry pattern
 */
@Singleton
class MobileToolRegistry @Inject constructor(
    private val deviceContextTool: DeviceContextTool
    // Additional tools will be injected here
) {
    private val tools = mutableMapOf<String, MobileTool>()
    
    init {
        registerTools()
    }
    
    private fun registerTools() {
        // Register core tools
        registerTool(deviceContextTool)
        
        // Future tools will be registered here:
        // registerTool(cameraTool)
        // registerTool(locationTool)
        // registerTool(fileTool)
        // etc.
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