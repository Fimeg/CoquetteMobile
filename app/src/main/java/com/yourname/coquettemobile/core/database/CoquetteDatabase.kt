package com.yourname.coquettemobile.core.database

import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import android.content.Context
import com.yourname.coquettemobile.core.database.dao.PersonalityDao
import com.yourname.coquettemobile.core.database.dao.ConversationDao
import com.yourname.coquettemobile.core.database.dao.ChatMessageDao
import com.yourname.coquettemobile.core.database.entities.Personality
import com.yourname.coquettemobile.core.database.entities.Conversation
import com.yourname.coquettemobile.core.models.ChatMessage

@Database(
    entities = [Personality::class, Conversation::class, ChatMessage::class],
    version = 2,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class CoquetteDatabase : RoomDatabase() {
    
    abstract fun personalityDao(): PersonalityDao
    abstract fun conversationDao(): ConversationDao
    abstract fun chatMessageDao(): ChatMessageDao
    
    companion object {
        @Volatile
        private var INSTANCE: CoquetteDatabase? = null
        
        fun getDatabase(context: Context): CoquetteDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    CoquetteDatabase::class.java,
                    "coquette_database"
                )
                .fallbackToDestructiveMigration() // For development - remove in production
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}