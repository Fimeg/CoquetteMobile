package com.yourname.coquettemobile.core.ai

import com.yourname.coquettemobile.core.models.ChatMessage
import com.yourname.coquettemobile.core.models.MessageType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ConversationTitleGenerator @Inject constructor(
    private val ollamaService: OllamaService
) {
    
    suspend fun generateTitle(messages: List<ChatMessage>): String = withContext(Dispatchers.IO) {
        try {
            // Get the first few messages to understand the conversation topic
            val contextMessages = messages.take(6).filter { 
                it.type == MessageType.USER || it.type == MessageType.AI 
            }
            
            if (contextMessages.isEmpty()) {
                return@withContext "New Chat"
            }
            
            // Build conversation context for title generation
            val conversationPreview = contextMessages.joinToString("\n") { message ->
                when (message.type) {
                    MessageType.USER -> "User: ${message.content.take(100)}"
                    MessageType.AI -> "AI: ${message.content.take(100)}"
                    else -> ""
                }
            }.trim()
            
            val titlePrompt = """
                Generate a short, descriptive title (3-6 words) for this conversation based on the main topic discussed. The title should be clear and helpful for identifying this conversation later.
                
                Conversation preview:
                $conversationPreview
                
                Return only the title, nothing else. Examples of good titles:
                - "Python Code Help"
                - "Travel Planning Discussion" 
                - "Recipe Ideas Chat"
                - "Math Problem Solving"
            """.trimIndent()
            
            // Use a fast, lightweight model for title generation (preferably gemma3:2b if available)
            val modelToUse = selectTitleGenerationModel()
            
            // Use smaller context for title generation since it's unimportant
            val options = if (modelToUse.contains("gemma3n:e4b")) {
                mapOf(
                    "num_ctx" to 1024,  // Small context for gemma3n:e4b
                    "temperature" to 0.3
                )
            } else {
                mapOf("temperature" to 0.3)
            }
            
            val response = ollamaService.generateResponse(
                model = modelToUse,
                prompt = "You are a helpful assistant that creates short, descriptive titles for conversations. Keep titles under 6 words and focus on the main topic.\n\n$titlePrompt",
                options = options
            )
            
            val responseContent = if (response.isSuccess) {
                response.getOrThrow()
            } else {
                throw Exception("Failed to generate title")
            }
            
            // Clean up the response and ensure it's a reasonable title
            val title = responseContent
                .trim()
                .replace("\"", "")
                .take(50) // Max 50 characters
                .ifBlank { "New Chat" }
            
            return@withContext title
            
        } catch (e: Exception) {
            // Fallback to timestamp-based title
            val timestamp = if (messages.isNotEmpty()) {
                java.text.SimpleDateFormat("MMM dd, HH:mm", java.util.Locale.getDefault())
                    .format(java.util.Date(messages.first().timestamp))
            } else {
                java.text.SimpleDateFormat("MMM dd, HH:mm", java.util.Locale.getDefault())
                    .format(java.util.Date())
            }
            return@withContext "Chat $timestamp"
        }
    }
    
    private suspend fun selectTitleGenerationModel(): String {
        return try {
            val availableModels = ollamaService.getAvailableModels()
            
            // Prefer gemma3n:e4b for title generation (lightweight, smart)
            when {
                availableModels.any { it.contains("gemma3n:e4b") } -> "gemma3n:e4b"
                availableModels.any { it.contains("qwen3:8b") } -> "qwen3:8b"
                availableModels.isNotEmpty() -> availableModels.first()
                else -> "qwen3:8b" // fallback
            }
        } catch (e: Exception) {
            "qwen3:8b" // fallback model
        }
    }
}