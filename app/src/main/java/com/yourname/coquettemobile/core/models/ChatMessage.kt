package com.yourname.coquettemobile.core.models

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.yourname.coquettemobile.core.orchestration.ExecutionPlan
import com.yourname.coquettemobile.core.orchestration.IntentAnalysis
import kotlinx.serialization.Contextual
import kotlinx.serialization.Serializable
import java.util.UUID

// Enhanced enum to track detailed message lifecycle
enum class AiMessageState(val level: Int) {
    THINKING(0),           // Initial reasoning phase
    ANALYZING_INTENT(1),   // Intent classification
    PLANNING_TOOLS(2),     // Tool selection phase
    EXECUTING_TOOL(3),     // Individual tool execution
    GENERATING_RESPONSE(4),// Final response creation
    COMPLETE(5)           // Done
}

// Represents the hierarchical, phased approach to an AI operation.
// This is the data structure for the "Evolving Thought Bubble".
@Serializable
sealed class OrchestrationPhase {
    abstract val thinking: List<String>

    @Serializable
    data class IntentAnalysisPhase(
        override val thinking: List<String>,
        val analysis: IntentAnalysis
    ) : OrchestrationPhase()

    @Serializable
    data class PlanningPhase(
        override val thinking: List<String>,
        val plan: ExecutionPlan
    ) : OrchestrationPhase()

    @Serializable
    data class ExecutionPhase(
        override val thinking: List<String>,
        val toolExecutions: List<ToolExecution>
    ) : OrchestrationPhase()

    @Serializable
    data class SynthesisPhase(
        override val thinking: List<String>,
        val response: String
    ) : OrchestrationPhase()
}


@Entity(tableName = "chat_messages")
data class ChatMessage(
    @PrimaryKey
    val id: String = UUID.randomUUID().toString(),
    val content: String, // This will hold the FINAL conversational response
    val type: MessageType,
    val timestamp: Long = System.currentTimeMillis(),

    // The current lifecycle state of this message.
    val messageState: AiMessageState = AiMessageState.COMPLETE,

    // The new hierarchical list of all operation phases.
    val orchestrationPhases: List<OrchestrationPhase> = emptyList(),

    // --- Existing fields ---
    val modelUsed: String? = null,
    val conversationId: String? = null,
    val metadata: Map<String, String> = emptyMap(),
    val imageUrl: String? = null,
    val processingTime: Long? = null
)

enum class MessageType {
    USER, AI, SYSTEM, ERROR, THINKING, TOOL_STATUS, IMAGE
}


@Serializable
data class ModelSelection(
    val model: String,
    val reasoning: String,
    val confidence: Float,
    val complexityLevel: String,
    val expectedProcessingTime: Int
)

@Serializable
data class ReasoningResult(
    val analysis: String,
    val recommendations: List<String>,
    val confidence: Float,
    val modelUsed: String
)

@Serializable
data class ReasoningContext(
    val userRequest: String,
    val conversationHistory: List<@Contextual ChatMessage>,
    val availableModels: List<String>,
    val currentPersonality: String
)

@Serializable
data class ToolExecution(
    val toolName: String,
    val args: Map<String, String>,
    val result: String,
    val startTime: Long,
    val endTime: Long,
    val wasSuccessful: Boolean,
    val reasoning: String? = null,
    val imageUrl: String? = null // New field for image URLs in tool executions
) {
    val executionTimeMs: Long get() = endTime - startTime
}
