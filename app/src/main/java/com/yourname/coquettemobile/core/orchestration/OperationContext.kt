package com.yourname.coquettemobile.core.orchestration

import java.util.UUID

/**
 * Immutable context that flows between routers during complex operations
 * Evolves with each step while maintaining operation history
 */
data class OperationContext(
    val sessionId: String = UUID.randomUUID().toString(),
    val originalIntent: String,
    val discoveredCapabilities: Map<String, Any> = emptyMap(),
    val previousResults: List<StepResult> = emptyList(),
    val userConstraints: List<Constraint> = emptyList(),
    val deviceContext: Map<String, Any> = emptyMap(),
    val securityLevel: SecurityLevel = SecurityLevel.STANDARD,
    val timeoutMs: Long = 300000L, // 5 minutes default
    private val startTimeMs: Long = System.currentTimeMillis()
) {
    
    /**
     * Creates evolved context with new results and discoveries
     */
    fun evolve(
        newResult: StepResult,
        newCapabilities: Map<String, Any> = emptyMap()
    ): OperationContext {
        return copy(
            discoveredCapabilities = discoveredCapabilities + newCapabilities,
            previousResults = previousResults + newResult
        )
    }
    
    /**
     * Gets all results from a specific router domain
     */
    fun getResultsFromDomain(domain: RouterDomain): List<StepResult> {
        return previousResults.filter { it.domain == domain }
    }
    
    /**
     * Checks if operation has exceeded time limit
     */
    fun isExpired(): Boolean {
        val elapsed = System.currentTimeMillis() - startTimeMs
        return elapsed > timeoutMs
    }
}

/**
 * User-defined constraints for operations
 */
data class Constraint(
    val type: ConstraintType,
    val value: String,
    val description: String
)

enum class ConstraintType {
    MAX_EXECUTION_TIME,
    REQUIRED_PERMISSION,
    FORBIDDEN_OPERATION,
    TARGET_RESTRICTION,
    DATA_SENSITIVITY
}

enum class SecurityLevel {
    MINIMAL,    // Basic operations only
    STANDARD,   // Normal tool access
    ELEVATED,   // Privileged operations
    MAXIMUM     // Full system access (requires explicit consent)
}