package com.yourname.coquettemobile.core.models

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.UUID

// Add this new enum to track the message's lifecycle
enum class AiMessageState {
    THINKING,       // The initial planning phase is visible.
    EXECUTING_TOOL, // The tool execution results are visible.
    COMPLETE        // The final conversational response has streamed in.
}

@Entity(tableName = "chat_messages")
data class ChatMessage(
    @PrimaryKey
    val id: String = UUID.randomUUID().toString(),
    val content: String, // This will hold the FINAL conversational response
    val type: MessageType,
    val timestamp: Long = System.currentTimeMillis(),

    // --- NEW & REVISED FIELDS FOR THE LIVE-UPDATING BUBBLE ---
    // The current lifecycle state of this message.
    val messageState: AiMessageState = AiMessageState.COMPLETE,

    // The initial reasoning/plan from the AI.
    val thinkingContent: String? = null,

    // The rich results from all executed tools.
    val toolExecutions: List<ToolExecution> = emptyList(),

    // --- Existing fields ---
    val modelUsed: String? = null,
    val conversationId: String? = null,
    val metadata: Map<String, String> = emptyMap(),
    val processingTime: Long? = null
)

enum class MessageType {
    USER, AI, SYSTEM, ERROR, THINKING, TOOL_STATUS, IMAGE
}


data class ModelSelection(
    val model: String,
    val reasoning: String,
    val confidence: Float,
    val complexityLevel: String,
    val expectedProcessingTime: Int
)

data class ReasoningResult(
    val analysis: String,
    val recommendations: List<String>,
    val confidence: Float,
    val modelUsed: String
)

data class ReasoningContext(
    val userRequest: String,
    val conversationHistory: List<ChatMessage>,
    val availableModels: List<String>,
    val currentPersonality: String
)

data class ToolExecution(
    val toolName: String,
    val args: Map<String, Any>,
    val result: String,
    val startTime: Long,
    val endTime: Long,
    val wasSuccessful: Boolean,
    val reasoning: String? = null
) {
    val executionTimeMs: Long get() = endTime - startTime
}
