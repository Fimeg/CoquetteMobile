package com.yourname.coquettemobile.core.orchestration.routers

import com.yourname.coquettemobile.core.logging.CoquetteLogger
import com.yourname.coquettemobile.core.orchestration.*
import com.yourname.coquettemobile.core.tools.*
import javax.inject.Inject
import javax.inject.Singleton

/**
 * AndroidIntelRouter - Specialized router for information gathering and reconnaissance
 * Domain expert in environment discovery, target analysis, and capability assessment
 */
@Singleton
class AndroidIntelRouter @Inject constructor(
    private val deviceContextTool: DeviceContextTool,
    private val fileTool: FileTool,
    private val grepTool: GrepTool,
    private val globTool: GlobTool,
    private val ollamaService: com.yourname.coquettemobile.core.ai.OllamaService,
    private val appPreferences: com.yourname.coquettemobile.core.preferences.AppPreferences,
    private val logger: CoquetteLogger
) : ToolRouter {
    
    override val domain = RouterDomain.ANDROID_INTELLIGENCE
    override val name = "AndroidIntelRouter"
    
    override val managedTools: List<MobileTool> = listOf(
        deviceContextTool,
        fileTool,
        grepTool,
        globTool
    )
    
    override val capabilities = listOf(
        "device information", "system analysis", "local file operations",
        "android diagnostics", "storage analysis", "app management", 
        "network configuration", "device settings", "performance monitoring",
        "battery diagnostics", "local security scanning", "permission analysis"
    )
    
    override val priority = 80 // High priority for intelligence gathering
    
    override suspend fun canHandle(operation: OperationStep): Boolean {
        return when (operation.type) {
            StepType.ENVIRONMENT_DISCOVERY,
            StepType.TARGET_ANALYSIS,
            StepType.CAPABILITY_ASSESSMENT,
            StepType.VULNERABILITY_SCAN,
            StepType.FILE_ENUMERATION,
            StepType.DIRECTORY_ANALYSIS -> true
            else -> operation.domain == domain
        }
    }
    
    override suspend fun executeStep(step: OperationStep, context: OperationContext): StepResult {
        val startTime = System.currentTimeMillis()
        logger.d(name, "Executing ${step.type}: ${step.description}")
        
        return try {
            when (step.type) {
                StepType.ENVIRONMENT_DISCOVERY -> discoverEnvironment(step, context)
                StepType.TARGET_ANALYSIS -> analyzeTarget(step, context)
                StepType.CAPABILITY_ASSESSMENT -> assessCapabilities(step, context)
                StepType.VULNERABILITY_SCAN -> scanVulnerabilities(step, context)
                StepType.FILE_ENUMERATION -> enumerateFiles(step, context)
                StepType.DIRECTORY_ANALYSIS -> analyzeDirectories(step, context)
                // Web operations belong to WebScraperRouter domain
                else -> StepResult.unsupported(step.id, domain)
            }
        } catch (e: Exception) {
            val executionTime = System.currentTimeMillis() - startTime
            logger.e(name, "Step execution failed: ${e.message}")
            
            StepResult.failure(
                step.id,
                domain,
                "AndroidIntel execution error: ${e.message}",
                executionTime
            )
        }
    }
    
    override suspend fun planSubSteps(goal: String, context: OperationContext): List<OperationStep> {
        val subSteps = mutableListOf<OperationStep>()
        val goalLower = goal.lowercase()
        
        // Always start with environment discovery
        subSteps.add(
            OperationStep(
                id = "intel_env_discovery",
                type = StepType.ENVIRONMENT_DISCOVERY,
                domain = domain,
                description = "Discover system environment and basic information",
                estimatedDurationMs = 30000L
            )
        )
        
        // Use AI to determine specific analysis steps needed
        val additionalSteps = generatePlanWithAI(goal, context)
        subSteps.addAll(additionalSteps)
        
        // Always end with capability assessment
        subSteps.add(
            OperationStep(
                id = "intel_capability_assessment",
                type = StepType.CAPABILITY_ASSESSMENT,
                domain = domain,
                description = "Assess available capabilities for further operations",
                dependencies = subSteps.map { it.id },
                estimatedDurationMs = 20000L
            )
        )
        
        return subSteps
    }
    
    override suspend fun validateCapabilities(operation: OperationStep): ValidationResult {
        val requirements = mutableListOf<String>()
        
        when (operation.type) {
            StepType.ENVIRONMENT_DISCOVERY -> {
                if (!hasDeviceContextCapability()) {
                    requirements.add("Device context access required")
                }
            }
            StepType.FILE_ENUMERATION, StepType.DIRECTORY_ANALYSIS -> {
                if (!hasFileSystemCapability()) {
                    requirements.add("File system access required")
                }
            }
            StepType.VULNERABILITY_SCAN -> {
                if (!hasSecurityAnalysisCapability()) {
                    requirements.add("Security analysis tools required")
                }
            }
            else -> { /* No specific requirements */ }
        }
        
        return if (requirements.isEmpty()) {
            ValidationResult.valid()
        } else {
            ValidationResult.invalid(requirements)
        }
    }
    
    override suspend fun getInsights(context: OperationContext): RouterInsights {
        return RouterInsights(
            domain = domain,
            availableCapabilities = capabilities,
            currentConstraints = getCurrentConstraints(context),
            recommendedApproach = getRecommendedApproach(context),
            riskAssessment = com.yourname.coquettemobile.core.tools.RiskLevel.LOW // Intelligence gathering is typically low risk
        )
    }
    
    // Implementation methods
    
    private suspend fun discoverEnvironment(step: OperationStep, context: OperationContext): StepResult {
        val startTime = System.currentTimeMillis()
        val discoveredData = mutableMapOf<String, Any>()
        
        // Get device context information
        val deviceResult = deviceContextTool.execute(mapOf("action" to "system"))
        if (deviceResult is ToolResult.Success) {
            discoveredData["device_info"] = deviceResult.output
        }
        
        // Get battery information
        val batteryResult = deviceContextTool.execute(mapOf("action" to "battery"))
        if (batteryResult is ToolResult.Success) {
            discoveredData["battery_info"] = batteryResult.output
        }
        
        // Get network information
        val networkResult = deviceContextTool.execute(mapOf("action" to "network"))
        if (networkResult is ToolResult.Success) {
            discoveredData["network_info"] = networkResult.output
        }
        
        val executionTime = System.currentTimeMillis() - startTime
        
        return StepResult.success(
            step.id,
            domain,
            data = discoveredData.mapValues { it.value.toString() },
            executionTimeMs = executionTime
        )
    }
    
    private suspend fun analyzeTarget(step: OperationStep, context: OperationContext): StepResult {
        val startTime = System.currentTimeMillis()
        val analysisData = mutableMapOf<String, Any>()
        
        // Analyze previous environment discovery results
        val envResults = context.getResultsFromDomain(domain)
            .firstOrNull { it.stepId.contains("env_discovery") }
        
        if (envResults != null) {
            analysisData["environment_analysis"] = analyzeEnvironmentData(envResults.data)
        }
        
        // Add target-specific analysis based on parameters
        val targetType = step.parameters["target_type"] as? String
        if (targetType != null) {
            analysisData["target_specific_analysis"] = analyzeTargetType(targetType)
        }
        
        val executionTime = System.currentTimeMillis() - startTime
        
        return StepResult.success(
            step.id,
            domain,
            data = analysisData.mapValues { it.value.toString() },
            executionTimeMs = executionTime
        )
    }
    
    private suspend fun assessCapabilities(step: OperationStep, context: OperationContext): StepResult {
        val startTime = System.currentTimeMillis()
        
        val capabilities = mutableMapOf<String, Any>()
        capabilities["available_tools"] = managedTools.map { it.name }
        capabilities["intelligence_capabilities"] = this.capabilities
        capabilities["operational_readiness"] = assessOperationalReadiness(context)
        
        val executionTime = System.currentTimeMillis() - startTime
        
        return StepResult.success(
            step.id,
            domain,
            data = capabilities.mapValues { it.value.toString() },
            executionTimeMs = executionTime
        )
    }
    
    private suspend fun scanVulnerabilities(step: OperationStep, context: OperationContext): StepResult {
        val startTime = System.currentTimeMillis()
        
        // This is a placeholder - actual vulnerability scanning would be more complex
        val vulnerabilities = mutableMapOf<String, Any>()
        vulnerabilities["scan_type"] = "basic_assessment"
        vulnerabilities["findings"] = "Vulnerability scanning capabilities limited in current implementation"
        vulnerabilities["recommendations"] = listOf(
            "Implement network vulnerability scanner",
            "Add system configuration analyzer",
            "Include security baseline checker"
        )
        
        val executionTime = System.currentTimeMillis() - startTime
        
        return StepResult.success(
            step.id,
            domain,
            data = vulnerabilities.mapValues { it.value.toString() },
            executionTimeMs = executionTime
        )
    }
    
    private suspend fun enumerateFiles(step: OperationStep, context: OperationContext): StepResult {
        val startTime = System.currentTimeMillis()
        val fileData = mutableMapOf<String, Any>()
        
        val searchPath = step.parameters["path"] as? String ?: "/storage/emulated/0"
        val pattern = step.parameters["pattern"] as? String ?: "*"
        
        // Use GlobTool to find files matching pattern
        val globResult = globTool.execute(mapOf(
            "pattern" to pattern,
            "path" to searchPath
        ))
        
        if (globResult is ToolResult.Success) {
            fileData["enumerated_files"] = globResult.output
        }
        
        val executionTime = System.currentTimeMillis() - startTime
        
        return StepResult.success(
            step.id,
            domain,
            data = fileData.mapValues { it.value.toString() },
            executionTimeMs = executionTime
        )
    }
    
    private suspend fun analyzeDirectories(step: OperationStep, context: OperationContext): StepResult {
        val startTime = System.currentTimeMillis()
        val dirData = mutableMapOf<String, Any>()
        
        // Analyze directory structure and contents
        dirData["analysis_type"] = "directory_structure"
        dirData["analyzed_paths"] = step.parameters["paths"] ?: listOf("/storage/emulated/0")
        
        val executionTime = System.currentTimeMillis() - startTime
        
        return StepResult.success(
            step.id,
            domain,
            data = dirData.mapValues { it.value.toString() },
            executionTimeMs = executionTime
        )
    }
    
    // Web content fetching removed - belongs to WebScraperRouter domain
    
    // Content extraction removed - belongs to WebScraperRouter domain
    
    // Data summarization removed - belongs to WebScraperRouter domain
    
    // Helper methods
    
    private fun hasDeviceContextCapability(): Boolean = true // DeviceContextTool is always available
    
    private fun hasFileSystemCapability(): Boolean = true // FileTool, GrepTool, GlobTool available
    
    private fun hasSecurityAnalysisCapability(): Boolean = false // Not fully implemented yet
    
    private fun getCurrentConstraints(context: OperationContext): List<String> {
        val constraints = mutableListOf<String>()
        
        if (context.securityLevel == SecurityLevel.MINIMAL) {
            constraints.add("Limited to basic system information only")
        }
        
        return constraints
    }
    
    private fun getRecommendedApproach(context: OperationContext): String {
        return "Systematic intelligence gathering: environment → analysis → capabilities"
    }
    
    private fun analyzeEnvironmentData(envData: Map<String, Any>): Map<String, Any> {
        val analysis = mutableMapOf<String, Any>()
        
        analysis["data_quality"] = "good"
        analysis["completeness"] = envData.size
        analysis["key_findings"] = "Environment data collected successfully"
        
        return analysis
    }
    
    private fun analyzeTargetType(targetType: String): Map<String, Any> {
        return mapOf(
            "target_type" to targetType,
            "analysis_approach" to "generic_analysis",
            "confidence" to "medium"
        )
    }
    
    private fun assessOperationalReadiness(context: OperationContext): String {
        return when {
            context.securityLevel == SecurityLevel.MAXIMUM -> "fully_operational"
            context.securityLevel == SecurityLevel.ELEVATED -> "elevated_operational"
            context.securityLevel == SecurityLevel.STANDARD -> "standard_operational"
            else -> "limited_operational"
        }
    }
    
    private suspend fun generatePlanWithAI(goal: String, context: OperationContext): List<OperationStep> {
        val planningPrompt = buildPlanningPrompt(goal, context)
        val model = appPreferences.orchestratorModel ?: "deepseek-r1:1.5b"
        
        return try {
            val response = ollamaService.sendMessage(
                message = planningPrompt,
                model = model,
                systemPrompt = "You are an Android device analysis specialist. Create step-by-step plans for local device operations only."
            )
            
            parseStepsFromResponse(response.content)
        } catch (e: Exception) {
            logger.e(name, "AI planning failed: ${e.message}")
            emptyList()
        }
    }
    
    private fun buildPlanningPrompt(goal: String, context: OperationContext): String {
        return """
        Goal: $goal
        
        Available Tools: device info, file operations, grep/glob search, local analysis
        Security Level: ${context.securityLevel}
        
        Create specific steps for LOCAL ANDROID DEVICE analysis only. No web operations.
        Focus on: device diagnostics, file system analysis, app information, system configuration.
        """.trimIndent()
    }
    
    private fun parseStepsFromResponse(response: String): List<OperationStep> {
        // Simple parsing - extract step descriptions from AI response
        val stepPattern = """(?:^\d+\.|Step \d+:|\n-)\s*(.+)""".toRegex(RegexOption.MULTILINE)
        val matches = stepPattern.findAll(response)
        
        return matches.mapIndexed { index, match ->
            OperationStep(
                id = "ai_step_${index + 1}",
                type = StepType.TARGET_ANALYSIS,
                domain = domain,
                description = match.groupValues[1].trim(),
                estimatedDurationMs = 30000L
            )
        }.toList()
    }
}