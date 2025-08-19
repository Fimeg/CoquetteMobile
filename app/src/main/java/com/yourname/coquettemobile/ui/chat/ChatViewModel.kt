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
    private val mobileToolsAgent: MobileToolsAgent
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
    
    private val _selectedModel = MutableStateFlow("qwen3:8b")
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
                
                // Build conversation context from database
                val conversationContext = buildConversationContext(conversation.id)
                val fullMessage = if (conversationContext.isNotEmpty()) {
                    "$conversationContext\n\nUser: $message"
                } else {
                    message
                }
                
                // Check for tool usage requests first
                val toolResult = detectAndExecuteTools(message)
                if (toolResult != null) {
                    // Add tool execution result as AI message
                    val toolMessage = ChatMessage(
                        id = UUID.randomUUID().toString(),
                        content = toolResult.summary,
                        type = MessageType.AI,
                        timestamp = System.currentTimeMillis(),
                        modelUsed = "tools",
                        conversationId = conversation.id
                    )
                    
                    _chatMessages.value = _chatMessages.value + toolMessage
                    conversationRepository.saveMessage(toolMessage, conversation.id)
                    _isLoading.value = false
                    return@launch
                }
                
                // Get system prompt
                val systemPrompt = _selectedPersonality.value?.let { personality ->
                    personalityProvider.getSystemPrompt(personality.id)
                } ?: personalityProvider.getSystemPrompt("default")
                
                if (_isStreamingEnabled.value) {
                    handleStreamingResponse(fullMessage, modelToUse, systemPrompt, conversation.id)
                } else {
                    handleNormalResponse(fullMessage, modelToUse, systemPrompt, conversation.id)
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
                _currentConversation.value = activeConversation
                // Load messages for active conversation
                val messages = conversationRepository.getRecentMessages(activeConversation.id, 100)
                _chatMessages.value = messages
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
}
