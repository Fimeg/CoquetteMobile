package com.yourname.coquettemobile.core.prompt

import android.content.Context
import android.content.SharedPreferences
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SystemPromptManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val preferences: SharedPreferences = context.getSharedPreferences(
        "system_prompts",
        Context.MODE_PRIVATE
    )

        var corePersonalityPrompt: String
        get() = preferences.getString(KEY_CORE_PERSONALITY, getDefaultCorePersonality())
        ?: getDefaultCorePersonality()
        set(value) = preferences.edit().putString(KEY_CORE_PERSONALITY, value).apply()

        private fun getDefaultCorePersonality(): String {
            return """
            You are an AI assistant. Be helpful, accurate, and concise. 

            **Tool Usage (Unity Architecture)**
            - When you need to use tools, respond with JSON containing reasoning and tools array
            - When you receive TOOL_RESULT, treat it like fresh information you just looked up  
            - If no tools are needed, respond directly with helpful conversation
            - Always include reasoning for your tool choices

            **Conversation Style**
            - Speak naturally and conversationally
            - Explain technical terms when needed
            - Acknowledge uncertainty rather than guessing
            - Provide helpful follow-up suggestions

            **Memory**
            - Treat injected MEMORY_SNIPPETs as relevant context
            - Confirm when the user requests forgetting information
            """.trimIndent()
        }

        fun resetToDefaults() {
            preferences.edit()
            .remove(KEY_CORE_PERSONALITY)
            .apply()
        }

        fun exportPrompts(): String {
            val escapedPrompt = corePersonalityPrompt.replace("\"", "\\\"").replace("\n", "\\n")
            return """
            {
            "core_personality": "$escapedPrompt"
        }
        """.trimIndent()
        }

        fun importPrompts(jsonString: String): Boolean {
            return try {
                val json = org.json.JSONObject(jsonString)
                corePersonalityPrompt = json.getString("core_personality")
                true
            } catch (e: Exception) {
                false
            }
        }

        companion object {
            private const val KEY_CORE_PERSONALITY = "core_personality_prompt"
        }
}
