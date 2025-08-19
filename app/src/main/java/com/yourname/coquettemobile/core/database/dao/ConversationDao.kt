package com.yourname.coquettemobile.core.database.dao

import androidx.room.*
import com.yourname.coquettemobile.core.database.entities.Conversation
import kotlinx.coroutines.flow.Flow

@Dao
interface ConversationDao {
    @Query("SELECT * FROM conversations ORDER BY updatedAt DESC")
    fun getAllConversations(): Flow<List<Conversation>>
    
    @Query("SELECT * FROM conversations WHERE isActive = 1 LIMIT 1")
    suspend fun getActiveConversation(): Conversation?
    
    @Query("SELECT * FROM conversations WHERE id = :id")
    suspend fun getConversationById(id: String): Conversation?
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertConversation(conversation: Conversation)
    
    @Update
    suspend fun updateConversation(conversation: Conversation)
    
    @Delete
    suspend fun deleteConversation(conversation: Conversation)
    
    @Query("UPDATE conversations SET isActive = 0")
    suspend fun deactivateAllConversations()
    
    @Query("UPDATE conversations SET isActive = 1 WHERE id = :id")
    suspend fun setActiveConversation(id: String)
    
    @Query("UPDATE conversations SET messageCount = messageCount + 1, updatedAt = :timestamp WHERE id = :conversationId")
    suspend fun incrementMessageCount(conversationId: String, timestamp: Long = System.currentTimeMillis())
}