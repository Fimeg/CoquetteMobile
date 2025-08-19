package com.yourname.coquettemobile.core.models

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.UUID

@Entity(tableName = "chat_messages")
data class ChatMessage(
    @PrimaryKey
    val id: String = UUID.randomUUID().toString(),
    val content: String,
    val type: MessageType,
    val timestamp: Long = System.currentTimeMillis(),
    val modelUsed: String? = null,
    val processingTime: Long? = null,
    val thinkingContent: String? = null,
    val conversationId: String? = null,
    val metadata: Map<String, String> = emptyMap()
)

enum class MessageType {
    USER, AI, SYSTEM, ERROR
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
