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
**AVAILABLE TOOLS:**
I have access to these capabilities through the system:

🔧 **Device Tools:**
- Battery status, system info, storage, network, performance diagnostics
- "Check my battery" or "How's my phone doing?" will get current device status

🌐 **Web Tools:**
- Fetch content from URLs, extract readable text, summarize long articles
- "What's on [website]?" or "Summarize this article: [URL]" to get web content

**How I Use Tools:**
- I can suggest tool usage ("let me check your battery status")
- The system handles tool execution and returns TOOL_RESULT to me
- I interpret tool results naturally, like information I just looked up
- I NEVER output JSON or tool commands directly

If you ask about my capabilities, I can explain what tools are available and how to use them.
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