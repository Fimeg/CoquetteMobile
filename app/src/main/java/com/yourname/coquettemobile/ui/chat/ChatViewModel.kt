package com.yourname.coquettemobile.ui.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.yourname.coquettemobile.core.ai.MobileErrorRecoveryAgent
import com.yourname.coquettemobile.core.ai.OllamaService
import com.yourname.coquettemobile.core.ai.PersonalityProvider
import com.yourname.coquettemobile.core.ai.UnifiedReasoningAgent
import com.yourname.coquettemobile.core.database.entities.Conversation
import com.yourname.coquettemobile.core.database.entities.Personality
import com.yourname.coquettemobile.core.greeting.WelcomeMessageProvider
import com.yourname.coquettemobile.core.models.ChatMessage
import com.yourname.coquettemobile.core.models.MessageType
import com.yourname.coquettemobile.core.models.ToolExecution
import com.yourname.coquettemobile.core.models.AiMessageState
import com.yourname.coquettemobile.core.preferences.AppPreferences
import com.yourname.coquettemobile.core.prompt.ModuleRegistry
import com.yourname.coquettemobile.core.prompt.PromptStateManager
import com.yourname.coquettemobile.core.prompt.SystemPromptManager
import com.yourname.coquettemobile.core.repository.ConversationRepository
import com.yourname.coquettemobile.core.repository.PersonalityRepository
import com.yourname.coquettemobile.core.tools.*
import com.yourname.coquettemobile.utils.ContextSizer
import com.yourname.coquettemobile.utils.ThinkingParser
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.*
import javax.inject.Inject

