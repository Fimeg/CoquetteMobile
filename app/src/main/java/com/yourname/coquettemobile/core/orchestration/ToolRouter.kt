package com.yourname.coquettemobile.core.orchestration

import com.yourname.coquettemobile.core.tools.MobileTool
import com.yourname.coquettemobile.core.tools.RiskLevel as ToolRiskLevel

/**
 * Interface for specialized Tool Routers in the Hierarchical Agent System
 * Each router is a domain expert that manages a logical group of tools
 */
interface ToolRouter {
    
    /**
     * The specialized domain this router handles
     */
    val domain: RouterDomain
    
    /**
     * Human-readable name for this router
     */
    val name: String
    
    /**
     * Tools managed by this router
     */
    val managedTools: List<MobileTool>
    
    /**
     * Keywords/patterns this router can handle
     */
    val capabilities: List<String>
    
    /**
     * Priority level for router selection (higher = preferred)
     */
    val priority: Int get() = 50
    
    /**
     * Determines if this router can handle a specific operation step
     */
    suspend fun canHandle(operation: OperationStep): Boolean
    
    /**
     * Executes a single operation step with full context
     */
    suspend fun executeStep(step: OperationStep, context: OperationContext): StepResult
    
    /**
     * Plans sub-steps needed to achieve a high-level goal
     * This is where router domain expertise shines
     */
    suspend fun planSubSteps(goal: String, context: OperationContext): List<OperationStep>
    
    /**
     * Estimates execution time for a given operation
     */
    suspend fun estimateExecutionTime(operation: OperationStep): Long {
        return 30000L // Default 30 seconds
    }
    
    /**
     * Validates that required tools and permissions are available
     */
    suspend fun validateCapabilities(operation: OperationStep): ValidationResult
    
    /**
     * Provides router-specific insights for operation planning
     */
    suspend fun getInsights(context: OperationContext): RouterInsights
}

/**
 * Result of capability validation
 */
data class ValidationResult(
    val valid: Boolean,
    val missingRequirements: List<String> = emptyList(),
    val warnings: List<String> = emptyList()
) {
    companion object {
        fun valid() = ValidationResult(true)
        fun invalid(requirements: List<String>) = ValidationResult(false, requirements)
    }
}

/**
 * Router-specific insights for operation planning
 */
data class RouterInsights(
    val domain: RouterDomain,
    val availableCapabilities: List<String>,
    val currentConstraints: List<String>,
    val recommendedApproach: String,
    val riskAssessment: ToolRiskLevel
)

// Using RiskLevel from MobileTool instead of redefining