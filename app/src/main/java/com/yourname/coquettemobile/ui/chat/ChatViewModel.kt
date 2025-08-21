package com.yourname.coquettemobile.ui.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.yourname.coquettemobile.core.ai.IntelligenceRouter
import com.yourname.coquettemobile.core.ai.OllamaService
import com.yourname.coquettemobile.core.ai.PersonalityProvider
import com.yourname.coquettemobile.core.ai.SubconsciousReasoner
import com.yourname.coquettemobile.core.ai.PlannerFollowup
import com.yourname.coquettemobile.core.models.ChatMessage
import com.yourname.coquettemobile.core.models.MessageType
import com.yourname.coquettemobile.core.database.entities.Personality
import com.yourname.coquettemobile.core.database.entities.Conversation
import com.yourname.coquettemobile.core.repository.PersonalityRepository
import com.yourname.coquettemobile.core.repository.ConversationRepository
import com.yourname.coquettemobile.core.preferences.AppPreferences
import com.yourname.coquettemobile.core.tools.MobileToolsAgent
import com.yourname.coquettemobile.core.service.CoquetteBackgroundService
import com.yourname.coquettemobile.core.prompt.PromptStateManager
import com.yourname.coquettemobile.core.prompt.ModuleRegistry
import com.yourname.coquettemobile.core.prompt.SystemPromptManager
import com.yourname.coquettemobile.core.ai.PlannerService
import com.yourname.coquettemobile.core.ai.PlannerDecision
import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.collect
import javax.inject.Inject
import java.util.UUID

