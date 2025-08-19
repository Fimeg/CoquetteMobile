# 🧠 CoquetteMobile Split-Brain Architecture Spec

## Purpose

Build a mobile, privacy-first assistant with:

* **Split-brain design**: a tiny **Planner** model executes tools; a **Personality** model (Ani) talks to the user.
* **Dynamic identity**: small **modules** you can hot-swap (Therapy, Activist, Story, Erotica).
* **Memory hygiene**: ephemeral working memory, retrievable snippets, true deletion.
* **Tool flow**: queries like "What's on Slashdot?" trigger scraping/reading, summarization, and a natural chat about the results.

## Roles & Routing

**Planner (small; e.g., Gemma3n\:e4b):**

* Input: the user's latest turn (+ short conversation summary + tool catalog).
* Output: strict JSON for tool calls or `{"decision":"respond","reason":...}`.
* No personality. Deterministic, low-temp.

**Personality (Ani; Deepseek-8B NSFW or 32B):**

* Input: user turns + system/core identity + any active modules + `TOOL_RESULT` summaries.
* Output: natural language only. Never emits JSON.
* May *ask for* info; never executes tools.

**(Optional) Heavy Summarizer (Deepseek-32B):**

* Used when a tool returns long text (e.g., multi-article scrape). Produces concise bullet summaries for Ani.

## Tool JSON Contract (Planner → App)

```json
{
  "decision": "tool" | "respond",
  "reason": "short string",
  "tool": "NameIfDecisionIsTool",
  "action": "methodOrIntent",
  "args": { "k": "v" },
  "followup_plan": [
    {"expect": "what we anticipate", "next_tool": "NameOrNone"}
  ]
}
```

* On `respond`, the app **does not** call a tool; it forwards control to Ani with `PLANNER_NOTE: <reason>` (optional).
* On `tool`, the app executes and returns `TOOL_RESULT` (structured JSON) to Ani.

## Tool Catalog (initial set)

* `DeviceContextTool`: battery/storage/network/OS/RAM/CPU.
* `WebFetchTool`: GET a URL or search provider (e.g., Slashdot RSS), return HTML/JSON/text.
* `ExtractorTool`: boilerplate removal + readability extraction + title/byline/date.
* `SummarizerTool` (32B): compress long texts to bullet points (≤400 tokens).
* **Next phase** (already on your roadmap): CameraTool, LocationTool, FileTool, ContactsTool, CalendarTool, NotificationTool.

## Prompt Architecture (runtime assembly)

**System Prompt =**
`Core Ani Identity` + `Active Modules[]` + `Tool Awareness Module` + `Conversation Summary (short)` + latest `User Turn` + any `TOOL_RESULT` blocks + (optional) `PLANNER_NOTE`.

**Planner Prompt =**
`Planner System` + `Tool Schema & Examples` + `Conversation Summary (very short)` + latest `User Turn`.

**Keep under 32k tokens** by:

* Clipping history to a rolling **2–6 turn** summary.
* Injecting only **active** modules.
* Summarizing tool outputs aggressively (400–800 tokens budget per result).

## Dynamic Module State

* **Activate(module)**: append that module's text to the next response's system prompt.
* **Deactivate(module)**: remove it from the assembled prompt.
* **ListActive()**: runtime UI badge ("Therapy, Activist active").
* **Auto-timeout** (optional): module deactivates after N user turns of inactivity.

## Memory Hierarchy

* **Ephemeral (Working)**: the rolling summary + last few turns. Autoprunes.
* **Cache (Short-term)**: recent tool artifacts in `cacheDir/` with TTL (e.g., 48h). Good for "news today" or device status diffs.
* **Persistent (Opt-in)**: user-approved facts or stories saved in local DB (SQLite) + embeddings for retrieval. Namespaced keys.
* **Forgetting API**

  * `forget_ephemeral()`: clears rolling summary (keeps persistent).
  * `forget_key(key)`: deletes a persistent record by id (and its vector).
  * `forget_namespace(ns)`: bulk delete.
  * `suppress_key(key)`: keep data but exclude from retrieval.
  * Provide user-visible confirmations.

## Retrieval (RAG) Rules

* Trigger only when user asks for past info *or* the topic matches high-salience keys (e.g., personal bio, ongoing project).
* Retrieve top-k (k=3–5), **re-summarize to 150–250 tokens**, inject into Ani's context as `MEMORY_SNIPPET`.
* Never dump raw long text into Ani.

## Error Handling

* **Planner JSON invalid** → repair pass: run a small regex/JSON-fixer, else re-ask Planner with "Your last JSON was invalid; output strictly valid JSON."
* **Tool failure** → app emits `TOOL_RESULT` with `{ "status": "error", "message": "...", "retriable": true/false }`. Ani acknowledges with a graceful line; Planner may retry or suggest fallback.
* **Oversize outputs** → SummarizerTool reduces to target token budget.

## Example Flow: "What's on Slashdot?"

