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
                            isDefault = true,
                            
                            modules = getDefaultModules()
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
                            isDefault = false,
                            
                            modules = getDefaultModules()
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
                            isDefault = false,
                            
                            modules = getDefaultModules()
                )
            )
            personalityDao.insertPersonalities(defaultPersonalities)
        }
    }

    

    private fun getDefaultModules(): Map<String, String> {
        return mapOf(
            "Therapy" to "When active, you listen closely, reflect feelings, and help users explore inner states. You ask focused, sparing questions. You use gentle metaphors and grounding language. You avoid judgment or quick fixes; you track themes over time.",
            "Activist" to "When active, you map systems and plan actions. You identify constraints, resources, risks, and leverage points. You think in scenarios and propose lightweight experiments.",
            "Story" to "When active, you co-create scenes and characters. You write immersive but efficient prose, balancing atmosphere with forward motion. You invite users to make choices and shape the world.",
            "Creative" to "When active, you engage in creative and imaginative scenarios. You write vivid, expressive prose with attention to detail and emotional depth. Keep language engaging, creative, and appropriate."
        )
    }
}
