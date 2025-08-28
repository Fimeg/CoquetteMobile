package com.yourname.coquettemobile.core.orchestration.routers

import com.yourname.coquettemobile.core.ai.OllamaService
import com.yourname.coquettemobile.core.logging.CoquetteLogger
import com.yourname.coquettemobile.core.orchestration.*
import com.yourname.coquettemobile.core.preferences.AppPreferences
import com.yourname.coquettemobile.core.tools.*
import kotlinx.coroutines.flow.collect
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

/**
 * WebScraperRouter - Specialized router for web content gathering and processing
 * Domain expert in fetching, extracting, and summarizing web content
 */
@Singleton
class WebScraperRouter @Inject constructor(
    private val webFetchTool: WebFetchTool,
    private val extractorTool: ExtractorTool,
    private val summarizerTool: SummarizerTool,
    private val ollamaService: OllamaService,
    private val appPreferences: AppPreferences,
    private val logger: CoquetteLogger
) : ToolRouter {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    override val domain = RouterDomain.WEB_INTELLIGENCE
    override val name = "WebScraperRouter"

    override val managedTools: List<MobileTool> = listOf(
        webFetchTool,
        extractorTool,
        summarizerTool
    )

    override val capabilities = listOf(
        "web content fetching", "html parsing", "content extraction",
        "web scraping", "data summarization", "text processing",
        "news aggregation", "api consumption", "web intelligence"
    )

    override val priority = 90 // High priority for web content

    override suspend fun canHandle(operation: OperationStep): Boolean {
        return when (operation.type) {
            StepType.WEB_CONTENT_FETCH,
            StepType.CONTENT_EXTRACTION,
            StepType.DATA_SUMMARIZATION,
            StepType.WEB_INTELLIGENCE -> true
            else -> operation.domain == domain
        }
    }

    override suspend fun executeStep(step: OperationStep, context: OperationContext): StepResult {
        val startTime = System.currentTimeMillis()
        logger.d(name, "WebScraperRouter handling request: ${step.description}")

        return try {
            // For web intelligence requests, execute the full workflow
            when (step.type) {
                StepType.WEB_INTELLIGENCE -> handleWebIntelligenceRequest(step, context)
                StepType.WEB_CONTENT_FETCH -> fetchWebContent(step, context)
                StepType.CONTENT_EXTRACTION -> extractContent(step, context)
                StepType.DATA_SUMMARIZATION -> summarizeContent(step, context)
                else -> handleGenericWebRequest(step, context)
            }
        } catch (e: Exception) {
            val executionTime = System.currentTimeMillis() - startTime
            logger.e(name, "Web scraping failed: ${e.message}")

            StepResult.failure(
                step.id,
                domain,
                "WebScraperRouter execution error: ${e.message}",
                executionTime
            )
        }
    }

    override suspend fun planSubSteps(goal: String, context: OperationContext): List<OperationStep> {
        logger.d(name, "WebScraperRouter using AI to plan for: $goal")
        
        return try {
            val aiGeneratedPlan = generatePlanWithAI(goal, context)
            aiGeneratedPlan.ifEmpty {
                // Fallback if AI planning fails
                listOf(
                    OperationStep(
                        id = "web_intelligence_${System.currentTimeMillis()}",
                        type = StepType.WEB_INTELLIGENCE,
                        domain = domain,
                        description = goal,
                        dependencies = emptyList()
                    )
                )
            }
        } catch (e: Exception) {
            logger.e(name, "AI planning failed: ${e.message}, using fallback")
            listOf(
                OperationStep(
                    id = "web_intelligence_${System.currentTimeMillis()}",
                    type = StepType.WEB_INTELLIGENCE,
                    domain = domain,
                    description = goal,
                    dependencies = emptyList()
                )
            )
        }
    }

    private suspend fun generatePlanWithAI(goal: String, context: OperationContext): List<OperationStep> {
        val planningPrompt = buildPlanningPrompt(goal, context)
        val model = appPreferences.orchestratorModel ?: "deepseek-r1:1.5b"
        
        val responseBuilder = StringBuilder()
        ollamaService.sendMessageStream(
            message = planningPrompt,
            model = model,
            systemPrompt = "You are a web scraping specialist. Analyze requests and create step-by-step execution plans."
        ).collect { chunk ->
            responseBuilder.append(chunk)
        }
        
        val response = responseBuilder.toString()
        logger.d(name, "AI planning response: $response")
        
        return try {
            val planResponse = json.decodeFromString<WebScrapingPlan>(extractJsonFromResponse(response))
            planResponse.steps.mapIndexed { index, step ->
                OperationStep(
                    id = "web_step_${System.currentTimeMillis()}_$index",
                    type = mapStepType(step.type),
                    domain = domain,
                    description = step.description,
                    dependencies = step.dependencies
                )
            }
        } catch (e: Exception) {
            logger.e(name, "Failed to parse AI plan: ${e.message}")
            emptyList()
        }
    }

    private fun buildPlanningPrompt(goal: String, context: OperationContext): String {
        return """
        You are WebScraperRouter, a domain expert in web content analysis and extraction.
        
        User Request: "$goal"
        
        Available step types:
        - WEB_CONTENT_FETCH: Fetch content from a specific URL
        - CONTENT_EXTRACTION: Extract specific data from fetched content  
        - DATA_SUMMARIZATION: Summarize and process extracted information
        - WEB_INTELLIGENCE: General web intelligence gathering
        
        Create a JSON execution plan with steps needed to fulfill this web-related request.
        Think about what websites to access, what data to extract, and how to process it.
        
        Response format:
        {
          "steps": [
            {
              "type": "WEB_CONTENT_FETCH",
              "description": "Fetch content from target website",
              "dependencies": []
            }
          ]
        }
        """.trimIndent()
    }

    private fun extractJsonFromResponse(response: String): String {
        val startIndex = response.indexOf('{')
        val endIndex = response.lastIndexOf('}') + 1
        return if (startIndex != -1 && endIndex > startIndex) {
            response.substring(startIndex, endIndex)
        } else {
            response
        }
    }

    private fun mapStepType(typeString: String): StepType {
        return when (typeString.uppercase()) {
            "WEB_CONTENT_FETCH" -> StepType.WEB_CONTENT_FETCH
            "CONTENT_EXTRACTION" -> StepType.CONTENT_EXTRACTION
            "DATA_SUMMARIZATION" -> StepType.DATA_SUMMARIZATION
            else -> StepType.WEB_INTELLIGENCE
        }
    }

    @Serializable
    data class WebScrapingPlan(
        val steps: List<PlanStep>
    )

    @Serializable
    data class PlanStep(
        val type: String,
        val description: String,
        val dependencies: List<String> = emptyList()
    )

    override suspend fun validateCapabilities(operation: OperationStep): ValidationResult {
        // Basic validation - we have all the tools we need
        return ValidationResult.valid()
    }

    override suspend fun getInsights(context: OperationContext): RouterInsights {
        return RouterInsights(
            domain = domain,
            availableCapabilities = capabilities,
            currentConstraints = emptyList(),
                              recommendedApproach = "Fetch → Extract → Summarize workflow for web content",
                              riskAssessment = RiskLevel.LOW // Web scraping is typically low risk
        )
    }

    // Implementation methods

    private suspend fun handleWebIntelligenceRequest(step: OperationStep, context: OperationContext): StepResult {
        val startTime = System.currentTimeMillis()
        val description = step.description.lowercase()

        logger.d(name, "Handling web intelligence request: $description")

        // Determine target URL based on request
        val url = when {
            description.contains("hacker news") -> "https://news.ycombinator.com"
            description.contains("reddit") -> "https://reddit.com"
            else -> step.parameters["url"] ?: return StepResult.failure(
                step.id, domain, "No URL specified for web intelligence request", 0L
            )
        }

        // Execute the full web scraping workflow
        return executeWebScrapingWorkflow(step.id, url, context, startTime)
    }

    private suspend fun handleGenericWebRequest(step: OperationStep, context: OperationContext): StepResult {
        val startTime = System.currentTimeMillis()
        val description = step.description.lowercase()

        // If it's a generic request that mentions web content, handle it
        if (description.contains("web") || description.contains("fetch") || description.contains("scrape")) {
            val url = step.parameters["url"] ?: return StepResult.failure(
                step.id, domain, "No URL provided for web request", 0L
            )

            return executeWebScrapingWorkflow(step.id, url, context, startTime)
        }

        return StepResult.failure(step.id, domain, "Cannot handle this type of request", 0L)
    }

    private suspend fun executeWebScrapingWorkflow(
        stepId: String,
        url: String,
        context: OperationContext,
        startTime: Long
    ): StepResult {
        val workflowData = mutableMapOf<String, String>()

        // Step 1: Fetch web content
        logger.d(name, "Step 1: Fetching content from $url")
        val webResult = webFetchTool.execute(mapOf("url" to url))

        if (webResult !is ToolResult.Success) {
            return StepResult.failure(
                stepId, domain, "Failed to fetch content: ${webResult.output}",
                System.currentTimeMillis() - startTime
            )
        }

        workflowData["url"] = url
        workflowData["raw_content_length"] = webResult.output.length.toString()
        
        // Debug logging for WebFetch result
        logger.d(name, "WebFetch result - Length: ${webResult.output.length}, Content preview: ${webResult.output.take(200)}")

        // Step 2: Extract readable content
        logger.d(name, "Step 2: Extracting readable content")
        val extractParams = mapOf("content" to webResult.output)
        logger.d(name, "ExtractorTool params - content length: ${(extractParams["content"] as? String)?.length ?: "null"}")
        val extractResult = extractorTool.execute(extractParams)

        if (extractResult !is ToolResult.Success) {
            return StepResult.failure(
                stepId, domain, "Failed to extract content: ${extractResult.output}",
                System.currentTimeMillis() - startTime
            )
        }

        workflowData["extracted_content_length"] = extractResult.output.length.toString()

        // Step 3: Summarize for user
        logger.d(name, "Step 3: Summarizing content")
        val summaryResult = summarizerTool.execute(mapOf(
            "text" to extractResult.output,
            "max_length" to "800"
        ))

        if (summaryResult !is ToolResult.Success) {
            return StepResult.failure(
                stepId, domain, "Failed to summarize content: ${summaryResult.output}",
                System.currentTimeMillis() - startTime
            )
        }

        workflowData["summary"] = summaryResult.output
        workflowData["workflow_type"] = "web_scraping"
        workflowData["steps_completed"] = "fetch,extract,summarize"

        val executionTime = System.currentTimeMillis() - startTime
        logger.d(name, "Web scraping workflow completed successfully in ${executionTime}ms")

        return StepResult.success(
            stepId,
            domain,
            data = workflowData,
            executionTimeMs = executionTime
        )
    }

    private suspend fun fetchWebContent(step: OperationStep, context: OperationContext): StepResult {
        val startTime = System.currentTimeMillis()
        val url = step.parameters["url"] ?: return StepResult.failure(
            step.id, domain, "No URL provided", 0L
        )

        val result = webFetchTool.execute(mapOf("url" to url))
        val executionTime = System.currentTimeMillis() - startTime

        return if (result is ToolResult.Success) {
            StepResult.success(
                step.id, domain,
                data = mapOf("content" to result.output, "url" to url),
                               executionTimeMs = executionTime
            )
        } else {
            StepResult.failure(step.id, domain, result.output, executionTime)
        }
    }

    private suspend fun extractContent(step: OperationStep, context: OperationContext): StepResult {
        val startTime = System.currentTimeMillis()
        val content = step.parameters["content"] ?: return StepResult.failure(
            step.id, domain, "No content provided", 0L
        )

        val result = extractorTool.execute(mapOf("content" to content))
        val executionTime = System.currentTimeMillis() - startTime

        return if (result is ToolResult.Success) {
            StepResult.success(
                step.id, domain,
                data = mapOf("extracted_content" to result.output),
                               executionTimeMs = executionTime
            )
        } else {
            StepResult.failure(step.id, domain, result.output, executionTime)
        }
    }

    private suspend fun summarizeContent(step: OperationStep, context: OperationContext): StepResult {
        val startTime = System.currentTimeMillis()
        val content = step.parameters["content"] ?: return StepResult.failure(
            step.id, domain, "No content provided", 0L
        )

        val result = summarizerTool.execute(mapOf(
            "text" to content,
            "max_length" to (step.parameters["max_length"] ?: "500")
        ))
        val executionTime = System.currentTimeMillis() - startTime

        return if (result is ToolResult.Success) {
            StepResult.success(
                step.id, domain,
                data = mapOf("summary" to result.output),
                               executionTimeMs = executionTime
            )
        } else {
            StepResult.failure(step.id, domain, result.output, executionTime)
        }
    }
}