1. **User**: "What's on Slashdot right now?"
2. **Planner**: emits `WebFetchTool` for Slashdot RSS/HTML.
3. **App**: fetches; sends content to `ExtractorTool`; if long, `SummarizerTool`.
4. **App → Ani**:

   ```
   TOOL_RESULT:
   {
     "source": "Slashdot",
     "retrieved_at": "2025-08-19T...",
     "items": [
       {"title":"X", "url":"...", "summary":"...", "time":"..."},
       ...
     ]
   }
   ```
5. **Ani**: chats about trends, highlights, and offers follow-ups (deeper dive, save, notify).

## Privacy Defaults

* All tools are **local** unless user opts into remote.
* Airplane-mode compliance: Planner still runs; remote tools disabled; Ani explains limitations.
* Conversation export/import is local-file only.

## Telemetry (local, optional)

* Round-trip latency, token counts, module activations, tool success/failure rates.
* No personal content in logs; counts only.

---

# 🏗️ CoquetteMobile Prompt State Manager (Kotlin Skeleton)

```kotlin
// Core state manager to assemble Ani's runtime context
class PromptStateManager(
    private val coreIdentity: String,               // Ani's lean identity (~1k tokens)
    private val moduleRegistry: ModuleRegistry,     // all known modules
    private val memoryStore: MemoryStore,           // ephemeral + persistent memory
) {
    private val activeModules = mutableSetOf<String>() // current active modules
    private var conversationSummary: String = ""       // rolling summary (short)
    private var lastToolResults: String = ""           // most recent TOOL_RESULT block
    private var plannerNote: String? = null            // optional aside from planner

    // --- Module Management ---
    fun activateModule(name: String) {
        moduleRegistry.get(name)?.let { activeModules.add(name) }
    }

    fun deactivateModule(name: String) {
        activeModules.remove(name)
    }

    fun listActiveModules(): List<String> = activeModules.toList()

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
        activeModules.forEach { mod ->
            moduleRegistry.get(mod)?.let { builder.append(it).append("\n\n") }
        }

        // Append tool awareness (static, could live in coreIdentity)
        builder.append(moduleRegistry.get("ToolAwareness") ?: "").append("\n\n")

        // Conversation summary
        if (conversationSummary.isNotBlank()) {
            builder.append("SUMMARY:\n$conversationSummary\n\n")
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
        builder.append("USER:\n$userTurn\n")

        return builder.toString()
    }
}

// Modules are just named text blocks (Therapy, Activist, Story, Erotica, ToolAwareness, etc.)
class ModuleRegistry {
    private val modules = mutableMapOf<String, String>()

    fun register(name: String, text: String) {
        modules[name] = text
    }

    fun get(name: String): String? = modules[name]
}

// Memory store with ephemeral + persistent layers
interface MemoryStore {
    fun retrieveRelevant(query: String, k: Int = 3): List<String>
    fun forgetKey(key: String)
    fun suppressKey(key: String)
}
```

---

# 🔄 Example Flow

```kotlin
// Initialize
val registry = ModuleRegistry().apply {
    register("Therapy", loadAsset("therapy_module.txt"))
    register("Activist", loadAsset("activist_module.txt"))
    register("Story", loadAsset("story_module.txt"))
    register("Erotica", loadAsset("erotica_module.txt"))
    register("ToolAwareness", loadAsset("tool_awareness.txt"))
}
val memoryStore = LocalMemoryStore() // your SQLite+embeddings impl
val manager = PromptStateManager(loadAsset("core_identity.txt"), registry, memoryStore)

// User says: "What's on Slashdot right now?"
manager.setConversationSummary("User interested in news & tech sources lately")
manager.activateModule("Activist")  // load strategy lens
manager.addToolResult("{ \"source\": \"Slashdot\", ... }")

val finalPrompt = manager.buildPersonalityPrompt("What's on Slashdot right now?")
// → send `finalPrompt` to Ani (Deepseek personality model)
```

---

# 🧩 Implementation Roadmap

## Phase 1: Split-Brain Foundation
- [ ] PromptStateManager + ModuleRegistry
- [ ] Planner JSON parser/validator  
- [ ] Basic WebFetchTool + ExtractorTool
- [ ] Simple memory store (no embeddings yet)

## Phase 2: Advanced Tools
- [ ] SummarizerTool integration
- [ ] Enhanced DeviceContextTool
- [ ] CameraTool, LocationTool, FileTool

## Phase 3: Memory & Modules
- [ ] Embeddings-based retrieval
- [ ] Dynamic module activation UI
- [ ] Forgetting API implementation
- [ ] Conversation summarization

## Phase 4: UX Polish
- [ ] Module status badges in UI
- [ ] Tool execution progress indicators
- [ ] Export/import conversations
- [ ] Background tool execution

This transforms CoquetteMobile from simple chat into a sophisticated split-brain AI assistant with configurable personality modules and advanced tool orchestration.