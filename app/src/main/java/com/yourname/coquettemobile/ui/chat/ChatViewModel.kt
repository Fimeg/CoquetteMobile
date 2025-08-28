package com.yourname.coquettemobile.ui.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.yourname.coquettemobile.core.ai.PersonalityProvider
import com.yourname.coquettemobile.core.orchestration.ExecutionPlan
import com.yourname.coquettemobile.core.orchestration.OrchestrationUpdate
import com.yourname.coquettemobile.core.orchestration.PersonalityOrchestrator
import com.yourname.coquettemobile.core.database.entities.Conversation
import com.yourname.coquettemobile.core.database.entities.Personality
import com.yourname.coquettemobile.core.greeting.WelcomeMessageProvider
import com.yourname.coquettemobile.core.logging.CoquetteLogger
import com.yourname.coquettemobile.core.models.*
import com.yourname.coquettemobile.core.preferences.AppPreferences
import com.yourname.coquettemobile.core.prompt.ModuleRegistry
import com.yourname.coquettemobile.core.prompt.PromptStateManager
import com.yourname.coquettemobile.core.repository.ConversationRepository
import com.yourname.coquettemobile.core.repository.PersonalityRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.*
import javax.inject.Inject

@HiltViewModel
class ChatViewModel @Inject constructor(
    private val personalityOrchestrator: PersonalityOrchestrator,
    private val personalityRepository: PersonalityRepository,
    private val conversationRepository: ConversationRepository,
    private val appPreferences: AppPreferences,
    private val promptStateManager: PromptStateManager,
    private val moduleRegistry: ModuleRegistry,
    private val welcomeMessageProvider: WelcomeMessageProvider,
    private val logger: CoquetteLogger
) : ViewModel() {

    private val _chatMessages = MutableStateFlow<List<ChatMessage>>(emptyList())
    val chatMessages: StateFlow<List<ChatMessage>> = _chatMessages.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _availablePersonalities = MutableStateFlow<List<Personality>>(emptyList())
    val availablePersonalities: StateFlow<List<Personality>> = _availablePersonalities.asStateFlow()

    private val _selectedPersonality = MutableStateFlow<Personality?>(null)
    val selectedPersonality: StateFlow<Personality?> = _selectedPersonality.asStateFlow()

    private val _currentConversation = MutableStateFlow<Conversation?>(null)
    val currentConversation: StateFlow<Conversation?> = _currentConversation.asStateFlow()

    private val _activeModules = MutableStateFlow<List<String>>(emptyList())
    val activeModules: StateFlow<List<String>> = _activeModules.asStateFlow()

    private val _pendingExecutionPlan = MutableStateFlow<ExecutionPlan?>(null)
    val pendingExecutionPlan: StateFlow<ExecutionPlan?> = _pendingExecutionPlan.asStateFlow()

    private val _isStreamingEnabled = MutableStateFlow(true)
    val isStreamingEnabled: StateFlow<Boolean> = _isStreamingEnabled.asStateFlow()

    init {
        initializePersonalities()
        initializeConversation()
        initializeSystems()
        initializeActiveModules()
    }

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

    private fun initializeSystems() {
        viewModelScope.launch {
            moduleRegistry.initialize()
        }
    }

    private fun initializeActiveModules() {
        viewModelScope.launch {
            promptStateManager.activeModules.collect { modules ->
                _activeModules.value = modules.toList()
            }
        }
    }

    private fun addWelcomeMessageToNewChat() {
        viewModelScope.launch {
            val welcomeMessage = ChatMessage(
                content = welcomeMessageProvider.getWelcomeMessage(),
                type = MessageType.SYSTEM,
                modelUsed = "System"
            )
            _chatMessages.value = listOf(welcomeMessage)
        }
    }

    fun sendMessage(message: String) {
        if (message.isBlank()) return

        val userMessage = ChatMessage(
            content = message,
            type = MessageType.USER
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

                handleStreamingOrchestrationFlow(message, conversation.id, currentPersonality)

                if (_chatMessages.value.size <= 4) {
                    conversationRepository.generateAndUpdateTitle(conversation.id)
                }

            } catch (e: Exception) {
                val errorMessage = ChatMessage(
                    content = "Error: ${e.message ?: "Failed to get response"}",
                    type = MessageType.ERROR
                )
                _chatMessages.value = _chatMessages.value + errorMessage
            } finally {
                _isLoading.value = false
            }
        }
    }

    private suspend fun handleStreamingOrchestrationFlow(
        message: String,
        conversationId: String,
        personality: Personality
    ) {
        val liveMessageId = UUID.randomUUID().toString()
        var liveMessage = ChatMessage(
            id = liveMessageId,
            content = "",
            type = MessageType.AI,
            conversationId = conversationId,
            messageState = AiMessageState.THINKING,
            modelUsed = personality.unifiedModel ?: appPreferences.orchestratorModel ?: "deepseek-r1:1.5b"
        )
        _chatMessages.value = _chatMessages.value + liveMessage

        val updateLiveMessage = {
            _chatMessages.value = _chatMessages.value.map { if (it.id == liveMessageId) liveMessage else it }
        }

        try {
            val conversationMessages = buildConversationHistoryForModel(conversationId)
            logger.d("ChatViewModel", "🚀 STARTING STREAMING ORCHESTRATION for: $message")

            val phases = mutableListOf<OrchestrationPhase>()
            val currentThinking = mutableListOf<String>()

            personalityOrchestrator.processRequestStreaming(message, personality.id, conversationMessages).collect { update ->
                logger.d("ChatViewModel", "📨 Received update: ${update.javaClass.simpleName}")

                when (update) {
                    is OrchestrationUpdate.Thinking -> {
                        currentThinking.add(update.thoughts)
                        // Update the thinking in the last phase if it exists
                        if (phases.isNotEmpty()) {
                            val lastPhase = phases.last()
                            val updatedPhase = when (lastPhase) {
                                is OrchestrationPhase.IntentAnalysisPhase -> lastPhase.copy(thinking = lastPhase.thinking + update.thoughts)
                                is OrchestrationPhase.PlanningPhase -> lastPhase.copy(thinking = lastPhase.thinking + update.thoughts)
                                is OrchestrationPhase.ExecutionPhase -> lastPhase.copy(thinking = lastPhase.thinking + update.thoughts)
                                is OrchestrationPhase.SynthesisPhase -> lastPhase.copy(thinking = lastPhase.thinking + update.thoughts)
                                is OrchestrationPhase.PersonalityResponsePhase -> lastPhase.copy(thinking = lastPhase.thinking + update.thoughts)
                            }
                            phases[phases.size - 1] = updatedPhase
                            liveMessage = liveMessage.copy(orchestrationPhases = phases.toList())
                        }
                    }
                    is OrchestrationUpdate.IntentAnalysisComplete -> {
                        val phase = OrchestrationPhase.IntentAnalysisPhase(
                            thinking = currentThinking.toList(),
                            analysis = update.analysis
                        )
                        phases.add(phase)
                        currentThinking.clear()
                        liveMessage = liveMessage.copy(
                            messageState = AiMessageState.ANALYZING_INTENT,
                            orchestrationPhases = phases.toList()
                        )
                    }
                    is OrchestrationUpdate.PlanGenerated -> {
                        val phase = OrchestrationPhase.PlanningPhase(
                            thinking = currentThinking.toList(),
                            plan = update.plan
                        )
                        phases.add(phase)
                        currentThinking.clear()
                        liveMessage = liveMessage.copy(
                            messageState = AiMessageState.PLANNING_TOOLS,
                            orchestrationPhases = phases.toList()
                        )
                    }
                    is OrchestrationUpdate.StepExecution -> {
                        val toolExecution = ToolExecution(
                            toolName = "${update.stepResult.domain}/${update.stepResult.stepId}",
                            args = emptyMap(),
                            result = if (update.stepResult.success) update.stepResult.data.toString() else update.stepResult.error ?: "Unknown error",
                            startTime = 0,
                            endTime = update.stepResult.executionTimeMs,
                            wasSuccessful = update.stepResult.success,
                            reasoning = update.stepResult.stepId
                        )

                        val existingPhase = phases.find { it is OrchestrationPhase.ExecutionPhase } as? OrchestrationPhase.ExecutionPhase
                        if (existingPhase != null) {
                            val updatedExecutions = existingPhase.toolExecutions + toolExecution
                            val updatedPhase = existingPhase.copy(toolExecutions = updatedExecutions)
                            phases[phases.indexOf(existingPhase)] = updatedPhase
                        } else {
                            val newPhase = OrchestrationPhase.ExecutionPhase(
                                thinking = currentThinking.toList(),
                                toolExecutions = listOf(toolExecution)
                            )
                            phases.add(newPhase)
                            currentThinking.clear()
                        }
                        liveMessage = liveMessage.copy(
                            messageState = AiMessageState.EXECUTING_TOOL,
                            orchestrationPhases = phases.toList()
                        )
                    }
                    is OrchestrationUpdate.Complete -> {
                        val phase = OrchestrationPhase.SynthesisPhase(
                            thinking = currentThinking.toList(),
                            response = update.response.message
                        )
                        phases.add(phase)
                        liveMessage = liveMessage.copy(
                            messageState = AiMessageState.COMPLETE,
                            content = update.response.message,
                            orchestrationPhases = phases.toList()
                        )
                    }
                    is OrchestrationUpdate.Error -> {
                        liveMessage = liveMessage.copy(
                            messageState = AiMessageState.COMPLETE,
                            content = "An error occurred: ${update.message}",
                            type = MessageType.ERROR
                        )
                    }
                }
                updateLiveMessage()
            }
            conversationRepository.saveMessage(liveMessage, conversationId)

        } catch (e: Exception) {
            logger.e("ChatViewModel", "🚨 STREAMING ORCHESTRATION FAILED: ${e.message}")
            liveMessage = liveMessage.copy(
                content = "A critical error occurred during orchestration: ${e.message}",
                type = MessageType.ERROR,
                messageState = AiMessageState.COMPLETE
            )
            updateLiveMessage()
            conversationRepository.saveMessage(liveMessage, conversationId)
        } finally {
            _isLoading.value = false
        }
    }

    fun updatePersonality(personality: Personality) {
        _selectedPersonality.value = personality
        promptStateManager.setCurrentPersonality(personality)
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

    private suspend fun buildConversationHistoryForModel(conversationId: String): List<ChatMessage> {
        return conversationRepository.getRecentMessages(conversationId, 10)
            .filter { it.type == MessageType.USER || it.type == MessageType.AI }
            .sortedBy { it.timestamp }
    }

    fun toggleStreaming(enabled: Boolean) {
        // This feature is now architecturally enforced and no longer a user option.
    }

    fun executeApprovedPlan(plan: ExecutionPlan) {
        viewModelScope.launch {
            _pendingExecutionPlan.value = null
        }
    }

    fun cancelPendingPlan() {
        _pendingExecutionPlan.value = null
        val cancelMessage = ChatMessage(
            content = "Operation cancelled by user.",
            type = MessageType.AI,
            messageState = AiMessageState.COMPLETE
        )
        _chatMessages.value = _chatMessages.value + cancelMessage
    }

    fun modifyPendingPlan(modifications: String) {
        viewModelScope.launch {
            _pendingExecutionPlan.value = null
        }
    }
}
