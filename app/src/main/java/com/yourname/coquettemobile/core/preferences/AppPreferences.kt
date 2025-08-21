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
        get() = preferences.getBoolean(KEY_STREAMING_ENABLED, false)
        set(value) = preferences.edit().putBoolean(KEY_STREAMING_ENABLED, value).apply()
    
    var enableSubconsciousReasoning: Boolean
        get() = preferences.getBoolean(KEY_SUBCONSCIOUS_REASONING, true)
        set(value) = preferences.edit().putBoolean(KEY_SUBCONSCIOUS_REASONING, value).apply()
    
    var enableModelRouting: Boolean
        get() = preferences.getBoolean(KEY_MODEL_ROUTING, true)
        set(value) = preferences.edit().putBoolean(KEY_MODEL_ROUTING, value).apply()
    
    var showModelUsed: Boolean
        get() = preferences.getBoolean(KEY_SHOW_MODEL_USED, true)
        set(value) = preferences.edit().putBoolean(KEY_SHOW_MODEL_USED, value).apply()
    
    // Split-brain model selection
    var plannerModel: String
        get() = preferences.getString(KEY_PLANNER_MODEL, "gemma3n:e4b") ?: "gemma3n:e4b"
        set(value) = preferences.edit().putString(KEY_PLANNER_MODEL, value).apply()
    
    var personalityModel: String
        get() = preferences.getString(KEY_PERSONALITY_MODEL, "deepseek-r1:32b") ?: "deepseek-r1:32b"
        set(value) = preferences.edit().putString(KEY_PERSONALITY_MODEL, value).apply()
    
    var enableSplitBrain: Boolean
        get() = preferences.getBoolean(KEY_ENABLE_SPLIT_BRAIN, true)
        set(value) = preferences.edit().putBoolean(KEY_ENABLE_SPLIT_BRAIN, value).apply()
    
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
    
    companion object {
        private const val KEY_STREAMING_ENABLED = "streaming_enabled"
        private const val KEY_SUBCONSCIOUS_REASONING = "subconscious_reasoning"
        private const val KEY_MODEL_ROUTING = "model_routing"
        private const val KEY_SHOW_MODEL_USED = "show_model_used"
        private const val KEY_PLANNER_MODEL = "planner_model"
        private const val KEY_PERSONALITY_MODEL = "personality_model"
        private const val KEY_ENABLE_SPLIT_BRAIN = "enable_split_brain"
        private const val KEY_OLLAMA_SERVER_URL = "ollama_server_url"
        private const val KEY_ENABLE_TOOL_OLLAMA = "enable_tool_ollama"
        private const val KEY_TOOL_OLLAMA_SERVER_URL = "tool_ollama_server_url"
        private const val KEY_TOOL_OLLAMA_MODEL = "tool_ollama_model"
        
        private const val DEFAULT_OLLAMA_URL = "http://10.10.20.19:11434"
        private const val DEFAULT_TOOL_OLLAMA_URL = "http://10.10.20.120:11434"
        private const val DEFAULT_TOOL_MODEL = "gemma-3-270m-it"
    }
}