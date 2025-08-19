package com.yourname.coquettemobile.core.ai

import com.yourname.coquettemobile.core.repository.PersonalityRepository
import javax.inject.Inject

class PersonalityProvider @Inject constructor(
    private val personalityRepository: PersonalityRepository
) {
    
    suspend fun getSystemPrompt(personalityId: String): String {
        val personality = personalityRepository.getPersonalityById(personalityId)
        return personality?.systemPrompt ?: getDefaultSystemPrompt()
    }
    
    suspend fun getDefaultPersonality(): com.yourname.coquettemobile.core.database.entities.Personality? {
        return personalityRepository.getDefaultPersonality()
    }
    
    private fun getDefaultSystemPrompt(): String {
        return """
            You are a helpful AI assistant. Please provide clear, accurate, and helpful responses
            to the user's questions and requests. Be friendly and professional in your interactions.
        """.trimIndent()
    }
    
    fun applyPersonalityFilter(response: String, personality: String): String {
        return when (personality.lowercase()) {
            "ani" -> applyAniPersonality(response)
            "professional" -> applyProfessionalPersonality(response)
            "casual" -> applyCasualPersonality(response)
            else -> response // Default to original response
        }
    }
    
    private fun applyAniPersonality(response: String): String {
        var processed = response
        
        // Add playful elements
        if (!processed.contains("✨") && processed.length > 50) {
            processed = "✨ $processed ✨"
        }
        
        // Add some modern slang and enthusiasm
        processed = processed.replace("I think", "I'm pretty sure")
            .replace("you should", "you might wanna")
            .replace("recommend", "suggest")
            .replace("therefore", "so like")
        
        // Add some anime-style enthusiasm
        if (processed.endsWith(".") && !processed.endsWith("!")) {
            processed = processed.dropLast(1) + "!"
        }
        
        return processed
    }
    
    private fun applyProfessionalPersonality(response: String): String {
        var processed = response
        
        // Make it more formal and structured
        processed = processed.replace("I'm", "I am")
            .replace("you're", "you are")
            .replace("don't", "do not")
            .replace("can't", "cannot")
            .replace("won't", "will not")
        
        // Add professional framing
        if (!processed.startsWith("Based on")) {
            processed = "Based on the analysis, $processed".replaceFirstChar { it.lowercase() }
        }
        
        // Ensure proper punctuation
        if (!processed.endsWith(".") && !processed.endsWith("!") && !processed.endsWith("?")) {
            processed += "."
        }
        
        return processed
    }
    
    private fun applyCasualPersonality(response: String): String {
        var processed = response
        
        // Make it more conversational
        processed = processed.replace("therefore", "so")
            .replace("however", "but")
            .replace("additionally", "also")
            .replace("consequently", "so")
        
        // Add casual contractions
        processed = processed.replace("I am", "I'm")
            .replace("you are", "you're")
            .replace("do not", "don't")
            .replace("cannot", "can't")
            .replace("will not", "won't")
        
        // Remove overly formal language
        processed = processed.replace("utilize", "use")
            .replace("facilitate", "help")
            .replace("implement", "set up")
        
        return processed
    }
    
    fun getAvailablePersonalities(): List<String> {
        return listOf("Ani", "Professional", "Casual")
    }
    
    fun getPersonalityDescription(personality: String): String {
        return when (personality.lowercase()) {
            "ani" -> "Playful, direct, uses modern language and anime-inspired enthusiasm"
            "professional" -> "Formal, structured responses suitable for business and technical contexts"
            "casual" -> "Relaxed, conversational tone for everyday interactions"
            else -> "Default personality - balanced and neutral"
        }
    }
}