@HiltViewModel
class ChatViewModel @Inject constructor(
    private val ollamaService: OllamaService,
    private val intelligenceRouter: IntelligenceRouter,
    private val subconsciousReasoner: SubconsciousReasoner,
    private val personalityProvider: PersonalityProvider,
    private val personalityRepository: PersonalityRepository,
    private val conversationRepository: ConversationRepository,
    private val appPreferences: AppPreferences,
    private val mobileToolsAgent: MobileToolsAgent,
    private val promptStateManager: PromptStateManager,
    private val moduleRegistry: ModuleRegistry,
    private val plannerService: PlannerService,
    private val systemPromptManager: SystemPromptManager
) : ViewModel() {

    private val _chatMessages = MutableStateFlow<List<ChatMessage>>(emptyList())
    val chatMessages: StateFlow<List<ChatMessage>> = _chatMessages.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()
    
    private val _processingState = MutableStateFlow<ProcessingState>(ProcessingState.Idle)
    val processingState: StateFlow<ProcessingState> = _processingState.asStateFlow()
    
    sealed class ProcessingState {
        object Idle : ProcessingState()
        object Scout : ProcessingState()  // Scout is planning/routing
        object Thinking : ProcessingState()  // AI model is generating response
        data class Tools(val toolName: String) : ProcessingState()  // Specific tool being executed
    }

    private val _availablePersonalities = MutableStateFlow<List<Personality>>(emptyList())
    val availablePersonalities: StateFlow<List<Personality>> = _availablePersonalities.asStateFlow()
    
    private val _selectedPersonality = MutableStateFlow<Personality?>(null)
    val selectedPersonality: StateFlow<Personality?> = _selectedPersonality.asStateFlow()
    
    private val _availableModels = MutableStateFlow<List<String>>(emptyList())
    val availableModels: StateFlow<List<String>> = _availableModels.asStateFlow()
    
    private val _availablePlannerModels = MutableStateFlow<List<String>>(emptyList())
    val availablePlannerModels: StateFlow<List<String>> = _availablePlannerModels.asStateFlow()
    
    private val _selectedModel = MutableStateFlow("auto")
    val selectedModel: StateFlow<String> = _selectedModel.asStateFlow()
    
    private val _connectionStatus = MutableStateFlow("Disconnected")
    val connectionStatus: StateFlow<String> = _connectionStatus.asStateFlow()
    
    private val _isStreamingEnabled = MutableStateFlow(appPreferences.isStreamingEnabled)
    val isStreamingEnabled: StateFlow<Boolean> = _isStreamingEnabled.asStateFlow()
    
    private val _currentStreamingMessage = MutableStateFlow<ChatMessage?>(null)
    val currentStreamingMessage: StateFlow<ChatMessage?> = _currentStreamingMessage.asStateFlow()
    
    private val _currentConversation = MutableStateFlow<Conversation?>(null)
    val currentConversation: StateFlow<Conversation?> = _currentConversation.asStateFlow()
    
    init {
        loadAvailableModels()
        initializePersonalities()
        initializeConversation()
        initializeSplitBrain()
    }
    
    private fun initializeSplitBrain() {
        viewModelScope.launch {
            // Initialize module registry
            moduleRegistry.initialize()
            
            // Force refresh of tool awareness from SystemPromptManager
            moduleRegistry.register("ToolAwareness", systemPromptManager.toolAwarenessPrompt)
            
            // Initialize prompt state manager with user-configurable prompts
            val coreIdentity = systemPromptManager.corePersonalityPrompt
            val plannerSystemPrompt = systemPromptManager.plannerSystemPrompt
            promptStateManager.initialize(coreIdentity, plannerSystemPrompt)
        }
    }

    fun sendMessage(message: String) {
        if (message.isBlank()) return

        val userMessage = ChatMessage(
            id = UUID.randomUUID().toString(),
            content = message,
            type = MessageType.USER,
            timestamp = System.currentTimeMillis()
        )

        _chatMessages.value = _chatMessages.value + userMessage
        _isLoading.value = true
        _processingState.value = ProcessingState.Scout

        viewModelScope.launch {
            try {
                // Ensure we have an active conversation
                val conversation = _currentConversation.value ?: createNewConversation()
                
                // Save user message to database
                conversationRepository.saveMessage(userMessage, conversation.id)
                
                // Use selected model or route automatically (only if routing is enabled)
                val modelToUse = if (_selectedModel.value != "auto") {
                    _selectedModel.value
                } else if (appPreferences.enableModelRouting) {
                    intelligenceRouter.selectModelForQuery(message)
                } else {
                    "qwen3:8b" // Default model when routing is disabled
                }
                
                // Check if split-brain mode is enabled
                if (appPreferences.enableSplitBrain) {
                    handleSplitBrainFlow(message, conversation.id)
                } else {
                    handleLegacyFlow(message, conversation.id)
                }
                
                // Generate title if this is early in the conversation
                if (_chatMessages.value.size <= 4) {
                    conversationRepository.generateAndUpdateTitle(conversation.id)
                }
                
            } catch (e: Exception) {
                val errorMessage = ChatMessage(
                    id = UUID.randomUUID().toString(),
                    content = "Error: ${e.message ?: "Failed to get response"}",
                    type = MessageType.ERROR,
                    timestamp = System.currentTimeMillis()
                )
                _chatMessages.value = _chatMessages.value + errorMessage
                _isLoading.value = false
                _processingState.value = ProcessingState.Idle
            }
        }
    }
    
    private suspend fun handleStreamingResponse(message: String, modelToUse: String, systemPrompt: String, conversationId: String) {
        val aiMessageId = UUID.randomUUID().toString()
        val aiMessage = ChatMessage(
            id = aiMessageId,
            content = "",
            type = MessageType.AI,
            timestamp = System.currentTimeMillis(),
            modelUsed = modelToUse
        )
        
        // Add empty AI message to show streaming
        _chatMessages.value = _chatMessages.value + aiMessage
        _currentStreamingMessage.value = aiMessage
        
        val fullContentBuilder = StringBuilder()
        
        try {
            ollamaService.sendMessageStream(
                message = message,
                model = modelToUse,
                systemPrompt = systemPrompt
            ).collect { chunk ->
                fullContentBuilder.append(chunk)
                val currentText = fullContentBuilder.toString()
                
                // Parse thinking content in real-time for streaming display
                val thinkingRegex = """<think>(.*?)(?:</think>|$)""".toRegex(RegexOption.DOT_MATCHES_ALL)
                val thinkingMatch = thinkingRegex.find(currentText)
                val thinkingContent = thinkingMatch?.groupValues?.get(1)
                
                // Extract response content (everything outside think tags and after last </think>)
                val responseContent = currentText
                    .replace("""<think>.*?</think>""".toRegex(RegexOption.DOT_MATCHES_ALL), "")
                    .replace("""<think>.*""".toRegex(RegexOption.DOT_MATCHES_ALL), "")
                    .trim()
                
                // Hide processing indicator when thinking content is detected or response starts
                if ((thinkingContent != null || responseContent.isNotBlank()) && 
                    _processingState.value != ProcessingState.Idle) {
                    _processingState.value = ProcessingState.Idle
                }
                
                // Update the streaming message
                val updatedMessage = aiMessage.copy(
                    content = responseContent,
                    thinkingContent = thinkingContent?.takeIf { it.isNotBlank() }
                )
                _currentStreamingMessage.value = updatedMessage
                
                // Update in chat messages list
                _chatMessages.value = _chatMessages.value.map { msg ->
                    if (msg.id == aiMessageId) updatedMessage else msg
                }
            }
            
            // Parse thinking tags after streaming completes for final cleanup
            val parsedResponse = com.yourname.coquettemobile.utils.ThinkingParser.parseThinkingTags(fullContentBuilder.toString())
            val finalMessage = aiMessage.copy(
                content = parsedResponse.content,
                thinkingContent = parsedResponse.thinkingContent
            )
            
            _chatMessages.value = _chatMessages.value.map { msg ->
                if (msg.id == aiMessageId) finalMessage else msg
            }
            
            // Save AI message to database
            conversationRepository.saveMessage(finalMessage, conversationId)
            
        } finally {
            _currentStreamingMessage.value = null
            _isLoading.value = false
            _processingState.value = ProcessingState.Idle
        }
    }
    
    private suspend fun handleNormalResponse(message: String, modelToUse: String, systemPrompt: String, conversationId: String) {
        val parsedResponse = ollamaService.sendMessage(
            message = message,
            model = modelToUse,
            systemPrompt = systemPrompt
        )

        // Apply subconscious reasoning for complex queries (only if enabled)
        val finalContent = if (appPreferences.enableSubconsciousReasoning && 
                              intelligenceRouter.requiresDeepReasoning(message)) {
            subconsciousReasoner.analyzeAndRefine(
                query = message,
                initialResponse = parsedResponse.content,
                modelToUse = modelToUse
            )
        } else {
            parsedResponse.content
        }

        val aiMessage = ChatMessage(
            id = UUID.randomUUID().toString(),
            content = finalContent,
            type = MessageType.AI,
            timestamp = System.currentTimeMillis(),
            modelUsed = modelToUse,
            thinkingContent = parsedResponse.thinkingContent
        )

        _chatMessages.value = _chatMessages.value + aiMessage
        _isLoading.value = false
        
        // Save AI message to database
        conversationRepository.saveMessage(aiMessage, conversationId)
    }
    
    private suspend fun buildConversationContext(conversationId: String): String {
        val recentMessages = conversationRepository.getRecentMessages(conversationId, 10)
        return recentMessages.reversed().joinToString("\n") { message ->
            when (message.type) {
                MessageType.USER -> "User: ${message.content}"
                MessageType.AI -> "Assistant: ${message.content}"
                else -> ""
            }
        }.trim()
    }

    fun updatePersonality(personality: Personality) {
        _selectedPersonality.value = personality
    }
    
    fun updateSelectedModel(model: String) {
        _selectedModel.value = model
    }
    
    fun loadAvailableModels() {
        viewModelScope.launch {
            try {
                _connectionStatus.value = "Connecting..."
                
                // Load main server models
                val models = ollamaService.getAvailableModels()
                _availableModels.value = listOf("auto") + models
                
                // Load planner models (from tool server if enabled)
                android.util.Log.d("ChatViewModel", "Initial load: tool server enabled = ${appPreferences.enableToolOllamaServer}")
                val plannerModels = if (appPreferences.enableToolOllamaServer) {
                    android.util.Log.d("ChatViewModel", "Initial load from tool server")
                    ollamaService.getAvailableModels(useToolServer = true)
                } else {
                    android.util.Log.d("ChatViewModel", "Initial load from main server")
                    models // Use same models if tool server disabled
                }
                android.util.Log.d("ChatViewModel", "Initial planner models: $plannerModels")
                _availablePlannerModels.value = emptyList() // Force update
                
                // If tool server returns empty models, provide fallbacks
                val finalPlannerModels = if (plannerModels.isEmpty() && appPreferences.enableToolOllamaServer) {
                    listOf("hf.co/unsloth/gemma-3-270m-it-GGUF:F16", "gemma3n:e4b", "deepseek-r1:1.5b") // Tool server fallbacks
                } else if (plannerModels.isEmpty()) {
                    models // Use main server models if tool server disabled
                } else {
                    plannerModels // Use actual tool server models
                }
                
                _availablePlannerModels.value = finalPlannerModels
                
                _connectionStatus.value = if (models.isNotEmpty()) "Connected" else "No models found"
            } catch (e: Exception) {
                _connectionStatus.value = "Connection failed"
                _availableModels.value = listOf("auto")
                // Always include a fallback model for tool server
                _availablePlannerModels.value = listOf("hf.co/unsloth/gemma-3-270m-it-GGUF:F16", "gemma3n:e4b", "deepseek-r1:1.5b") // Fallback models
            }
        }
    }
    
    fun refreshPlannerModels() {
        viewModelScope.launch {
            try {
                android.util.Log.d("ChatViewModel", "Refreshing planner models, tool server enabled: ${appPreferences.enableToolOllamaServer}")
                val plannerModels = if (appPreferences.enableToolOllamaServer) {
                    android.util.Log.d("ChatViewModel", "Loading planner models from tool server")
                    ollamaService.getAvailableModels(useToolServer = true)
                } else {
                    android.util.Log.d("ChatViewModel", "Loading planner models from main server")
                    ollamaService.getAvailableModels(useToolServer = false)
                }
                android.util.Log.d("ChatViewModel", "Planner models loaded: $plannerModels")
                _availablePlannerModels.value = emptyList() // Force update
                _availablePlannerModels.value = plannerModels
            } catch (e: Exception) {
                android.util.Log.e("ChatViewModel", "Failed to load planner models: ${e.message}")
                // Always include a fallback model for tool server
                _availablePlannerModels.value = listOf("hf.co/unsloth/gemma-3-270m-it-GGUF:F16", "gemma3n:e4b", "deepseek-r1:1.5b") // Fallback models
            }
        }
    }
    
    private fun initializePersonalities() {
        viewModelScope.launch {
            try {
                // Seed default personalities if needed
                personalityRepository.seedDefaultPersonalities()
                
                // Load personalities
                personalityRepository.getAllPersonalities().collect { personalities ->
                    _availablePersonalities.value = personalities
                    
                    // Set default personality if none selected
                    if (_selectedPersonality.value == null) {
                        val defaultPersonality = personalityRepository.getDefaultPersonality()
                            ?: personalities.firstOrNull()
                        _selectedPersonality.value = defaultPersonality
                    }
                }
            } catch (e: Exception) {
                // Handle error - maybe show in UI
            }
        }
    }

    fun clearChat() {
        _chatMessages.value = emptyList()
        _currentStreamingMessage.value = null
        viewModelScope.launch {
            createNewConversation()
        }
    }
    
    private fun detectComplexTask(message: String): Boolean {
        val complexKeywords = listOf(
            "compare", "research", "analyze", "comprehensive", "detailed analysis",
            "multiple sources", "in-depth", "thorough investigation", "report",
            "study", "examine", "investigate", "compile", "synthesize",
            "check", "look up", "find out", "get", "fetch", "latest", "news"
        )
        
        val wordCount = message.split(" ").size
        val hasComplexKeywords = complexKeywords.any { keyword ->
            message.contains(keyword, ignoreCase = true)
        }
        
        // More sensitive detection - enable subconscious reasoning for web-related requests
        val webRelatedKeywords = listOf("http", "www", ".com", ".org", "website", "site", "url")
        val hasWebKeywords = webRelatedKeywords.any { keyword ->
            message.contains(keyword, ignoreCase = true)
        }
        
        return hasComplexKeywords || wordCount > 10 || hasWebKeywords
    }
    
    private suspend fun buildReasoningContext(conversationId: String, userRequest: String): com.yourname.coquettemobile.core.models.ReasoningContext {
        val conversationHistory = buildConversationHistoryForModel(conversationId)
        return com.yourname.coquettemobile.core.models.ReasoningContext(
            userRequest = userRequest,
            conversationHistory = conversationHistory,
            availableModels = _availableModels.value,
            currentPersonality = _selectedPersonality.value?.name ?: "Default"
        )
    }
    
    private fun convertTaskPlanToDecision(taskPlan: com.yourname.coquettemobile.core.ai.TaskPlan, originalMessage: String): PlannerDecision {
        if (taskPlan.steps.isEmpty()) {
            return PlannerDecision(
                decision = "respond",
                reason = "No actionable steps in task plan"
            )
        }
        
        val firstStep = taskPlan.steps.first()
        val remainingSteps = taskPlan.steps.drop(1)
        
        // Convert first step to primary tool execution
        val primaryTool = firstStep.toolsRequired.firstOrNull() ?: return PlannerDecision(
            decision = "respond",
            reason = "No tools specified in first step"
        )
        
        // Build followup plan from remaining steps
        val followupPlan = remainingSteps.mapNotNull { step ->
            step.toolsRequired.firstOrNull()?.let { toolName ->
                PlannerFollowup(
                    expect = step.expectedOutput,
                    nextTool = toolName
                )
            }
        }
        
        // Determine args based on tool and step description
        val args = when (primaryTool) {
            "WebFetchTool" -> {
                // Extract URL from step description or use a default
                val urlRegex = """https?://[^\s]+""".toRegex()
                val url = urlRegex.find(firstStep.description)?.value 
                    ?: extractImpliedUrl(firstStep.description, originalMessage)
                mapOf("url" to url)
            }
            "DeviceContextTool" -> mapOf<String, Any>()
            else -> mapOf<String, Any>()
        }
        
        return PlannerDecision(
            decision = "tool",
            reason = "Complex task: ${taskPlan.reasoning}",
            tool = primaryTool,
            action = "execute",
            args = args,
            followupPlan = followupPlan
        )
    }
    
    private fun extractImpliedUrl(stepDescription: String, originalMessage: String): String {
        // Try to extract or infer URLs from the description or original message
        val urlRegex = """https?://[^\s]+""".toRegex()
        
        // First check original message
        urlRegex.find(originalMessage)?.let { return it.value }
        
        // Then check step description
        urlRegex.find(stepDescription)?.let { return it.value }
        
        // Fallback to common sites based on keywords
        return when {
            stepDescription.contains("news", ignoreCase = true) -> "https://news.google.com/"
            stepDescription.contains("tech", ignoreCase = true) -> "https://techcrunch.com/"
            stepDescription.contains("AI", ignoreCase = true) -> "https://openai.com/blog/"
            stepDescription.contains("research", ignoreCase = true) -> "https://arxiv.org/"
            else -> "https://www.google.com/search?q=${originalMessage.replace(" ", "+")}"
        }
    }
    
    private suspend fun createNewConversation(): Conversation {
        val conversation = conversationRepository.createNewConversation()
        _currentConversation.value = conversation
        return conversation
    }
    
    fun switchToConversation(conversationId: String) {
        viewModelScope.launch {
            val conversation = conversationRepository.switchToConversation(conversationId)
            _currentConversation.value = conversation
            
            // Load messages for this conversation
            conversation?.let {
                // Clear current messages first
                _chatMessages.value = emptyList()
                // Load messages from database
                val messages = conversationRepository.getRecentMessages(it.id, 100) // Get more messages for history
                _chatMessages.value = messages
            }
        }
    }
    
    private fun initializeConversation() {
        viewModelScope.launch {
            val activeConversation = conversationRepository.getActiveConversation()
            if (activeConversation != null) {
                // Load messages for active conversation
                val messages = conversationRepository.getRecentMessages(activeConversation.id, 100)
                
                if (messages.isNotEmpty()) {
                    // Previous conversation has messages - create a fresh chat
                    createNewConversation()
                } else {
                    // Previous conversation is empty - reuse it
                    _currentConversation.value = activeConversation
                    _chatMessages.value = messages
                }
            } else {
                // Create new conversation if none exists
                createNewConversation()
            }
        }
    }
    
    fun toggleStreaming(enabled: Boolean) {
        _isStreamingEnabled.value = enabled
        appPreferences.isStreamingEnabled = enabled
    }
    
    /**
     * Detect and execute tools if the message contains tool requests
     */
    private suspend fun detectAndExecuteTools(message: String): com.yourname.coquettemobile.core.tools.ToolExecutionResult? {
        val lowerMessage = message.lowercase()
        
        // Simple keyword detection for tool requests
        val toolKeywords = listOf(
            "device", "battery", "system", "status", "info", "storage", "memory",
            "network", "wifi", "performance", "specs", "diagnostics"
        )
        
        val containsToolKeywords = toolKeywords.any { keyword ->
            lowerMessage.contains(keyword)
        }
        
        if (!containsToolKeywords) {
            return null
        }
        
        return try {
            mobileToolsAgent.executeTools(message)
        } catch (e: Exception) {
            null
        }
    }

    // --- Split-Brain Flow ---
    
    private suspend fun handleSplitBrainFlow(message: String, conversationId: String) {
        android.util.Log.d("SplitBrainFlow", "=== STARTING SPLIT-BRAIN FLOW ===")
        android.util.Log.d("SplitBrainFlow", "User message: '$message'")
        
        try {
            // Step 1: Check if we need subconscious reasoning for complex tasks
            val needsComplexPlanning = detectComplexTask(message)
            android.util.Log.d("SplitBrainFlow", "Complex planning needed: $needsComplexPlanning")
            
            var decision: PlannerDecision
            
            if (needsComplexPlanning && appPreferences.enableSubconsciousReasoning) {
                android.util.Log.d("SplitBrainFlow", "Step 1A: Using SubconsciousReasoner for complex task...")
                _processingState.value = ProcessingState.Scout
                
                val context = buildReasoningContext(conversationId, message)
                val availableTools = listOf("DeviceContextTool", "WebFetchTool", "ExtractorTool", "SummarizerTool")
                
                val taskPlan = subconsciousReasoner.planComplexTask(message, context, availableTools)
                android.util.Log.d("SplitBrainFlow", "Task plan: ${taskPlan.steps.size} steps, complexity: ${taskPlan.estimatedComplexity}")
                
                // Convert TaskPlan to PlannerDecision for execution
                decision = convertTaskPlanToDecision(taskPlan, message)
            } else {
                android.util.Log.d("SplitBrainFlow", "Step 1B: Using standard planner...")
                val conversationSummary = buildConversationSummary(conversationId)
                android.util.Log.d("SplitBrainFlow", "Conversation summary: '$conversationSummary'")
                
                decision = plannerService.planAction(
                    userTurn = message,
                    conversationSummary = conversationSummary,
                    plannerModel = appPreferences.plannerModel
                )
            }
            
            android.util.Log.d("SplitBrainFlow", "Planner decision: '${decision.decision}'")
            android.util.Log.d("SplitBrainFlow", "Planner reasoning: '${decision.reason}'")
            android.util.Log.d("SplitBrainFlow", "Tool: '${decision.tool}'")
            android.util.Log.d("SplitBrainFlow", "Followup plan: ${decision.followupPlan.size} steps")
            
            when (decision.decision) {
                "tool" -> {
                    android.util.Log.d("SplitBrainFlow", "→ Executing tool path")
                    _processingState.value = ProcessingState.Tools("DeviceContextTool")
                    // Update processing state to show specific tool
                    decision.tool?.let { toolName ->
                        // Could emit specific tool execution state here
                        android.util.Log.d("SplitBrainFlow", "Executing tool: $toolName")
                    }
                    handleToolExecution(decision, message, conversationId)
                }
                "respond" -> {
                    android.util.Log.d("SplitBrainFlow", "→ Executing personality response path")
                    _processingState.value = ProcessingState.Thinking
                    handlePersonalityResponse(message, conversationId, decision.reason)
                }
                else -> {
                    android.util.Log.w("SplitBrainFlow", "→ Unknown planner decision: '${decision.decision}'")
                    _processingState.value = ProcessingState.Thinking
                    handlePersonalityResponse(message, conversationId, "Unknown planner decision: ${decision.decision}")
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("SplitBrainFlow", "PLANNER EXCEPTION: ${e.message}", e)
            // Fallback to personality response
            handlePersonalityResponse(message, conversationId, "Planner error: ${e.message}")
        }
        
        android.util.Log.d("SplitBrainFlow", "=== SPLIT-BRAIN FLOW COMPLETE ===")
    }
    
    private suspend fun handleToolExecution(decision: PlannerDecision, message: String, conversationId: String) {
        var currentData: String? = null
        val executedTools = mutableListOf<String>()
        val toolExecutionLog = StringBuilder()
        
        try {
            // Execute primary tool
            _processingState.value = ProcessingState.Tools(decision.tool ?: "Unknown Tool")
            val initialResult = executeSpecificTool(decision.tool, decision.action, decision.args)
            if (initialResult == null) {
                handlePersonalityResponseWithToolContext(message, conversationId, null, "Tool execution failed")
                return
            }
            
            currentData = getToolResultData(initialResult)
            executedTools.add(decision.tool ?: "unknown")
            toolExecutionLog.append("🔧 ${decision.tool}: ${initialResult.summary}\n")
            
            // Execute followup chain if specified
            for (followup in decision.followupPlan) {
                if (followup.nextTool == null || currentData == null) break
                
                // Update UI to show current tool
                _processingState.value = ProcessingState.Tools(followup.nextTool)
                
                val chainedArgs = when (followup.nextTool) {
                    "ExtractorTool" -> mapOf("html" to currentData)
                    "SummarizerTool" -> {
                        // Skip auto-summarization - let personality handle raw content
                        android.util.Log.d("SplitBrainFlow", "Skipping auto-summarization, letting personality handle raw content")
                        break
                    }
                    else -> mapOf("data" to currentData)
                }
                
                val chainResult = executeSpecificTool(followup.nextTool, null, chainedArgs)
                if (chainResult != null) {
                    currentData = getToolResultData(chainResult)
                    executedTools.add(followup.nextTool)
                    toolExecutionLog.append("🔧 ${followup.nextTool}: ${chainResult.summary}\n")
                } else {
                    break // Stop chain on failure
                }
            }
            
            // Create the tool execution context for personality with raw content
            val toolContext = if (currentData != null) {
                """## TOOL EXECUTION CONTEXT

User requested: "$message"

Tools executed successfully: ${executedTools.joinToString(" → ")}

## RAW CONTENT FOR ANALYSIS:
$currentData

You have the raw content above. Please analyze, synthesize, and respond to the user's request using your personality and expertise. Do not just acknowledge that tools were run - actually process and present the information in a natural, helpful way."""
            } else {
                "Tools were executed but no usable content was retrieved."
            }.trimIndent()
            
            // Pass tool context to personality for integrated response
            handlePersonalityResponseWithToolContext(message, conversationId, toolContext, null)
            
        } catch (e: Exception) {
            handlePersonalityResponseWithToolContext(message, conversationId, null, "Tool chain error: ${e.message}")
        }
    }
    
    private fun getToolResultData(result: com.yourname.coquettemobile.core.tools.ToolExecutionResult): String? {
        return result.stepResults.firstOrNull()?.output ?: result.summary
    }
    
    private suspend fun executeSpecificTool(toolName: String?, action: String?, args: Map<String, Any>): com.yourname.coquettemobile.core.tools.ToolExecutionResult? {
        return try {
            when (toolName) {
                "DeviceContextTool" -> {
                    val deviceAction = action ?: args["action"] as? String ?: "all"
                    mobileToolsAgent.executeTools("device $deviceAction")
                }
                "WebFetchTool" -> {
                    val url = args["url"] as? String
                    if (url != null) {
                        val webTool = mobileToolsAgent.toolRegistry.getTool("WebFetchTool")
                        val result = webTool?.execute(args)
                        if (result != null && result.success) {
                            // Create a simplified ToolExecutionResult for chaining
                            com.yourname.coquettemobile.core.tools.ToolExecutionResult(
                                success = true,
                                request = "fetch $url",
                                plan = null,
                                stepResults = listOf(result),
                                summary = "Fetched content from: $url",
                                executionTime = 0
                            )
                        } else null
                    } else null
                }
                "ExtractorTool" -> {
                    val html = args["html"] as? String
                    if (html != null) {
                        val extractorTool = mobileToolsAgent.toolRegistry.getTool("ExtractorTool")
                        val result = extractorTool?.execute(args)
                        if (result != null && result.success) {
                            com.yourname.coquettemobile.core.tools.ToolExecutionResult(
                                success = true,
                                request = "extract html",
                                plan = null,
                                stepResults = listOf(result),
                                summary = "Extracted readable text",
                                executionTime = 0
                            )
                        } else null
                    } else null
                }
                "SummarizerTool" -> {
                    val text = args["text"] as? String
                    if (text != null) {
                        val summarizerTool = mobileToolsAgent.toolRegistry.getTool("SummarizerTool")
                        val result = summarizerTool?.execute(args)
                        if (result != null && result.success) {
                            com.yourname.coquettemobile.core.tools.ToolExecutionResult(
                                success = true,
                                request = "summarize text",
                                plan = null,
                                stepResults = listOf(result),
                                summary = "Created summary",
                                executionTime = 0
                            )
                        } else null
                    } else null
                }
                else -> {
                    // Fallback to existing detection
                    detectAndExecuteTools("Execute tool: $toolName")
                }
            }
        } catch (e: Exception) {
            null
        }
    }
    
    private suspend fun handlePersonalityResponseWithToolContext(
        originalMessage: String,
        conversationId: String,
        toolContext: String? = null,
        error: String? = null
    ) {
        // DEBUG: Check selected personality
        android.util.Log.d("PersonalityDebug", "Selected personality: ${_selectedPersonality.value?.name}")
        android.util.Log.d("PersonalityDebug", "Selected personality ID: ${_selectedPersonality.value?.id}")
        
        // Get the selected personality system prompt and combine with core identity
        val selectedPersonalityPrompt = _selectedPersonality.value?.let { personality ->
            val prompt = personalityProvider.getSystemPrompt(personality.id)
            android.util.Log.d("PersonalityDebug", "Personality prompt length: ${prompt.length}")
            android.util.Log.d("PersonalityDebug", "Personality prompt preview: ${prompt.take(200)}...")
            prompt
        } ?: personalityProvider.getSystemPrompt("default").also {
            android.util.Log.d("PersonalityDebug", "Using default personality, length: ${it.length}")
        }
        
        // Build combined prompt: Core identity + Selected personality
        val coreIdentity = systemPromptManager.corePersonalityPrompt
        android.util.Log.d("PersonalityDebug", "Core identity length: ${coreIdentity.length}")
        
        val combinedSystemPrompt = if (selectedPersonalityPrompt.isNotBlank()) {
            "$coreIdentity\n\n## ACTIVE PERSONALITY\n$selectedPersonalityPrompt"
        } else {
            coreIdentity
        }
        android.util.Log.d("PersonalityDebug", "Combined system prompt length: ${combinedSystemPrompt.length}")
        
        // Add tool context to system prompt
        val toolAwareSystemPrompt = if (toolContext != null) {
            """$combinedSystemPrompt
            
## TOOL EXECUTION CONTEXT
$toolContext

Respond to the user's request naturally, incorporating the tool results as appropriate for your personality. Present the information in a way that feels conversational and helpful."""
        } else if (error != null) {
            """$combinedSystemPrompt
            
## TOOL EXECUTION ERROR
$error

Respond to the user's request, acknowledging that the requested tools couldn't complete successfully."""
        } else {
            combinedSystemPrompt
        }
        
        // Build the system prompt using PromptStateManager with personality override
        val systemPrompt = promptStateManager.buildSystemPrompt(toolAwareSystemPrompt)
        android.util.Log.d("PersonalityDebug", "Final system prompt length: ${systemPrompt.length}")
        android.util.Log.d("PersonalityDebug", "Final system prompt preview: ${systemPrompt.take(500)}...")
        
        // Create a message that includes original user message for proper context
        val contextualMessage = if (toolContext != null || error != null) {
            originalMessage // The personality responds to the original message with tool context
        } else {
            originalMessage
        }
        
        // Use personality model to generate response
        val personalityModel = appPreferences.personalityModel
        
        if (_isStreamingEnabled.value) {
            handlePersonalityStreamingResponse(contextualMessage, systemPrompt, personalityModel, conversationId)
        } else {
            handlePersonalityNormalResponse(contextualMessage, systemPrompt, personalityModel, conversationId)
        }
    }
    
    private suspend fun handlePersonalityResponse(
        message: String, 
        conversationId: String, 
        plannerNote: String? = null,
        hasToolResult: Boolean = false
    ) {
        // Fallback to context-aware method
        handlePersonalityResponseWithToolContext(message, conversationId, null, plannerNote)
    }
    
    private suspend fun handlePersonalityStreamingResponse(message: String, systemPrompt: String, model: String, conversationId: String) {
        val aiMessageId = UUID.randomUUID().toString()
        val aiMessage = ChatMessage(
            id = aiMessageId,
            content = "",
            type = MessageType.AI,
            timestamp = System.currentTimeMillis(),
            modelUsed = model
        )
        
        _chatMessages.value = _chatMessages.value + aiMessage
        _currentStreamingMessage.value = aiMessage
        
        val fullContentBuilder = StringBuilder()
        
        try {
            // Use chat completion with conversation history for personality model
            val conversationHistory = buildConversationHistoryForModel(conversationId)
            
            ollamaService.sendChatMessageStream(
                messages = conversationHistory,
                model = model,
                systemPrompt = systemPrompt // Pass as system prompt for chat completion
            ).collect { chunk ->
                fullContentBuilder.append(chunk)
                val currentText = fullContentBuilder.toString()
                
                // Parse thinking content in real-time
                val thinkingRegex = """<think>(.*?)(?:</think>|$)""".toRegex(RegexOption.DOT_MATCHES_ALL)
                val thinkingMatch = thinkingRegex.find(currentText)
                val thinkingContent = thinkingMatch?.groupValues?.get(1)
                
                val responseContent = currentText
                    .replace("""<think>.*?</think>""".toRegex(RegexOption.DOT_MATCHES_ALL), "")
                    .replace("""<think>.*""".toRegex(RegexOption.DOT_MATCHES_ALL), "")
                    .trim()
                
                // Hide processing indicator when thinking content is detected or response starts
                if ((thinkingContent != null || responseContent.isNotBlank()) && 
                    _processingState.value != ProcessingState.Idle) {
                    _processingState.value = ProcessingState.Idle
                }
                
                val updatedMessage = aiMessage.copy(
                    content = responseContent,
                    thinkingContent = thinkingContent?.takeIf { it.isNotBlank() }
                )
                _currentStreamingMessage.value = updatedMessage
                
                _chatMessages.value = _chatMessages.value.map { msg ->
                    if (msg.id == aiMessageId) updatedMessage else msg
                }
            }
            
            // Final cleanup
            val parsedResponse = com.yourname.coquettemobile.utils.ThinkingParser.parseThinkingTags(fullContentBuilder.toString())
            val finalMessage = aiMessage.copy(
                content = parsedResponse.content,
                thinkingContent = parsedResponse.thinkingContent
            )
            
            _chatMessages.value = _chatMessages.value.map { msg ->
                if (msg.id == aiMessageId) finalMessage else msg
            }
            
            conversationRepository.saveMessage(finalMessage, conversationId)
            
        } finally {
            _currentStreamingMessage.value = null
            _isLoading.value = false
            _processingState.value = ProcessingState.Idle
        }
    }
    
    private suspend fun handlePersonalityNormalResponse(message: String, systemPrompt: String, model: String, conversationId: String) {
        // Use chat completion with conversation history for personality model
        val conversationHistory = buildConversationHistoryForModel(conversationId)
        
        val parsedResponse = ollamaService.sendChatMessage(
            messages = conversationHistory,
            model = model,
            systemPrompt = systemPrompt // Pass as system prompt for chat completion
        )
        
        val aiMessage = ChatMessage(
            id = UUID.randomUUID().toString(),
            content = parsedResponse.content,
            type = MessageType.AI,
            timestamp = System.currentTimeMillis(),
            modelUsed = model,
            thinkingContent = parsedResponse.thinkingContent
        )
        
        _chatMessages.value = _chatMessages.value + aiMessage
        _isLoading.value = false
        
        conversationRepository.saveMessage(aiMessage, conversationId)
    }
    
    private suspend fun handleLegacyFlow(message: String, conversationId: String) {
        // Original logic for backward compatibility
        val conversationContext = buildConversationContext(conversationId)
        val fullMessage = if (conversationContext.isNotEmpty()) {
            "$conversationContext\n\nUser: $message"
        } else {
            message
        }
        
        // Check for tool usage requests first
        val toolResult = detectAndExecuteTools(message)
        if (toolResult != null) {
            val toolMessage = ChatMessage(
                id = UUID.randomUUID().toString(),
                content = toolResult.summary,
                type = MessageType.AI,
                timestamp = System.currentTimeMillis(),
                modelUsed = "tools",
                conversationId = conversationId
            )
            
            _chatMessages.value = _chatMessages.value + toolMessage
            conversationRepository.saveMessage(toolMessage, conversationId)
            _isLoading.value = false
            return
        }
        
        // Use selected model or route automatically (only if routing is enabled)
        val modelToUse = if (_selectedModel.value != "auto") {
            _selectedModel.value
        } else if (appPreferences.enableModelRouting) {
            intelligenceRouter.selectModelForQuery(message)
        } else {
            "qwen3:8b" // Default model when routing is disabled
        }
        
        // Get system prompt using same method as split-brain
        val selectedPersonalityPrompt = _selectedPersonality.value?.let { personality ->
            personalityProvider.getSystemPrompt(personality.id)
        } ?: personalityProvider.getSystemPrompt("default")
        
        // Build combined prompt: Core identity + Selected personality
        val coreIdentity = systemPromptManager.corePersonalityPrompt
        val combinedSystemPrompt = if (selectedPersonalityPrompt.isNotBlank()) {
            "$coreIdentity\n\n## ACTIVE PERSONALITY\n$selectedPersonalityPrompt"
        } else {
            coreIdentity
        }
        
        // Build the system prompt using PromptStateManager
        val systemPrompt = promptStateManager.buildSystemPrompt(combinedSystemPrompt)
        
        if (_isStreamingEnabled.value) {
            handleStreamingResponse(fullMessage, modelToUse, systemPrompt, conversationId)
        } else {
            handleNormalResponse(fullMessage, modelToUse, systemPrompt, conversationId)
        }
    }
    
    private fun buildConversationSummary(conversationId: String): String {
        // Simple summary for now - just get last few messages
        val recentMessages = _chatMessages.value.takeLast(6)
        return recentMessages.joinToString(" | ") { "${it.type.name}: ${it.content.take(50)}" }
    }
    
    private fun formatToolResult(toolResult: com.yourname.coquettemobile.core.tools.ToolExecutionResult): String {
        val toolName = toolResult.plan?.steps?.firstOrNull()?.toolName ?: "unknown"
        return """
{
  "tool": "$toolName",
  "status": "${if (toolResult.success) "success" else "error"}",
  "summary": "${toolResult.summary}",
  "timestamp": "${System.currentTimeMillis()}"
}
        """.trimIndent()
    }
    
    // Module management functions for UI
    fun getActiveModules() = promptStateManager.activeModules
    
    fun toggleModule(moduleName: String) {
        promptStateManager.toggleModule(moduleName)
    }
    
    fun getAvailableModules() = moduleRegistry.getAvailableModules()
    
    /**
     * Build conversation history for chat completion models
     * Maintains proper message context for personality model
     */
    private suspend fun buildConversationHistoryForModel(conversationId: String): List<ChatMessage> {
        // Get recent messages from current conversation
        val recentMessages = conversationRepository.getRecentMessages(conversationId, 10)
        
        // Filter to only user and AI messages (skip tool/error messages for cleaner context)
        return recentMessages.filter { 
            it.type == MessageType.USER || it.type == MessageType.AI 
        }.sortedBy { it.timestamp }
    }
}
