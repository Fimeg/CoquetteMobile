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
    
    // Core Personality Prompt (user-editable)
    var corePersonalityPrompt: String
        get() = preferences.getString(KEY_CORE_PERSONALITY, getDefaultCorePersonality()) 
            ?: getDefaultCorePersonality()
        set(value) = preferences.edit().putString(KEY_CORE_PERSONALITY, value).apply()
    
    // Planner System Prompt (user-editable)
    var plannerSystemPrompt: String
        get() = preferences.getString(KEY_PLANNER_SYSTEM, getDefaultPlannerSystem()) 
            ?: getDefaultPlannerSystem()
        set(value) = preferences.edit().putString(KEY_PLANNER_SYSTEM, value).apply()
    
    // Tool Awareness Module (user-editable)
    var toolAwarenessPrompt: String
        get() = preferences.getString(KEY_TOOL_AWARENESS, getDefaultToolAwareness()) 
            ?: getDefaultToolAwareness()
        set(value) = preferences.edit().putString(KEY_TOOL_AWARENESS, value).apply()
    
    private fun getDefaultCorePersonality(): String {
        return """
You are an AI assistant. Be helpful, accurate, and concise. You never output JSON; tool execution is handled elsewhere. When you receive TOOL_RESULT, treat it like fresh information you just looked up. If a PLANNER_NOTE appears, treat it as a quiet aside.

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
    
    private fun getDefaultPlannerSystem(): String {
        return """
You are the Planner. Your job is to decide whether to call a tool or pass control to the Personality. Output valid JSON only, no extra text.

You have access to these tools:
- DeviceContextTool(action: "battery"|"storage"|"network"|"system"|"performance", args:{})
- WebFetchTool(action: "get"|"search", args:{ url?:string, query?:string, site?:string })

Rules:
- If the user asks for device info, prefer DeviceContextTool
- If the user asks for web content, prefer WebFetchTool
- If a tool isn't needed, return {"decision":"respond","reason":"..."}

Examples:
User: "What's my battery?"
Output: {"decision":"tool","reason":"User asked battery","tool":"DeviceContextTool","action":"battery","args":{}}

User: "Tell me a story"
Output: {"decision":"respond","reason":"No tool needed"}
        """.trimIndent()
    }
    
    private fun getDefaultToolAwareness(): String {
        return """
You never call tools directly. You may suggest checks ("let's check current battery info"), trusting the system to return TOOL_RESULT. Discuss tool data like reviewing information you just looked up.

If the app injects TOOL_RESULT:
- Summarize the essentials in 1–3 sentences
- Add brief analysis or options if relevant
- If data is long, highlight key points rather than dumping everything
        """.trimIndent()
    }
    
    // Reset to defaults
    fun resetToDefaults() {
        preferences.edit()
            .remove(KEY_CORE_PERSONALITY)
            .remove(KEY_PLANNER_SYSTEM)
            .remove(KEY_TOOL_AWARENESS)
            .apply()
    }
    
    // Export all prompts as JSON for backup
    fun exportPrompts(): String {
        return """
{
  "core_personality": "${corePersonalityPrompt.replace("\"", "\\\"")}",
  "planner_system": "${plannerSystemPrompt.replace("\"", "\\\"")}",
  "tool_awareness": "${toolAwarenessPrompt.replace("\"", "\\\"")}"
}
        """.trimIndent()
    }
    
    companion object {
        private const val KEY_CORE_PERSONALITY = "core_personality_prompt"
        private const val KEY_PLANNER_SYSTEM = "planner_system_prompt"
        private const val KEY_TOOL_AWARENESS = "tool_awareness_prompt"
    }
}