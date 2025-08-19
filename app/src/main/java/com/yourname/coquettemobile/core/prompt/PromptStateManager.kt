package com.yourname.coquettemobile.core.prompt

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PromptStateManager @Inject constructor(
    private val moduleRegistry: ModuleRegistry,
    private val memoryStore: MemoryStore
) {
    private val _activeModules = MutableStateFlow<Set<String>>(emptySet())
    val activeModules: StateFlow<Set<String>> = _activeModules.asStateFlow()
    
    private var conversationSummary: String = ""
    private var lastToolResults: String = ""
    private var plannerNote: String? = null
    
    // Core identity prompts (will be loaded from assets)
    private var coreIdentity: String = ""
    private var plannerSystemPrompt: String = ""

    fun initialize(coreIdentity: String, plannerSystemPrompt: String) {
        this.coreIdentity = coreIdentity
        this.plannerSystemPrompt = plannerSystemPrompt
    }

    // --- Module Management ---
    fun activateModule(name: String) {
        if (moduleRegistry.get(name) != null) {
            _activeModules.value = _activeModules.value + name
        }
    }

    fun deactivateModule(name: String) {
        _activeModules.value = _activeModules.value - name
    }

    fun toggleModule(name: String) {
        if (_activeModules.value.contains(name)) {
            deactivateModule(name)
        } else {
            activateModule(name)
        }
    }

    fun listActiveModules(): List<String> = _activeModules.value.toList()

    // --- Memory Management ---
    fun setConversationSummary(summary: String) {
        conversationSummary = summary
    }

    fun addToolResult(result: String) {
        lastToolResults = result
    }

    fun setPlannerNote(note: String?) {
        plannerNote = note
    }

    fun forgetEphemeral() {
        conversationSummary = ""
        lastToolResults = ""
        plannerNote = null
    }

    fun forgetKey(key: String) {
        memoryStore.forgetKey(key)
    }

    fun suppressKey(key: String) {
        memoryStore.suppressKey(key)
    }

    // --- Prompt Assembly ---
    fun buildPersonalityPrompt(userTurn: String): String {
        val builder = StringBuilder()
        builder.append(coreIdentity).append("\n\n")

        // Append active modules
        _activeModules.value.forEach { mod ->
            moduleRegistry.get(mod)?.let { 
                builder.append("## MODULE: $mod\n")
                builder.append(it).append("\n\n") 
            }
        }

        // Append tool awareness (static module)
        moduleRegistry.get("ToolAwareness")?.let { 
            builder.append("## TOOL AWARENESS\n")
            builder.append(it).append("\n\n") 
        }

        // Conversation summary
        if (conversationSummary.isNotBlank()) {
            builder.append("CONVERSATION_SUMMARY:\n$conversationSummary\n\n")
        }

        // Memory retrieval (inject top-k snippets if relevant)
        val snippets = memoryStore.retrieveRelevant(userTurn, k = 3)
        if (snippets.isNotEmpty()) {
            builder.append("MEMORY_SNIPPETS:\n")
            snippets.forEach { builder.append("- $it\n") }
            builder.append("\n")
        }

        // Tool result (if present)
        if (lastToolResults.isNotBlank()) {
            builder.append("TOOL_RESULT:\n$lastToolResults\n\n")
        }

        // Planner note (if any)
        plannerNote?.let { builder.append("PLANNER_NOTE: $it\n\n") }

        // Finally, user input
        builder.append("USER:\n$userTurn")

        return builder.toString()
    }

    fun buildPlannerPrompt(userTurn: String): String {
        val builder = StringBuilder()
        builder.append(plannerSystemPrompt).append("\n\n")

        // Very brief conversation context for planner
        if (conversationSummary.isNotBlank()) {
            builder.append("CONTEXT: $conversationSummary\n\n")
        }

        // User input
        builder.append("USER: $userTurn")

        return builder.toString()
    }

    // --- Token Management ---
    fun estimateTokenCount(text: String): Int {
        // Rough estimate: ~4 characters per token
        return text.length / 4
    }

    fun isPromptTooLong(prompt: String, maxTokens: Int = 32000): Boolean {
        return estimateTokenCount(prompt) > maxTokens
    }

    // Clear old data to reduce prompt size
    fun pruneForTokenLimit() {
        if (conversationSummary.length > 1000) {
            conversationSummary = conversationSummary.takeLast(800) + "..."
        }
        if (lastToolResults.length > 2000) {
            lastToolResults = lastToolResults.takeLast(1500) + "..."
        }
    }
}