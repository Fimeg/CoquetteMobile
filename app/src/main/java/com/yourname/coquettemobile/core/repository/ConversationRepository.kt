package com.yourname.coquettemobile.core.repository

import com.yourname.coquettemobile.core.database.dao.ConversationDao
import com.yourname.coquettemobile.core.database.dao.ChatMessageDao
import com.yourname.coquettemobile.core.database.entities.Conversation
import com.yourname.coquettemobile.core.models.ChatMessage
import com.yourname.coquettemobile.core.ai.ConversationTitleGenerator
import kotlinx.coroutines.flow.Flow
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ConversationRepository @Inject constructor(
    private val conversationDao: ConversationDao,
    private val chatMessageDao: ChatMessageDao,
    private val titleGenerator: ConversationTitleGenerator
) {
    
    fun getAllConversations(): Flow<List<Conversation>> {
        return conversationDao.getAllConversations()
    }
    
    suspend fun getActiveConversation(): Conversation? {
        return conversationDao.getActiveConversation()
    }
    
    suspend fun createNewConversation(): Conversation {
        // Deactivate all existing conversations
        conversationDao.deactivateAllConversations()
        
        val conversation = Conversation(
            id = UUID.randomUUID().toString(),
            title = "New Chat",
            createdAt = System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis(),
            messageCount = 0,
            isActive = true
        )
        
        conversationDao.insertConversation(conversation)
        return conversation
    }
    
    suspend fun switchToConversation(conversationId: String): Conversation? {
        conversationDao.deactivateAllConversations()
        conversationDao.setActiveConversation(conversationId)
        return conversationDao.getConversationById(conversationId)
    }
    
    suspend fun saveMessage(message: ChatMessage, conversationId: String) {
        val messageWithConversation = message.copy(conversationId = conversationId)
        chatMessageDao.insertMessage(messageWithConversation)
        conversationDao.incrementMessageCount(conversationId)
    }
    
    fun getConversationMessages(conversationId: String): Flow<List<ChatMessage>> {
        return chatMessageDao.getMessagesByConversation(conversationId)
    }
    
    suspend fun getRecentMessages(conversationId: String, limit: Int = 10): List<ChatMessage> {
        return chatMessageDao.getRecentMessages(conversationId, limit)
    }
    
    suspend fun deleteConversation(conversation: Conversation) {
        chatMessageDao.deleteMessagesByConversation(conversation.id)
        conversationDao.deleteConversation(conversation)
    }
    
    suspend fun generateAndUpdateTitle(conversationId: String) {
        try {
            val messages = chatMessageDao.getRecentMessages(conversationId, 6)
            if (messages.size >= 2) { // At least one exchange
                val title = titleGenerator.generateTitle(messages.reversed()) // Reverse to get chronological order
                val conversation = conversationDao.getConversationById(conversationId)
                conversation?.let {
                    if (it.title == "New Chat") { // Only update if still default title
                        conversationDao.updateConversation(it.copy(title = title))
                    }
                }
            }
        } catch (e: Exception) {
            // Silently fail - title generation is not critical
        }
    }
}