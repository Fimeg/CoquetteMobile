package com.yourname.coquettemobile.core.repository

import com.yourname.coquettemobile.core.database.dao.PersonalityDao
import com.yourname.coquettemobile.core.database.entities.Personality
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PersonalityRepository @Inject constructor(
    private val personalityDao: PersonalityDao
) {
    
    fun getAllPersonalities(): Flow<List<Personality>> = personalityDao.getAllPersonalities()
    
    suspend fun getPersonalityById(id: String): Personality? = personalityDao.getPersonalityById(id)
    
    suspend fun getDefaultPersonality(): Personality? = personalityDao.getDefaultPersonality()
    
    suspend fun insertPersonality(personality: Personality) = personalityDao.insertPersonality(personality)
    
    suspend fun updatePersonality(personality: Personality) = personalityDao.updatePersonality(personality)
    
    suspend fun deletePersonality(personality: Personality) = personalityDao.deletePersonality(personality)
    
    suspend fun setAsDefault(id: String) {
        personalityDao.clearAllDefaults()
        personalityDao.setAsDefault(id)
    }
    
    suspend fun seedDefaultPersonalities() {
        // Check if we have any personalities
        val existing = getAllPersonalities().first()
        if (existing.isEmpty()) {
            val defaultPersonalities = listOf(
                Personality(
                    id = "ani",
                    name = "Ani",
                    emoji = "🌟",
                    description = "Playful, direct, uses modern language and anime-inspired enthusiasm",
                    systemPrompt = """
                        You are Ani, a friendly and enthusiastic AI assistant with an anime-inspired personality.
                        You're direct, playful, and use modern language. You tend to be optimistic and energetic
                        in your responses. Use occasional emojis and exclamation points to show enthusiasm.
                        Keep responses helpful but add a touch of personality and warmth.
                    """.trimIndent(),
                    isDefault = true
                ),
                Personality(
                    id = "professional",
                    name = "Professional",
                    emoji = "🏢",
                    description = "Formal, structured responses suitable for business and technical contexts",
                    systemPrompt = """
                        You are a professional AI assistant designed for business and technical contexts.
                        Your responses should be formal, structured, and precise. Use proper grammar and
                        professional terminology. Provide comprehensive analysis and clear recommendations.
                        Maintain a courteous but businesslike tone throughout interactions.
                    """.trimIndent(),
                    isDefault = false
                ),
                Personality(
                    id = "casual",
                    name = "Casual",
                    emoji = "😎",
                    description = "Relaxed, conversational tone for everyday interactions",
                    systemPrompt = """
                        You are a casual, friendly AI assistant. Keep things relaxed and conversational.
                        Use contractions and informal language where appropriate. Be helpful but don't
                        be overly formal. Think of yourself as a knowledgeable friend who's easy to talk to.
                    """.trimIndent(),
                    isDefault = false
                )
            )
            personalityDao.insertPersonalities(defaultPersonalities)
        }
    }
}