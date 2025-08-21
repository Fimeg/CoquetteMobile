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
            val response = ollamaService.generateResponse(
                model = plannerModel,
                prompt = "${getPlannerSystemPrompt()}\n\n$plannerPrompt",
                options = mapOf(
                    "temperature" to 0.1,
                    "num_ctx" to 1024,
                    "num_predict" to 512
                ),
                useToolServer = true
            )
            
            if (response.isFailure) {
                throw Exception(response.exceptionOrNull()?.message ?: "Planner request failed")
            }
            
            val rawResponse = response.getOrThrow()
            android.util.Log.d("PlannerService", "Raw planner response: $rawResponse")
            
            parseDecision(rawResponse)
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
You are the Planner. Your job is to decide whether to call tools or pass control to the Personality. Output valid JSON only, no extra text.

You have access to these tools:
- DeviceContextTool(action: "battery"|"storage"|"network"|"system"|"performance", args:{})
- WebFetchTool(args:{ url:string })
- ExtractorTool(args:{ html:string })
- SummarizerTool(args:{ text:string, format?:"bullets"|"paragraph"|"headlines", target_length?:number })

Rules:
- For complex requests, create multi-step tool chains
- Chain tools strategically: fetch → extract → summarize, or multiple fetches for comparison
- For research tasks, plan multiple sources and synthesis
- For analysis tasks, break down into discrete data gathering steps
- If no tools needed, return {"decision":"respond","reason":"..."}

Examples:

User: "Compare the latest news from TechCrunch and Ars Technica"
Output:
{
  "decision":"tool",
  "reason":"Multi-source news comparison requires fetching from both sites",
  "tool":"WebFetchTool",
  "action":"get",
  "args":{"url":"https://techcrunch.com/"},
  "followup_plan":[
    {"expect":"html","next_tool":"ExtractorTool"},
    {"expect":"readable_text","next_tool":"SummarizerTool"},
    {"expect":"summary","next_tool":"WebFetchTool","args":{"url":"https://arstechnica.com/"}},
    {"expect":"html","next_tool":"ExtractorTool"},
    {"expect":"readable_text","next_tool":"SummarizerTool"}
  ]
}

User: "Research the latest AI developments and create a comprehensive report"
Output:
{
  "decision":"tool",
  "reason":"Comprehensive research requires multiple sources and analysis",
  "tool":"WebFetchTool",
  "action":"get",
  "args":{"url":"https://arxiv.org/list/cs.AI/recent"},
  "followup_plan":[
    {"expect":"html","next_tool":"ExtractorTool"},
    {"expect":"readable_text","next_tool":"WebFetchTool","args":{"url":"https://openai.com/blog/"}},
    {"expect":"html","next_tool":"ExtractorTool"},
    {"expect":"readable_text","next_tool":"WebFetchTool","args":{"url":"https://ai.googleblog.com/"}},
    {"expect":"html","next_tool":"ExtractorTool"},
    {"expect":"readable_text","next_tool":"SummarizerTool","args":{"format":"bullets","target_length":10}}
  ]
}

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
{"decision":"respond","reason":"Creative writing task - no tools needed"}
        """.trimIndent()
    }

    private fun parseDecision(jsonResponse: String): PlannerDecision {
        return try {
            android.util.Log.d("PlannerService", "Parsing JSON: ${jsonResponse.trim()}")
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
            android.util.Log.e("PlannerService", "JSON parsing failed: ${e.message}")
            android.util.Log.e("PlannerService", "Failed JSON: $jsonResponse")
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
            // Direct JSON parsing without recursion
            val json = JSONObject(cleaned.trim())
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