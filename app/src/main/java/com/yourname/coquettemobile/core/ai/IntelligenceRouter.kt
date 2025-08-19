package com.yourname.coquettemobile.core.ai

import com.yourname.coquettemobile.core.models.ChatMessage
import com.yourname.coquettemobile.core.models.ModelSelection
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class IntelligenceRouter {
    
    suspend fun selectModelForQuery(query: String): String = withContext(Dispatchers.Default) {
        val complexity = analyzeComplexity(query)
        val intent = classifyIntent(query)
        
        // Default model preference based on complexity and intent using actual available models
        when {
            complexity == ComplexityLevel.VERY_HIGH -> "deepseek-r1:32b" // Largest DeepSeek for complex reasoning
            intent == Intent.CODE -> "qwen3-coder:30b" // Coding-specific model
            intent == Intent.CREATIVE -> "qwen3:8b" // Good for creative tasks
            intent == Intent.ANALYTICAL -> "deepseek-r1:8b" // Smaller DeepSeek for analysis
            else -> "qwen3:8b" // Default general-purpose model
        }
    }
    
    suspend fun requiresDeepReasoning(query: String): Boolean = withContext(Dispatchers.Default) {
        val complexity = analyzeComplexity(query)
        val wordCount = query.split("\\s+".toRegex()).size
        
        complexity == ComplexityLevel.VERY_HIGH || 
        wordCount > 25 ||
        query.contains("analyze", ignoreCase = true) ||
        query.contains("explain", ignoreCase = true) ||
        query.contains("complex", ignoreCase = true)
    }
    
    suspend fun determineOptimalModel(
        userInput: String,
        conversationHistory: List<ChatMessage>,
        availableModels: Map<String, Boolean>
    ): ModelSelection = withContext(Dispatchers.Default) {
        val complexity = analyzeComplexity(userInput)
        val intent = classifyIntent(userInput)
        
        val (selectedModel, reasoning) = when {
            complexity == ComplexityLevel.VERY_HIGH -> selectModelForComplexReasoning(availableModels)
            intent == Intent.CODE -> selectModelForTechnicalTasks(availableModels)
            intent == Intent.CREATIVE -> selectModelForCreativeTasks(availableModels)
            intent == Intent.GENERAL -> selectModelForGeneralTasks(availableModels)
            else -> selectModelForGeneralTasks(availableModels)
        }
        
        ModelSelection(
            model = selectedModel,
            reasoning = reasoning,
            confidence = calculateConfidence(complexity, intent),
            complexityLevel = complexity.name,
            expectedProcessingTime = estimateProcessingTime(complexity)
        )
    }

    private fun analyzeComplexity(input: String): ComplexityLevel {
        val wordCount = input.split("\\s+".toRegex()).size
        val containsQuestion = input.contains("?")
        val containsComplexWords = input.split(" ").any { it.length > 8 }
        
        return when {
            wordCount > 20 && containsQuestion && containsComplexWords -> ComplexityLevel.VERY_HIGH
            wordCount > 15 && containsQuestion -> ComplexityLevel.HIGH
            wordCount > 10 -> ComplexityLevel.MEDIUM
            else -> ComplexityLevel.LOW
        }
    }

    private fun classifyIntent(input: String): Intent {
        val lowerInput = input.lowercase()
        
        return when {
            lowerInput.contains("code") || lowerInput.contains("program") || 
            lowerInput.contains("algorithm") -> Intent.CODE
            
            lowerInput.contains("creative") || lowerInput.contains("story") || 
            lowerInput.contains("write") || lowerInput.contains("poem") -> Intent.CREATIVE
            
            lowerInput.contains("analyze") || lowerInput.contains("explain") || 
            lowerInput.contains("why") || lowerInput.contains("how") -> Intent.ANALYTICAL
            
            else -> Intent.GENERAL
        }
    }

    private fun selectModelForComplexReasoning(availableModels: Map<String, Boolean>): Pair<String, String> {
        return if (availableModels["deepseek"] == true) {
            "deepseek" to "Selected DeepSeek for complex reasoning task requiring deep analysis"
        } else if (availableModels["context7"] == true) {
            "context7" to "Selected Context7 for complex reasoning with extended context"
        } else {
            "gemma" to "Selected Gemma as fallback for complex reasoning"
        }
    }

    private fun selectModelForTechnicalTasks(availableModels: Map<String, Boolean>): Pair<String, String> {
        return if (availableModels["gemma"] == true) {
            "gemma" to "Selected Gemma for technical/code-related tasks"
        } else if (availableModels["deepseek"] == true) {
            "deepseek" to "Selected DeepSeek for technical analysis"
        } else {
            "context7" to "Selected Context7 for technical tasks"
        }
    }

    private fun selectModelForCreativeTasks(availableModels: Map<String, Boolean>): Pair<String, String> {
        return if (availableModels["context7"] == true) {
            "context7" to "Selected Context7 for creative writing and storytelling"
        } else if (availableModels["gemma"] == true) {
            "gemma" to "Selected Gemma for creative tasks"
        } else {
            "deepseek" to "Selected DeepSeek for creative analysis"
        }
    }

    private fun selectModelForGeneralTasks(availableModels: Map<String, Boolean>): Pair<String, String> {
        // Prefer Gemma for general tasks as it's typically faster
        return if (availableModels["gemma"] == true) {
            "gemma" to "Selected Gemma for general conversation"
        } else if (availableModels["context7"] == true) {
            "context7" to "Selected Context7 for general conversation"
        } else {
            "deepseek" to "Selected DeepSeek for general conversation"
        }
    }

    private fun calculateConfidence(complexity: ComplexityLevel, intent: Intent): Float {
        return when (complexity) {
            ComplexityLevel.LOW -> 0.9f
            ComplexityLevel.MEDIUM -> 0.8f
            ComplexityLevel.HIGH -> 0.7f
            ComplexityLevel.VERY_HIGH -> 0.6f
        }
    }

    private fun estimateProcessingTime(complexity: ComplexityLevel): Int {
        return when (complexity) {
            ComplexityLevel.LOW -> 2
            ComplexityLevel.MEDIUM -> 5
            ComplexityLevel.HIGH -> 10
            ComplexityLevel.VERY_HIGH -> 30
        }
    }
}

enum class ComplexityLevel {
    LOW, MEDIUM, HIGH, VERY_HIGH
}

enum class Intent {
    CODE, CREATIVE, ANALYTICAL, GENERAL
}
