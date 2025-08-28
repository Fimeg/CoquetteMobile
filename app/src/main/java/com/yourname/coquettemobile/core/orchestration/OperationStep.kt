package com.yourname.coquettemobile.core.orchestration

import kotlinx.serialization.Serializable

/**
 * Represents a single step in a complex multi-step operation
 */
@Serializable
data class OperationStep(
    val id: String,
    val type: StepType,
    val domain: RouterDomain,
    val description: String,
    val parameters: Map<String, String> = emptyMap(), // Changed Any to String
    val dependencies: List<String> = emptyList(), // IDs of steps that must complete first
    val optional: Boolean = false,
    val retryable: Boolean = true,
    val estimatedDurationMs: Long = 30000L
) {
    
    /**
     * Checks if this step can execute given completed step IDs
     */
    fun canExecute(completedStepIds: Set<String>): Boolean {
        return dependencies.all { it in completedStepIds }
    }
}

/**
 * Types of operations that can be performed by routers
 */
@Serializable
enum class StepType {
    // System Intelligence
    ENVIRONMENT_DISCOVERY,
    TARGET_ANALYSIS,
    CAPABILITY_ASSESSMENT,
    VULNERABILITY_SCAN,

    // Payload Delivery
    PAYLOAD_GENERATION,
    SCRIPT_EXECUTION,
    COMMAND_INJECTION,
    SOFTWARE_INSTALLATION,

    // Network Operations
    NETWORK_DISCOVERY,
    CONNECTION_ESTABLISHMENT,
    DATA_TRANSFER,
    TUNNEL_CREATION,

    // File Operations
    FILE_ENUMERATION,
    CONTENT_EXTRACTION,
    FILE_TRANSFER,
    DIRECTORY_ANALYSIS,

    // Device Control
    HID_INJECTION,
    HARDWARE_QUERY,
    PERMISSION_ESCALATION,
    DEVICE_CONFIGURATION,

    // Security Operations
    CREDENTIAL_EXTRACTION,
    PRIVILEGE_ANALYSIS,
    SECURITY_BYPASS,
    FORENSIC_COLLECTION,

    // Web Operations
    WEB_CONTENT_FETCH,
    DATA_SUMMARIZATION,
    WEB_INTELLIGENCE,

    // Data Processing
    CONTENT_ANALYSIS,
    DATA_TRANSFORMATION,
    PATTERN_MATCHING,
    RESULT_SYNTHESIS,

    // Communication
    MESSAGE_DELIVERY,
    NOTIFICATION_DISPATCH,
    STATUS_REPORTING,
    LOG_TRANSMISSION
}

/**
 * Result of executing an operation step
 */
@Serializable
data class StepResult(
    val stepId: String,
    val domain: RouterDomain,
    val success: Boolean,
    val data: Map<String, String> = emptyMap(), // Changed Any to String
    val error: String? = null,
    val executionTimeMs: Long,
    val requiresReplanning: Boolean = false,
    val nextSuggestedSteps: List<OperationStep> = emptyList()
) {

    companion object {
        fun success(
            stepId: String,
            domain: RouterDomain,
            data: Map<String, String> = emptyMap(), // Changed Any to String
            executionTimeMs: Long = 0L
        ): StepResult {
            return StepResult(
                stepId = stepId,
                domain = domain,
                success = true,
                data = data,
                executionTimeMs = executionTimeMs
            )
        }

        fun failure(
            stepId: String,
            domain: RouterDomain,
            error: String,
            executionTimeMs: Long = 0L,
            requiresReplanning: Boolean = false
        ): StepResult {
            return StepResult(
                stepId = stepId,
                domain = domain,
                success = false,
                error = error,
                executionTimeMs = executionTimeMs,
                requiresReplanning = requiresReplanning
            )
        }

        fun unsupported(stepId: String = "unknown", domain: RouterDomain = RouterDomain.ANDROID_INTELLIGENCE): StepResult {
            return StepResult(
                stepId = stepId,
                domain = domain,
                success = false,
                error = "Operation not supported by this router",
                executionTimeMs = 0L
            )
        }
    }
}
