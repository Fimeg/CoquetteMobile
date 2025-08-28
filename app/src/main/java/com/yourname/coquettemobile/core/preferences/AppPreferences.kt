package com.yourname.coquettemobile.core.preferences

import android.content.Context
import android.content.SharedPreferences
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AppPreferences @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val preferences: SharedPreferences = context.getSharedPreferences(
        "coquette_preferences",
        Context.MODE_PRIVATE
    )

        var isStreamingEnabled: Boolean
        get() = preferences.getBoolean(KEY_STREAMING_ENABLED, true) // UltraThink: Always streaming by default
        set(value) = preferences.edit().putBoolean(KEY_STREAMING_ENABLED, value).apply()

        // Split-brain architecture preferences removed - using Unity architecture only

        var showModelUsed: Boolean
        get() = preferences.getBoolean(KEY_SHOW_MODEL_USED, true)
        set(value) = preferences.edit().putBoolean(KEY_SHOW_MODEL_USED, value).apply()

        var errorRecoveryModel: String
        get() = preferences.getString(KEY_ERROR_RECOVERY_MODEL, "hf.co/janhq/Jan-v1-4B-GGUF:Q8_0") ?: "hf.co/janhq/Jan-v1-4B-GGUF:Q8_0"
        set(value) = preferences.edit().putString(KEY_ERROR_RECOVERY_MODEL, value).apply()

        // Tool prompt customization
        var unifiedReasoningPrompt: String?
        get() = preferences.getString(KEY_UNIFIED_REASONING_PROMPT, null)
        set(value) = preferences.edit().putString(KEY_UNIFIED_REASONING_PROMPT, value).apply()

        var errorRecoveryPrompt: String?
        get() = preferences.getString(KEY_ERROR_RECOVERY_PROMPT, null)
        set(value) = preferences.edit().putString(KEY_ERROR_RECOVERY_PROMPT, value).apply()

        // Orchestrator system preferences
        var orchestratorModel: String?
        get() = preferences.getString(KEY_ORCHESTRATOR_MODEL, "hf.co/janhq/Jan-v1-4B-GGUF:Q8_0")
        set(value) = preferences.edit().putString(KEY_ORCHESTRATOR_MODEL, value).apply()

        var functionCallingPrompt: String?
        get() = preferences.getString(KEY_FUNCTION_CALLING_PROMPT, null)
        set(value) = preferences.edit().putString(KEY_FUNCTION_CALLING_PROMPT, value).apply()

        var defaultToolAwarenessPrompt: String?
        get() = preferences.getString(KEY_DEFAULT_TOOL_AWARENESS_PROMPT, null)
        set(value) = preferences.edit().putString(KEY_DEFAULT_TOOL_AWARENESS_PROMPT, value).apply()

        // System date prompt
        var includeSystemDate: Boolean
        get() = preferences.getBoolean(KEY_INCLUDE_SYSTEM_DATE, true)
        set(value) = preferences.edit().putBoolean(KEY_INCLUDE_SYSTEM_DATE, value).apply()

        var systemDatePrompt: String?
        get() = preferences.getString(KEY_SYSTEM_DATE_PROMPT, null)
        set(value) = preferences.edit().putString(KEY_SYSTEM_DATE_PROMPT, value).apply()

        // Ollama Server Configuration
        var ollamaServerUrl: String
        get() = preferences.getString(KEY_OLLAMA_SERVER_URL, DEFAULT_OLLAMA_URL) ?: DEFAULT_OLLAMA_URL
        set(value) = preferences.edit().putString(KEY_OLLAMA_SERVER_URL, value).apply()

        // Tool Ollama Server (separate weak server for tools)
        var enableToolOllamaServer: Boolean
        get() = preferences.getBoolean(KEY_ENABLE_TOOL_OLLAMA, false)
        set(value) = preferences.edit().putBoolean(KEY_ENABLE_TOOL_OLLAMA, value).apply()

        var toolOllamaServerUrl: String
        get() = preferences.getString(KEY_TOOL_OLLAMA_SERVER_URL, DEFAULT_TOOL_OLLAMA_URL) ?: DEFAULT_TOOL_OLLAMA_URL
        set(value) = preferences.edit().putString(KEY_TOOL_OLLAMA_SERVER_URL, value).apply()

        var toolOllamaModel: String
        get() = preferences.getString(KEY_TOOL_OLLAMA_MODEL, DEFAULT_TOOL_MODEL) ?: DEFAULT_TOOL_MODEL
        set(value) = preferences.edit().putString(KEY_TOOL_OLLAMA_MODEL, value).apply()

        var developerContextLimit: Int?
        get() {
            val value = preferences.getInt(KEY_DEVELOPER_CONTEXT_LIMIT, -1)
            return if (value == -1) null else value
        }
        set(value) {
            if (value == null) {
                preferences.edit().remove(KEY_DEVELOPER_CONTEXT_LIMIT).apply()
            } else {
                preferences.edit().putInt(KEY_DEVELOPER_CONTEXT_LIMIT, value).apply()
            }
        }

        companion object {
            private const val KEY_STREAMING_ENABLED = "streaming_enabled"
            // Legacy split-brain keys removed
            private const val KEY_SHOW_MODEL_USED = "show_model_used"
            private const val KEY_ERROR_RECOVERY_MODEL = "error_recovery_model"
            private const val KEY_OLLAMA_SERVER_URL = "ollama_server_url"
            private const val KEY_ENABLE_TOOL_OLLAMA = "enable_tool_ollama"
            private const val KEY_TOOL_OLLAMA_SERVER_URL = "tool_ollama_server_url"
            private const val KEY_TOOL_OLLAMA_MODEL = "tool_ollama_model"

            // Tool prompt customization keys
            private const val KEY_UNIFIED_REASONING_PROMPT = "unified_reasoning_prompt"
            private const val KEY_ERROR_RECOVERY_PROMPT = "error_recovery_prompt"
            private const val KEY_FUNCTION_CALLING_PROMPT = "function_calling_prompt"
            private const val KEY_DEFAULT_TOOL_AWARENESS_PROMPT = "default_tool_awareness_prompt"
            private const val KEY_INCLUDE_SYSTEM_DATE = "include_system_date"
            private const val KEY_SYSTEM_DATE_PROMPT = "system_date_prompt"
            private const val KEY_DEVELOPER_CONTEXT_LIMIT = "developer_context_limit"
            private const val KEY_ORCHESTRATOR_MODEL = "orchestrator_model"

            private const val DEFAULT_OLLAMA_URL = "http://10.10.20.19:11434"
                private const val DEFAULT_TOOL_OLLAMA_URL = "http://10.10.20.19:11434"
                    private const val DEFAULT_TOOL_MODEL = "hf.co/janhq/Jan-v1-4B-GGUF:Q8_0"
        }
}