@HiltViewModel
class ChatViewModel @Inject constructor(
    private val ollamaService: OllamaService,
        private val unifiedReasoningAgent: UnifiedReasoningAgent,
            private val personalityProvider: PersonalityProvider,
                private val personalityRepository: PersonalityRepository,
                    private val conversationRepository: ConversationRepository,
                        private val appPreferences: AppPreferences,
                            private val promptStateManager: PromptStateManager,
                                private val moduleRegistry: ModuleRegistry,
                                    private val systemPromptManager: SystemPromptManager,
                                        private val contextSizer: ContextSizer,
                                            private val webFetchTool: WebFetchTool,
                                                private val extractorTool: ExtractorTool,
                                                    private val summarizerTool: SummarizerTool,
                                                        private val deviceContextTool: DeviceContextTool,
                                                            private val notificationTool: NotificationTool,
                                                                private val webImageTool: WebImageTool,
                                                                    private val errorRecoveryAgent: MobileErrorRecoveryAgent,
                                                                        private val welcomeMessageProvider: WelcomeMessageProvider
) : ViewModel() {
    private val _chatMessages = MutableStateFlow<List<ChatMessage>>(emptyList())
    val chatMessages: StateFlow<List<ChatMessage>> = _chatMessages.asStateFlow()
    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    // Removed ProcessingState - now using stateful ChatMessage lifecycle

    private val _availablePersonalities = MutableStateFlow<List<Personality>>(emptyList())
    val availablePersonalities: StateFlow<List<Personality>> = _availablePersonalities.asStateFlow()

    private val _selectedPersonality = MutableStateFlow<Personality?>(null)
    val selectedPersonality: StateFlow<Personality?> = _selectedPersonality.asStateFlow()

    private val _isStreamingEnabled = MutableStateFlow(appPreferences.isStreamingEnabled)
    val isStreamingEnabled: StateFlow<Boolean> = _isStreamingEnabled.asStateFlow()

    private val _currentConversation = MutableStateFlow<Conversation?>(null)
    val currentConversation: StateFlow<Conversation?> = _currentConversation.asStateFlow()

    private val _activeModules = MutableStateFlow<List<String>>(emptyList())
    val activeModules: StateFlow<List<String>> = _activeModules.asStateFlow()

    // --- Restored Properties ---
    private val _availableModels = MutableStateFlow<List<String>>(emptyList())
    val availableModels: StateFlow<List<String>> = _availableModels.asStateFlow()

    private val _selectedModel = MutableStateFlow("auto")
    val selectedModel: StateFlow<String> = _selectedModel.asStateFlow()
    // -------------------------

    init {
        loadAvailableModels()
        initializePersonalities()
        initializeConversation()
        initializeSystems()
        initializeActiveModules()
    }

    private fun addWelcomeMessageToNewChat() {
        viewModelScope.launch {
            val welcomeMessage = ChatMessage(
                id = UUID.randomUUID().toString(),
                                             content = welcomeMessageProvider.getWelcomeMessage(),
                                             type = MessageType.AI,
                                             timestamp = System.currentTimeMillis(),
                                             modelUsed = "System"
            )
            _chatMessages.value = listOf(welcomeMessage)
        }
    }

    private fun initializeActiveModules() {
        viewModelScope.launch {
            promptStateManager.activeModules.collect { modules ->
                _activeModules.value = modules.toList()
            }
        }
    }

    private fun initializeSystems() {
        viewModelScope.launch {
            moduleRegistry.initialize()
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
                    val conversation = _currentConversation.value ?: createNewConversation()
                    conversationRepository.saveMessage(userMessage, conversation.id)

                    val currentPersonality = _selectedPersonality.value
                    ?: personalityRepository.getDefaultPersonality()
                    ?: throw IllegalStateException("No personality available")

                    promptStateManager.setCurrentPersonality(currentPersonality)
                    handleUnityFlow(message, conversation.id, currentPersonality)

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
                } finally {
                    _isLoading.value = false
                    // Removed processingState - now using stateful ChatMessage lifecycle
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

        _chatMessages.value = _chatMessages.value + aiMessage

        val fullContentBuilder = StringBuilder()

        ollamaService.sendMessageStream(
            message = message,
            model = modelToUse,
            systemPrompt = systemPrompt
        ).collect { chunk ->
            fullContentBuilder.append(chunk)
            val currentText = fullContentBuilder.toString()

            val parsed = ThinkingParser.parseThinkingTags(currentText)

            val updatedMessage = aiMessage.copy(
                content = parsed.content,
                thinkingContent = parsed.thinkingContent
            )
            _chatMessages.value = _chatMessages.value.map { msg ->
                if (msg.id == aiMessageId) updatedMessage else msg
            }
        }

        val finalMessage = _chatMessages.value.find { it.id == aiMessageId }
        if (finalMessage != null) {
            conversationRepository.saveMessage(finalMessage, conversationId)
        }
    }

    private suspend fun handleNormalResponse(message: String, modelToUse: String, systemPrompt: String, conversationId: String) {
        val parsedResponse = ollamaService.sendMessage(
            message = message,
            model = modelToUse,
            systemPrompt = systemPrompt
        )

        val aiMessage = ChatMessage(
            id = UUID.randomUUID().toString(),
                                    content = parsedResponse.content,
                                    type = MessageType.AI,
                                    timestamp = System.currentTimeMillis(),
                                    modelUsed = modelToUse,
                                    thinkingContent = parsedResponse.thinkingContent
        )
        _chatMessages.value = _chatMessages.value + aiMessage
        conversationRepository.saveMessage(aiMessage, conversationId)
    }

    fun updatePersonality(personality: Personality) {
        _selectedPersonality.value = personality
        promptStateManager.setCurrentPersonality(personality)
    }

    // --- Restored Function ---
    fun loadAvailableModels() {
        viewModelScope.launch {
            try {
                val models = ollamaService.getAvailableModels()
                _availableModels.value = listOf("auto") + models
            } catch (e: Exception) {
                _availableModels.value = listOf("auto")
            }
        }
    }
    // -----------------------

    private fun initializePersonalities() {
        viewModelScope.launch {
            personalityRepository.seedDefaultPersonalities()
            personalityRepository.getAllPersonalities().collect { personalities ->
                _availablePersonalities.value = personalities
                if (_selectedPersonality.value == null) {
                    _selectedPersonality.value = personalities.find { it.isDefault } ?: personalities.firstOrNull()
                }
            }
        }
    }

    fun clearChat() {
        viewModelScope.launch {
            createNewConversation()
            addWelcomeMessageToNewChat()
        }
    }

    private suspend fun createNewConversation(): Conversation {
        val conversation = conversationRepository.createNewConversation()
        _currentConversation.value = conversation
        _chatMessages.value = emptyList()
        return conversation
    }

    fun switchToConversation(conversationId: String) {
        viewModelScope.launch {
            val conversation = conversationRepository.switchToConversation(conversationId)
            _currentConversation.value = conversation
            _chatMessages.value = emptyList()
            conversation?.let {
                val messages = conversationRepository.getRecentMessages(it.id, 100)
                _chatMessages.value = messages
            }
        }
    }

    private fun initializeConversation() {
        viewModelScope.launch {
            val activeConversation = conversationRepository.getActiveConversation()
            if (activeConversation != null) {
                switchToConversation(activeConversation.id)
            } else {
                clearChat()
            }
        }
    }

    fun toggleStreaming(enabled: Boolean) {
        _isStreamingEnabled.value = enabled
        appPreferences.isStreamingEnabled = enabled
    }

    private suspend fun buildConversationHistoryForModel(conversationId: String): List<ChatMessage> {
        return conversationRepository.getRecentMessages(conversationId, 10)
        .filter { it.type == MessageType.USER || it.type == MessageType.AI }
        .sortedBy { it.timestamp }
    }

    private fun formatConversationHistoryAsString(messages: List<ChatMessage>): String {
        return messages.joinToString("\n\n") { message ->
            when (message.type) {
                MessageType.USER -> "User: ${message.content}"
                MessageType.AI -> "Assistant: ${message.content}"
                else -> ""
            }
        }.trim().ifEmpty { "No previous conversation history." }
    }

    private suspend fun handleUnityFlow(
        message: String,
        conversationId: String,
        personality: Personality
    ) {
        val liveMessageId = UUID.randomUUID().toString()

        try {
            // --- IMMEDIATE THINKING INDICATOR ---
            // Create the thinking message INSTANTLY before any model processing
            var liveMessage = ChatMessage(
                id = liveMessageId,
                content = "",
                type = MessageType.AI,
                conversationId = conversationId,
                messageState = AiMessageState.THINKING,
                thinkingContent = null, // Empty at first - will be populated when available
                modelUsed = personality.unifiedModel ?: "hf.co/janhq/Jan-v1-4B-GGUF:Q8_0"
            )
            _chatMessages.value = _chatMessages.value + liveMessage

            // --- NOW PROCESS WITH MODEL ---
            val conversationMessages = buildConversationHistoryForModel(conversationId)
            val unifiedResponse = unifiedReasoningAgent.processRequest(
                query = message,
                conversationHistory = conversationMessages,
                personality = personality
            )

            // --- UPDATE WITH THINKING CONTENT ---
            liveMessage = liveMessage.copy(thinkingContent = unifiedResponse.reasoning)
            _chatMessages.value = _chatMessages.value.map { 
                if (it.id == liveMessageId) liveMessage else it 
            }

            // If no tools are needed, handle it as a simple, direct response.
            if (!unifiedResponse.hasTools || unifiedResponse.toolsToUse.isEmpty()) {
                val directMessage = liveMessage.copy(
                    content = unifiedResponse.directResponse ?: "I'm not sure how to respond to that.",
                    messageState = AiMessageState.COMPLETE
                )
                _chatMessages.value = _chatMessages.value.map { 
                    if (it.id == liveMessageId) directMessage else it 
                }
                conversationRepository.saveMessage(directMessage, conversationId)
                return
            }

            // 2. EXECUTE tools and update the message with results.
            liveMessage = executeToolChainAndUpdateMessage(liveMessage, unifiedResponse, message)

            // 3. SYNTHESIZE the final response and stream it into the message.
            val finalPrompt = buildFinalResponsePrompt(
                originalQuery = message,
                toolResults = liveMessage.toolExecutions.map { it.result },
                conversationHistory = formatConversationHistoryAsString(conversationMessages)
            )

            val fullContentBuilder = StringBuilder()
            ollamaService.sendMessageStream(
                message = finalPrompt,
                model = personality.unifiedModel ?: "hf.co/janhq/Jan-v1-4B-GGUF:Q8_0",
                systemPrompt = personality.customSystemPrompt ?: personality.systemPrompt,
                conversationHistory = conversationMessages
            ).collect { chunk ->
                fullContentBuilder.append(chunk)
                // Strip any <think> tags from final response before displaying
                val cleanContent = stripThinkTags(fullContentBuilder.toString())
                // Update the message in real-time as the final content streams in
                _chatMessages.value = _chatMessages.value.map {
                    if (it.id == liveMessageId) it.copy(content = cleanContent) else it
                }
            }

            // 4. SET the message to its final COMPLETE state.
            val finalMessage = liveMessage.copy(
                content = stripThinkTags(fullContentBuilder.toString()),
                messageState = AiMessageState.COMPLETE
            )
            _chatMessages.value = _chatMessages.value.map { if (it.id == liveMessageId) finalMessage else it }
            conversationRepository.saveMessage(finalMessage, conversationId)

        } catch (e: Exception) {
            val errorMessage = ChatMessage(
                content = "Unity processing failed: ${e.message}",
                type = MessageType.ERROR,
                conversationId = conversationId
            )
            _chatMessages.value = _chatMessages.value + errorMessage
            conversationRepository.saveMessage(errorMessage, conversationId)
        } finally {
            _isLoading.value = false
        }
    }

    private suspend fun executeToolChainAndUpdateMessage(
        initialMessage: ChatMessage,
        unifiedResponse: UnifiedReasoningAgent.UnifiedResponse,
        originalQuery: String
    ): ChatMessage {
        var updatedMessage = initialMessage
        val finalToolExecutions = mutableListOf<ToolExecution>()
        var chainedData: String? = null

        for (toolSelection in unifiedResponse.toolsToUse) {
            val effectiveArgs = when {
                toolSelection.toolName == "ExtractorTool" && chainedData != null -> toolSelection.args.plus("html" to chainedData)
                toolSelection.toolName == "SummarizerTool" && chainedData != null -> toolSelection.args.plus("text" to chainedData)
                else -> toolSelection.args
            }

            val startTime = System.currentTimeMillis()
            val result = executeUnityToolStreaming(toolSelection.toolName, effectiveArgs) { progress ->
                // Progress updates could be handled here if needed
            }
            val endTime = System.currentTimeMillis()

            val toolResult = ToolResult.success(result)
            val isValid = errorRecoveryAgent.validateToolResults(toolSelection.toolName, toolResult, originalQuery)

            val executionData = ToolExecution(
                toolName = toolSelection.toolName,
                args = effectiveArgs,
                result = result,
                startTime = startTime,
                endTime = endTime,
                wasSuccessful = isValid,
                reasoning = toolSelection.reasoning
            )
            finalToolExecutions.add(executionData)

            if (isValid && (toolSelection.toolName == "WebFetchTool" || toolSelection.toolName == "ExtractorTool")) {
                chainedData = result
            }

            // Create an updated version of the message with the new tool result
            updatedMessage = initialMessage.copy(
                messageState = AiMessageState.EXECUTING_TOOL,
                toolExecutions = finalToolExecutions.toList()
            )
            
            // Replace the old version of the message in the chat list with the new one
            _chatMessages.value = _chatMessages.value.map { if (it.id == initialMessage.id) updatedMessage else it }
        }
        return updatedMessage
    }

    private fun buildFinalResponsePrompt(
        originalQuery: String,
        toolResults: List<String>,
        conversationHistory: String
    ): String {
        return """
        CONVERSATION HISTORY:
        $conversationHistory

        CURRENT USER QUERY: $originalQuery

        TOOL RESULTS:
        ${toolResults.joinToString("\n\n")}

        Based on the conversation and tool results, provide a comprehensive response. Synthesize information naturally.
        """.trimIndent()
    }

    private suspend fun executeUnityToolStreaming(
        toolName: String,
        args: Map<String, Any>,
        onProgress: (String) -> Unit
    ): String {
        return try {
            when (toolName) {
                "WebFetchTool" -> webFetchTool.executeStreaming(args, onProgress).output
                "ExtractorTool" -> extractorTool.executeStreaming(args, onProgress).output
                "SummarizerTool" -> summarizerTool.executeStreaming(args, onProgress).output
                "DeviceContextTool" -> deviceContextTool.executeStreaming(args, onProgress).output
                "NotificationTool" -> notificationTool.executeStreaming(args, onProgress).output
                "WebImageTool" -> webImageTool.executeStreaming(args, onProgress).output
                else -> "Unknown tool: $toolName"
            }
        } catch (e: Exception) {
            "Tool execution failed: ${e.message}"
        }
    }

    private fun stripThinkTags(content: String): String {
        val thinkRegex = """<think>.*?</think>""".toRegex(RegexOption.DOT_MATCHES_ALL)
        return thinkRegex.replace(content, "").trim()
    }
}
