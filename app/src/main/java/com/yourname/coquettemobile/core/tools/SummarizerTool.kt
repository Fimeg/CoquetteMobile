package com.yourname.coquettemobile.core.tools

import com.yourname.coquettemobile.core.ai.OllamaService
import com.yourname.coquettemobile.core.preferences.AppPreferences
import com.yourname.coquettemobile.core.logging.CoquetteLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject

/**
 * SummarizerTool - Intelligent content summarization
 * Uses deepseek-r1:32b for high-quality summaries
 */
class SummarizerTool @Inject constructor(
    private val ollamaService: OllamaService,
    private val appPreferences: AppPreferences,
    private val logger: CoquetteLogger
) : MobileTool {
    
    override val name = "SummarizerTool"
    override val description = "Create intelligent summaries of long text content"
    override val riskLevel = RiskLevel.LOW
    override val requiredPermissions = emptyList<String>()
    
    override suspend fun execute(params: Map<String, Any>): ToolResult = withContext(Dispatchers.IO) {
        executeStreaming(params) { /* ignore progress for sync execution */ }
    }

    override suspend fun executeStreaming(
        params: Map<String, Any>,
        onProgress: (String) -> Unit
    ): ToolResult = withContext(Dispatchers.IO) {
        val text = params["text"] as? String
            ?: return@withContext ToolResult.error("Missing required parameter: text")
            
        val format = params["format"] as? String ?: "bullets"
        val targetLength = params["target_length"] as? Int ?: 5
        
        try {
            onProgress("Starting summarization of ${text.length} characters...")
            logger.d("SummarizerTool", "Starting summarization: ${text.length} chars, format=$format, length=$targetLength")
            
            onProgress("Building summary prompt...")
            val summaryPrompt = buildSummaryPrompt(text, format, targetLength)
            
            // Use user's selected personality model for summarization
            val summaryModel = appPreferences.toolOllamaModel
            logger.d("SummarizerTool", "Using tool model: $summaryModel")
            onProgress("Using model: $summaryModel for summarization")
            
            onProgress("Generating summary...")
            val response = ollamaService.generateResponse(
                model = summaryModel,
                prompt = summaryPrompt,
                options = mapOf(
                    "temperature" to 0.3,
                    "num_ctx" to 16384,
                    "num_predict" to 1000
                )
            )
            
            if (response.isFailure) {
                val error = "Summarization failed: ${response.exceptionOrNull()?.message}"
                logger.e("SummarizerTool", error)
                onProgress(error)
                return@withContext ToolResult.error(error)
            }
            
            val summary = response.getOrThrow().trim()
            logger.d("SummarizerTool", "Summarization completed: ${summary.length} chars output")
            onProgress("Summary complete: ${summary.length} characters generated")
            
            ToolResult.success(
                output = summary,
                metadata = mapOf(
                    "original_length" to text.length,
                    "summary_length" to summary.length,
                    "compression_ratio" to String.format("%.1f", text.length.toFloat() / summary.length),
                    "format" to format,
                    "model_used" to summaryModel
                )
            )
            
        } catch (e: Exception) {
            val error = "Failed to summarize content: ${e.message}"
            onProgress(error)
            ToolResult.error(error)
        }
    }
    
    private fun buildSummaryPrompt(text: String, format: String, targetLength: Int): String {
        return when (format) {
            "bullets" -> """
                Create a concise bullet-point summary of the following content. 
                Limit to $targetLength main points. Focus on key information and insights.
                
                Content to summarize:
                $text
                
                Summary (bullet points):
            """.trimIndent()
            
            "paragraph" -> """
                Create a concise paragraph summary of the following content.
                Keep it to approximately $targetLength sentences. Capture the main ideas and conclusions.
                
                Content to summarize:
                $text
                
                Summary:
            """.trimIndent()
            
            "headlines" -> """
                Create $targetLength key headlines that capture the most important points from this content.
                Format as numbered headlines.
                
                Content to summarize:
                $text
                
                Key Headlines:
            """.trimIndent()
            
            else -> """
                Summarize the following content in a clear, concise format.
                Focus on the most important information and key takeaways.
                
                Content to summarize:
                $text
                
                Summary:
            """.trimIndent()
        }
    }
    
    override fun getDescription(params: Map<String, Any>): String {
        val format = params["format"] as? String ?: "bullets"
        val targetLength = params["target_length"] as? Int ?: 5
        return "Creating $format summary with $targetLength points"
    }
    
    override fun validateParams(params: Map<String, Any>): String? {
        val text = params["text"] as? String
        return when {
            text.isNullOrBlank() -> "Text parameter is required"
            text.length < 100 -> "Text too short to summarize meaningfully"
            text.length > 50_000 -> "Text too long (max 50KB for summarization)"
            else -> null
        }
    }
    
    override fun getParameterSchema(): String {
        return """
            Parameters:
            - text (required, string): The text content to summarize
              Minimum length: 100 characters
              Maximum length: 50KB
            - format (optional, string): Summary format - "bullets", "paragraph", "headlines", or "default"
              Default: "bullets"
            - target_length (optional, integer): Target number of points/sentences/headlines
              Default: 5
              
            Example: {"text": "Long content to summarize...", "format": "bullets", "target_length": 3}
        """.trimIndent()
    }
}
