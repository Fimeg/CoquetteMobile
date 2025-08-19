package com.yourname.coquettemobile.core.ai

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import org.json.JSONArray
import javax.inject.Inject
import javax.inject.Singleton

data class PlannerDecision(
    val decision: String, // "tool" or "respond"
    val reason: String,
    val tool: String? = null,
    val action: String? = null,
    val args: Map<String, Any> = emptyMap(),
    val followupPlan: List<PlannerFollowup> = emptyList()
)

data class PlannerFollowup(
    val expect: String,
    val nextTool: String?
)

@Singleton
class PlannerService @Inject constructor(
    private val ollamaService: OllamaService
) {
    
    suspend fun planAction(
        userTurn: String,
        conversationSummary: String = "",
        plannerModel: String = "gemma3n:e4b"
    ): PlannerDecision = withContext(Dispatchers.IO) {
        val plannerPrompt = buildPlannerPrompt(userTurn, conversationSummary)
        
        try {
            val response = ollamaService.sendMessage(
                message = plannerPrompt,
                model = plannerModel,
                systemPrompt = getPlannerSystemPrompt(),
                streaming = false
            )
            
            parseDecision(response.content)
        } catch (e: Exception) {
            // Fallback: assume we should respond with personality
            PlannerDecision(
                decision = "respond",
                reason = "Planner error: ${e.message}"
            )
        }
    }

    private fun buildPlannerPrompt(userTurn: String, conversationSummary: String): String {
        val builder = StringBuilder()
        
        if (conversationSummary.isNotBlank()) {
            builder.append("CONTEXT: $conversationSummary\n\n")
        }
        
        builder.append("USER: $userTurn")
        
        return builder.toString()
    }

    private fun getPlannerSystemPrompt(): String {
        return """
You are the Planner. Your job is to decide whether to call a tool or pass control to the Personality. Output valid JSON only, no extra text.

You have access to these tools:
- DeviceContextTool(action: "battery"|"storage"|"network"|"system"|"performance", args:{})
- WebFetchTool(action: "get"|"search", args:{ url?:string, query?:string, site?:string })
- ExtractorTool(action: "readability", args:{ html?:string, url?:string })
- SummarizerTool(action: "bullets", args:{ text:string, target_tokens?:number })

Rules:
- If the user asks for device info or web content, prefer tools
- Chain tools when necessary: fetch → extract → summarize
- Keep args minimal; no commentary
- If a tool isn't needed, return {"decision":"respond","reason":"..."}

Examples:

User: "What's on Slashdot today?"
Output:
{
  "decision":"tool",
  "reason":"Fetch and summarize Slashdot frontpage",
  "tool":"WebFetchTool",
  "action":"get",
  "args":{"url":"https://slashdot.org/"},
  "followup_plan":[
    {"expect":"html","next_tool":"ExtractorTool"},
    {"expect":"readable_text","next_tool":"SummarizerTool"}
  ]
}

User: "What's my battery?"
Output:
{"decision":"tool","reason":"User asked battery","tool":"DeviceContextTool","action":"battery","args":{}}

User: "Tell me a story about a lighthouse."
Output:
{"decision":"respond","reason":"No tool needed"}
        """.trimIndent()
    }

    private fun parseDecision(jsonResponse: String): PlannerDecision {
        return try {
            val json = JSONObject(jsonResponse.trim())
            
            val decision = json.getString("decision")
            val reason = json.getString("reason")
            
            if (decision == "tool") {
                val tool = json.optString("tool")
                val action = json.optString("action")
                val args = parseArgs(json.optJSONObject("args"))
                val followupPlan = parseFollowupPlan(json.optJSONArray("followup_plan"))
                
                PlannerDecision(
                    decision = decision,
                    reason = reason,
                    tool = tool,
                    action = action,
                    args = args,
                    followupPlan = followupPlan
                )
            } else {
                PlannerDecision(
                    decision = decision,
                    reason = reason
                )
            }
        } catch (e: Exception) {
            // JSON parsing failed - try to repair or fallback
            repairAndParseDecision(jsonResponse) ?: PlannerDecision(
                decision = "respond",
                reason = "JSON parse error: ${e.message}"
            )
        }
    }

    private fun parseArgs(argsJson: JSONObject?): Map<String, Any> {
        if (argsJson == null) return emptyMap()
        
        val args = mutableMapOf<String, Any>()
        argsJson.keys().forEach { key ->
            args[key] = argsJson.get(key)
        }
        return args
    }

    private fun parseFollowupPlan(planJson: JSONArray?): List<PlannerFollowup> {
        if (planJson == null) return emptyList()
        
        val followups = mutableListOf<PlannerFollowup>()
        for (i in 0 until planJson.length()) {
            val item = planJson.getJSONObject(i)
            followups.add(
                PlannerFollowup(
                    expect = item.getString("expect"),
                    nextTool = item.optString("next_tool").takeIf { it.isNotBlank() }
                )
            )
        }
        return followups
    }

    private fun repairAndParseDecision(malformedJson: String): PlannerDecision? {
        // Simple repair attempts
        val cleaned = malformedJson
            .trim()
            .removePrefix("```json")
            .removeSuffix("```")
            .trim()

        return try {
            parseDecision(cleaned)
        } catch (e: Exception) {
            // If still fails, try to extract decision from text
            when {
                cleaned.contains("\"decision\":\"tool\"") -> {
                    PlannerDecision(
                        decision = "tool",
                        reason = "Extracted from malformed JSON",
                        tool = "DeviceContextTool", // Default fallback
                        action = "all"
                    )
                }
                else -> {
                    PlannerDecision(
                        decision = "respond",
                        reason = "Could not parse JSON, defaulting to respond"
                    )
                }
            }
        }
    }
}