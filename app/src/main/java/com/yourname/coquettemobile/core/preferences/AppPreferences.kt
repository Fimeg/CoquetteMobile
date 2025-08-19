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
        get() = preferences.getBoolean(KEY_ENABLE_SPLIT_BRAIN, false)
        set(value) = preferences.edit().putBoolean(KEY_ENABLE_SPLIT_BRAIN, value).apply()
    
    companion object {
        private const val KEY_STREAMING_ENABLED = "streaming_enabled"
        private const val KEY_SUBCONSCIOUS_REASONING = "subconscious_reasoning"
        private const val KEY_MODEL_ROUTING = "model_routing"
        private const val KEY_SHOW_MODEL_USED = "show_model_used"
        private const val KEY_PLANNER_MODEL = "planner_model"
        private const val KEY_PERSONALITY_MODEL = "personality_model"
        private const val KEY_ENABLE_SPLIT_BRAIN = "enable_split_brain"
    }
}