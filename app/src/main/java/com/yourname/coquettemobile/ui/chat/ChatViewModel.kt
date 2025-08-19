package com.yourname.coquettemobile.ui.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.yourname.coquettemobile.core.ai.IntelligenceRouter
import com.yourname.coquettemobile.core.ai.OllamaService
import com.yourname.coquettemobile.core.ai.PersonalityProvider
import com.yourname.coquettemobile.core.ai.SubconsciousReasoner
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

    private val _availablePersonalities = MutableStateFlow<List<Personality>>(emptyList())
    val availablePersonalities: StateFlow<List<Personality>> = _availablePersonalities.asStateFlow()
    
    private val _selectedPersonality = MutableStateFlow<Personality?>(null)
    val selectedPersonality: StateFlow<Personality?> = _selectedPersonality.asStateFlow()
    
    private val _availableModels = MutableStateFlow<List<String>>(emptyList())
    val availableModels: StateFlow<List<String>> = _availableModels.asStateFlow()
    
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
            
            // Update tool awareness module from SystemPromptManager
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

        viewModelScope.launch {
            try {
                // Ensure we have an active conversation
                val conversation = _currentConversation.value ?: createNewConversation()
                
                // Save user message to database
                conversationRepository.saveMessage(userMessage, conversation.id)
                
                // Use selected model or route automatically
                val modelToUse = if (_selectedModel.value != "auto") {
                    _selectedModel.value
                } else {
                    intelligenceRouter.selectModelForQuery(message)
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
        }
    }
    
    private suspend fun handleNormalResponse(message: String, modelToUse: String, systemPrompt: String, conversationId: String) {
        val parsedResponse = ollamaService.sendMessage(
            message = message,
            model = modelToUse,
            systemPrompt = systemPrompt
        )

        // Apply subconscious reasoning for complex queries
        val finalContent = if (intelligenceRouter.requiresDeepReasoning(message)) {
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
    
    private fun loadAvailableModels() {
        viewModelScope.launch {
            try {
                _connectionStatus.value = "Connecting..."
                val models = ollamaService.getAvailableModels()
                _availableModels.value = listOf("auto") + models
                _connectionStatus.value = if (models.isNotEmpty()) "Connected" else "No models found"
            } catch (e: Exception) {
                _connectionStatus.value = "Connection failed"
                _availableModels.value = listOf("auto")
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
        try {
            // Step 1: Use Planner to decide what to do
            val conversationSummary = buildConversationSummary(conversationId)
            val decision = plannerService.planAction(
                userTurn = message,
                conversationSummary = conversationSummary,
                plannerModel = appPreferences.plannerModel
            )
            
            when (decision.decision) {
                "tool" -> {
                    handleToolExecution(decision, message, conversationId)
                }
                "respond" -> {
                    handlePersonalityResponse(message, conversationId, decision.reason)
                }
                else -> {
                    handlePersonalityResponse(message, conversationId, "Unknown planner decision")
                }
            }
        } catch (e: Exception) {
            // Fallback to personality response
            handlePersonalityResponse(message, conversationId, "Planner error: ${e.message}")
        }
    }
    
    private suspend fun handleToolExecution(decision: PlannerDecision, message: String, conversationId: String) {
        // Execute the tool using existing mobile tools agent
        val toolResult = when (decision.tool) {
            "DeviceContextTool" -> {
                val action = decision.action ?: "all"
                mobileToolsAgent.executeTools("device $action")
            }
            else -> {
                // For now, fallback to existing tool detection
                detectAndExecuteTools(message)
            }
        }
        
        if (toolResult != null) {
            // Update prompt state with tool result
            promptStateManager.addToolResult(formatToolResult(toolResult))
            
            // Create tool result message
            val toolMessage = ChatMessage(
                id = UUID.randomUUID().toString(),
                content = toolResult.summary,
                type = MessageType.AI,
                timestamp = System.currentTimeMillis(),
                modelUsed = decision.tool ?: "tools",
                conversationId = conversationId
            )
            
            _chatMessages.value = _chatMessages.value + toolMessage
            conversationRepository.saveMessage(toolMessage, conversationId)
            
            // Now use personality model to comment on the tool result
            handlePersonalityResponse(message, conversationId, null, hasToolResult = true)
        } else {
            // Tool execution failed, use personality to respond
            handlePersonalityResponse(message, conversationId, "Tool execution failed")
        }
    }
    
    private suspend fun handlePersonalityResponse(
        message: String, 
        conversationId: String, 
        plannerNote: String? = null,
        hasToolResult: Boolean = false
    ) {
        // Set planner note if any
        promptStateManager.setPlannerNote(plannerNote)
        
        // Get the selected personality system prompt and combine with core identity
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
        
        // Build the personality prompt using PromptStateManager with personality override
        val personalityPrompt = promptStateManager.buildPersonalityPrompt(message, combinedSystemPrompt)
        
        // Use personality model to generate response
        val personalityModel = appPreferences.personalityModel
        
        if (_isStreamingEnabled.value) {
            handleSplitBrainStreamingResponse(personalityPrompt, personalityModel, conversationId)
        } else {
            handleSplitBrainNormalResponse(personalityPrompt, personalityModel, conversationId)
        }
    }
    
    private suspend fun handleSplitBrainStreamingResponse(prompt: String, model: String, conversationId: String) {
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
            ollamaService.sendMessageStream(
                message = prompt,
                model = model,
                systemPrompt = null // Already included in prompt
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
        }
    }
    
    private suspend fun handleSplitBrainNormalResponse(prompt: String, model: String, conversationId: String) {
        val parsedResponse = ollamaService.sendMessage(
            message = prompt,
            model = model,
            systemPrompt = null // Already included in prompt
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
        
        // Use selected model or route automatically
        val modelToUse = if (_selectedModel.value != "auto") {
            _selectedModel.value
        } else {
            intelligenceRouter.selectModelForQuery(message)
        }
        
        // Get system prompt
        val systemPrompt = _selectedPersonality.value?.let { personality ->
            personalityProvider.getSystemPrompt(personality.id)
        } ?: personalityProvider.getSystemPrompt("default")
        
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
}
