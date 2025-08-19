package com.yourname.coquettemobile.core.database.dao

import androidx.room.*
import com.yourname.coquettemobile.core.models.ChatMessage
import kotlinx.coroutines.flow.Flow

@Dao 
interface ChatMessageDao {
    @Query("SELECT * FROM chat_messages WHERE conversationId = :conversationId ORDER BY timestamp ASC")
    fun getMessagesByConversation(conversationId: String): Flow<List<ChatMessage>>
    
    @Query("SELECT * FROM chat_messages WHERE conversationId = :conversationId ORDER BY timestamp ASC LIMIT :limit")
    suspend fun getRecentMessages(conversationId: String, limit: Int = 10): List<ChatMessage>
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessage(message: ChatMessage)
    
    @Query("DELETE FROM chat_messages WHERE conversationId = :conversationId")
    suspend fun deleteMessagesByConversation(conversationId: String)
    
    @Delete
    suspend fun deleteMessage(message: ChatMessage)
}