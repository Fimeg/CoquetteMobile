package com.yourname.coquettemobile.core.orchestration

import com.yourname.coquettemobile.core.tools.RiskLevel
import kotlinx.serialization.Serializable

/**
 * Represents a complete execution plan for a complex operation
 * Contains ordered steps with dependency management
 */
@Serializable
data class ExecutionPlan(
    val planId: String,
    val originalIntent: String,
    val steps: List<OperationStep>,
    val estimatedDurationMs: Long,
    val riskLevel: RiskLevel = RiskLevel.LOW,
    val createdAt: Long = System.currentTimeMillis()
) {

    /**
     * Gets steps that can be executed now (all dependencies satisfied)
     */
    fun getExecutableSteps(completedStepIds: Set<String>): List<OperationStep> {
        return steps.filter { step ->
            step.canExecute(completedStepIds) && step.id !in completedStepIds
        }
    }

    /**
     * Checks if there are any steps remaining that can be executed
     */
    fun hasExecutableSteps(completedStepIds: Set<String>): Boolean {
        return getExecutableSteps(completedStepIds).isNotEmpty()
    }

    /**
     * Gets all steps that depend on a specific step
     */
    fun getDependentSteps(stepId: String): List<OperationStep> {
        return steps.filter { it.dependencies.contains(stepId) }
    }

    /**
     * Validates that all step dependencies can be resolved
     */
    fun validateDependencies(): ValidationResult {
        val stepIds = steps.map { it.id }.toSet()
        val invalidDependencies = mutableListOf<String>()

        steps.forEach { step ->
            step.dependencies.forEach { depId ->
                if (depId !in stepIds) {
                    invalidDependencies.add("Step '${step.id}' depends on non-existent step '$depId'")
                }
            }
        }

        return if (invalidDependencies.isEmpty()) {
            ValidationResult.valid()
        } else {
            ValidationResult.invalid(invalidDependencies)
        }
    }

    /**
     * Checks for circular dependencies in the plan
     */
    fun hasCircularDependencies(): Boolean {
        val visited = mutableSetOf<String>()
        val recursionStack = mutableSetOf<String>()

        fun hasCycleDFS(stepId: String): Boolean {
            if (stepId in recursionStack) return true
            if (stepId in visited) return false

            visited.add(stepId)
            recursionStack.add(stepId)

            val step = steps.find { it.id == stepId }
            step?.dependencies?.forEach { depId ->
                if (hasCycleDFS(depId)) return true
            }

            recursionStack.remove(stepId)
            return false
        }

        return steps.any { hasCycleDFS(it.id) }
    }

    /**
     * Gets execution order considering dependencies
     */
    fun getExecutionOrder(): List<List<OperationStep>> {
        val completed = mutableSetOf<String>()
        val executionWaves = mutableListOf<List<OperationStep>>()

        while (completed.size < steps.size) {
            val executableNow = getExecutableSteps(completed)
            if (executableNow.isEmpty()) break // Circular dependency or impossible plan

            executionWaves.add(executableNow)
            completed.addAll(executableNow.map { it.id })
        }

        return executionWaves
    }

    /**
     * Creates a copy of the plan with additional steps
     */
    fun withAdditionalSteps(newSteps: List<OperationStep>): ExecutionPlan {
        return copy(
            steps = steps + newSteps,
            estimatedDurationMs = estimatedDurationMs + newSteps.sumOf { it.estimatedDurationMs }
        )
    }

    /**
     * Creates a copy with updated risk level
     */
    fun withRiskLevel(newRiskLevel: RiskLevel): ExecutionPlan {
        return copy(riskLevel = newRiskLevel)
    }
}

/**
 * Result of a complete operation orchestration
 */
@Serializable
data class OperationResult(
    val sessionId: String,
    val success: Boolean,
    val message: String,
    val combinedData: Map<String, String> = emptyMap(), // Changed Any to String
    val stepResults: List<StepResult> = emptyList(),
    val executionTimeMs: Long = 0L,
    val error: String? = null
) {

    /**
     * Gets successful steps only
     */
    fun getSuccessfulSteps(): List<StepResult> {
        return stepResults.filter { it.success }
    }

    /**
     * Gets failed steps only
     */
    fun getFailedSteps(): List<StepResult> {
        return stepResults.filter { !it.success }
    }

    /**
     * Gets results from specific router domain
     */
    fun getResultsFromDomain(domain: RouterDomain): List<StepResult> {
        return stepResults.filter { it.domain == domain }
    }

    companion object {
        fun success(
            sessionId: String,
            message: String,
            combinedData: Map<String, String> = emptyMap(), // Changed Any to String
            stepResults: List<StepResult> = emptyList()
        ): OperationResult {
            return OperationResult(
                sessionId = sessionId,
                success = true,
                message = message,
                combinedData = combinedData,
                stepResults = stepResults
            )
        }

        fun failure(
            sessionId: String,
            error: String,
            executionTimeMs: Long = 0L,
            stepResults: List<StepResult> = emptyList()
        ): OperationResult {
            return OperationResult(
                sessionId = sessionId,
                success = false,
                message = "Operation failed",
                stepResults = stepResults,
                executionTimeMs = executionTimeMs,
                error = error
            )
        }
    }
}
