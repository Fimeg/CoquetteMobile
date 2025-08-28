package com.yourname.coquettemobile.core.orchestration

import com.yourname.coquettemobile.core.ai.OllamaService
import com.yourname.coquettemobile.core.logging.CoquetteLogger
import com.yourname.coquettemobile.core.preferences.AppPreferences
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The CEO of the Hierarchical Agent System.
 * This agent is now refactored to support a transparent, streaming execution model.
 * It orchestrates complex operations by planning, executing, and synthesizing in distinct, observable phases.
 */
@Singleton
class OrchestratorAgent @Inject constructor(
    private val ollamaService: OllamaService,
    private val routerRegistry: RouterRegistry,
    private val appPreferences: AppPreferences,
    private val logger: CoquetteLogger
) {

    /**
     * CEO Decision: Choose the right department and delegate the entire request
     */
    suspend fun delegateRequest(
        userIntent: String,
        context: OperationContext
    ): StepResult {
        logger.i("OrchestratorAgent", "CEO delegating request: $userIntent")
        
        // Simple CEO decision making - pick the right department
        val selectedRouter = selectAppropriateRouter(userIntent, context)
        if (selectedRouter == null) {
            return StepResult.failure(
                "delegation_failed",
                RouterDomain.SYSTEM_INTELLIGENCE,
                "No suitable department found for request: $userIntent",
                0L
            )
        }
        
        logger.i("OrchestratorAgent", "Delegating to ${selectedRouter.name} for: $userIntent")
        
        // Create a simple delegation step
        val delegationStep = OperationStep(
            id = "ceo_delegation",
            type = determineStepType(userIntent, selectedRouter.domain),
            domain = selectedRouter.domain,
            description = userIntent // Pass the full user request
        )
        
        // Let the department handle it their way
        return selectedRouter.executeStep(delegationStep, context)
    }

    /**
     * Simple CEO decision making - choose the right department
     */
    private fun selectAppropriateRouter(userIntent: String, context: OperationContext): ToolRouter? {
        val intent = userIntent.lowercase()
        val availableRouters = routerRegistry.getAllRouters()
        
        // CEO decision logic - simple and direct
        return when {
            // Web content requests go to WebScraperRouter
            intent.contains("hacker news") || intent.contains("web") || 
            intent.contains("scrape") || intent.contains("fetch") ||
            intent.contains("what's on") -> availableRouters.find { it.domain == RouterDomain.WEB_INTELLIGENCE }
            
            // System analysis goes to SystemIntelRouter  
            intent.contains("system") || intent.contains("device") ||
            intent.contains("analyze") || intent.contains("info") -> availableRouters.find { it.domain == RouterDomain.SYSTEM_INTELLIGENCE }
            
            // Desktop HID operations go to DesktopExploitRouter  
            intent.contains("execute") || intent.contains("script") ||
            intent.contains("install") || intent.contains("run") -> availableRouters.find { it.domain == RouterDomain.DESKTOP_EXPLOIT }
            
            // Default fallback - try SystemIntelRouter for general requests
            else -> availableRouters.find { it.domain == RouterDomain.SYSTEM_INTELLIGENCE }
        }
    }

    /**
     * Determine appropriate step type based on user intent and selected domain
     */
    private fun determineStepType(userIntent: String, domain: RouterDomain): StepType {
        val intent = userIntent.lowercase()
        
        return when (domain) {
            RouterDomain.WEB_INTELLIGENCE -> StepType.WEB_INTELLIGENCE
            RouterDomain.SYSTEM_INTELLIGENCE -> when {
                intent.contains("analyze") -> StepType.TARGET_ANALYSIS
                intent.contains("scan") -> StepType.ENVIRONMENT_DISCOVERY
                else -> StepType.CAPABILITY_ASSESSMENT
            }
            RouterDomain.DESKTOP_EXPLOIT -> when {
                intent.contains("script") -> StepType.SCRIPT_EXECUTION
                intent.contains("install") -> StepType.SOFTWARE_INSTALLATION
                else -> StepType.PAYLOAD_GENERATION
            }
            else -> StepType.TARGET_ANALYSIS
        }
    }

    // DEPRECATED: Old complex planning methods - will be removed
    fun executePlan(
        initialPlan: ExecutionPlan,
        context: OperationContext
    ): Flow<StepResult> = flow {
        logger.i("OrchestratorAgent", "Phase 2: Starting execution of plan with ${initialPlan.steps.size} steps.")
        var currentPlan = initialPlan
        val allResults = mutableListOf<StepResult>()
        val completedStepIds = mutableSetOf<String>()

        while (currentPlan.hasExecutableSteps(completedStepIds)) {
            if (context.isExpired()) {
                logger.w("OrchestratorAgent", "Operation timeout reached during plan execution.")
                // Potentially emit an error StepResult here
                break
            }

            val readySteps = currentPlan.getExecutableSteps(completedStepIds)
            logger.d("OrchestratorAgent", "Executing batch of ${readySteps.size} parallel steps.")
            val stepResults = executeStepsInParallel(
                readySteps, 
                if (allResults.isNotEmpty()) context.evolve(allResults.last()) else context
            )

            stepResults.forEach { emit(it) }

            allResults.addAll(stepResults)
            completedStepIds.addAll(stepResults.filter { it.success }.map { it.stepId })

            val replanningRequired = stepResults.any { it.requiresReplanning }
            if (replanningRequired) {
                logger.i("OrchestratorAgent", "Replanning required based on step results.")
                currentPlan = replanBasedOnResults(currentPlan, allResults, context)
                // Emitting a special 'replanning' step could be a future enhancement
            }
        }
        logger.i("OrchestratorAgent", "Plan execution complete.")
    }

    /**
     * Phase 3: Synthesizes all step results into a final, natural-language response.
     */
    suspend fun synthesizeFinalResponse(
        results: List<StepResult>,
        context: OperationContext
    ): String {
        logger.i("OrchestratorAgent", "Phase 3: Synthesizing final response from ${results.size} results.")
        val successCount = results.count { it.success }
        if (successCount == 0 && results.isNotEmpty()) {
            logger.w("OrchestratorAgent", "All operation steps failed. Synthesizing failure message.")
            return "Unfortunately, the operation could not be completed as all steps failed. The last error was: ${results.last().error}"
        }

        val synthesisPrompt = buildSynthesisPrompt(results, context)
        val response = ollamaService.sendMessage(
            message = synthesisPrompt,
            model = appPreferences.orchestratorModel ?: "deepseek-r1:1.5b",
            systemPrompt = SYNTHESIS_SYSTEM_PROMPT
        )
        logger.i("OrchestratorAgent", "Synthesis complete.")
        return response.content
    }


    // --- PRIVATE HELPER FUNCTIONS ---

    /**
     * Executes multiple steps in parallel where dependencies allow.
     */
    private suspend fun executeStepsInParallel(
        steps: List<OperationStep>,
        context: OperationContext
    ): List<StepResult> = coroutineScope {
        steps.map { step ->
            async {
                executeIndividualStep(step, context)
            }
        }.awaitAll()
    }

    /**
     * Executes a single step by delegating to the appropriate router.
     */
    private suspend fun executeIndividualStep(
        step: OperationStep,
        context: OperationContext
    ): StepResult {
        val startTime = System.currentTimeMillis()
        logger.d("OrchestratorAgent", "Executing step: ${step.id} (${step.type}) via domain '${step.domain}'")

        return try {
            val router = routerRegistry.selectOptimalRouter(step)
                ?: return StepResult.failure(
                    step.id,
                    step.domain,
                    "No suitable router available for domain ${step.domain}",
                    System.currentTimeMillis() - startTime
                )

            val validation = router.validateCapabilities(step)
            if (!validation.valid) {
                return StepResult.failure(
                    step.id,
                    step.domain,
                    "Router validation failed: ${validation.missingRequirements}",
                    System.currentTimeMillis() - startTime
                )
            }

            router.executeStep(step, context)
        } catch (e: Exception) {
            val executionTime = System.currentTimeMillis() - startTime
            logger.e("OrchestratorAgent", "Step ${step.id} execution failed: ${e.message}")
            StepResult.failure(
                step.id,
                step.domain,
                "Execution error: ${e.message}",
                executionTime
            )
        }
    }

    /**
     * Replans the operation based on intermediate results.
     */
    private suspend fun replanBasedOnResults(
        currentPlan: ExecutionPlan,
        results: List<StepResult>,
        context: OperationContext
    ): ExecutionPlan {
        logger.i("OrchestratorAgent", "Replanning operation based on ${results.size} results.")
        val replanningPrompt = buildReplanningPrompt(currentPlan, results)

        val response = ollamaService.sendMessage(
            message = replanningPrompt,
            model = appPreferences.orchestratorModel ?: "deepseek-r1:1.5b",
            systemPrompt = REPLANNING_SYSTEM_PROMPT
        )

        // This shouldn't be reached anymore since we removed PlanParser
        throw IllegalStateException("Replanning not yet implemented without PlanParser")
    }

    // --- PROMPT BUILDERS ---

    private fun buildPlanningPrompt(userIntent: String, context: OperationContext): String {
        val availableRouters = routerRegistry.getAllRouters()
        val routerCapabilities = availableRouters.joinToString("\n") {
            val capabilities = it.capabilities.joinToString(", ")
            "- **${it.domain}**: This department is an expert in ${it.domain.name.replace('_', ' ').lowercase()}. Its capabilities include: $capabilities"
        }

        return """
        **User's Request:** "$userIntent"

        **The Departments and Their Expertise:**
        $routerCapabilities

        Based on the user's request, create the most direct and efficient execution plan to accomplish the goal.
        """.trimIndent()
    }

    private fun buildSynthesisPrompt(results: List<StepResult>, context: OperationContext): String {
        val successfulResults = results.filter { it.success }
        val failedResults = results.filter { !it.success }

        return """
        Operation: ${context.originalIntent}

        Successful Steps (${successfulResults.size}):
        ${successfulResults.joinToString("\n") { "- ${it.stepId}: ${it.data}" }}

        Failed Steps (${failedResults.size}):
        ${failedResults.joinToString("\n") { "- ${it.stepId}: ${it.error}" }}

        Synthesize these results into a natural, conversational response for the user.
        Focus on what was accomplished and any important findings.
        """.trimIndent()
    }

    private fun buildReplanningPrompt(
        currentPlan: ExecutionPlan,
        results: List<StepResult>
    ): String {
        return """
        Original Plan: ${currentPlan.steps.size} steps
        Current Results: ${results.size} completed

        Recent Results:
        ${results.takeLast(3).joinToString("\n") { "- ${it.stepId}: ${if (it.success) "SUCCESS" else "FAILED: ${it.error}"}" }}

        Based on these results, create an updated execution plan to achieve the original goal.
        Adapt to new information and handle any failures or unexpected discoveries.
        """.trimIndent()
    }

    companion object {
        private const val ORCHESTRATOR_SYSTEM_PROMPT = """
        You are the OrchestratorAgent, the CEO of a company of AI specialists. Your one and only job is to create the most efficient, simple, and direct execution plan to accomplish the user's goal.

        **Guiding Principles:**
        - **Simplicity First:** Always choose the simplest path. Do not add unnecessary steps.
        - **Direct Action:** If a tool can directly accomplish the goal, use it.
        - **Trust Your Departments:** Delegate the task to the most appropriate department (router) and trust them to handle the details. You do not need to specify every single action they should take.

        Your job is to create the high-level plan, and the departments will execute it.
        """

        private const val SYNTHESIS_SYSTEM_PROMPT = """
        You are synthesizing the results of a complex operation.
        Create natural, conversational responses that explain what was accomplished.
        Focus on practical outcomes and actionable insights for the user.
        Be concise but informative.
        """

        private const val REPLANNING_SYSTEM_PROMPT = """
        You are adapting an execution plan based on new information.
        Analyze what worked, what failed, and what new opportunities emerged.
        Create an updated plan that leverages successes and addresses failures.
        Maintain the original goal while adapting the approach.
        """
    }
}
