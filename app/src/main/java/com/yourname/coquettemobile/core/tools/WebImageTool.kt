package com.yourname.coquettemobile.core.tools

import com.yourname.coquettemobile.core.logging.CoquetteLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject

class WebImageTool @Inject constructor(
    private val logger: CoquetteLogger
) : MobileTool {
    override val name: String = "WebImageTool"
    override val description: String = "Finds and returns a URL for an image based on a search query. Use this when the user asks for a picture, image, or photo of something."
    override val requiredPermissions: List<String> = emptyList()
    override val riskLevel: RiskLevel = RiskLevel.LOW

    override fun getDescription(params: Map<String, Any>): String {
        val query = params["query"] as? String ?: "unknown"
        return "Find an image for '$query'"
    }

    override fun validateParams(params: Map<String, Any>): String? {
        val query = params["query"] as? String
        return if (query.isNullOrBlank()) {
            "Query parameter is required and cannot be blank"
        } else null
    }

    override fun getParameterSchema(): String {
        return "query: string (required) - Search query for the image"
    }

    override suspend fun execute(parameters: Map<String, Any>): ToolResult = withContext(Dispatchers.IO) {
        executeStreaming(parameters) { /* ignore progress for sync execution */ }
    }

    override suspend fun executeStreaming(
        parameters: Map<String, Any>,
        onProgress: (String) -> Unit
    ): ToolResult {
        val query = parameters["query"] as? String
        if (query.isNullOrBlank()) {
            logger.w(name, "Query is missing or blank.")
            onProgress("❌ Query is missing or blank")
            return ToolResult.error("You must provide a 'query' for the image search.")
        }

        onProgress("🔍 Searching for images matching '$query'...")
        
        return try {
            // Use a placeholder image service for now. This can be replaced with a real image search API.
            // Using picsum.photos to get a random image based on a seed derived from the query.
            val seed = query.hashCode()
            val imageUrl = "https://picsum.photos/seed/$seed/800/600"
            
            onProgress("📷 Found image for '$query'")
            onProgress("🖼️ Image URL: $imageUrl")
            
            logger.i(name, "Found image for query '$query': $imageUrl")
            onProgress("✅ Image search complete!")
            ToolResult.success(imageUrl)
        } catch (e: Exception) {
            logger.e(name, "Failed to generate image URL for query: $query")
            onProgress("❌ Failed to find image")
            ToolResult.error("An error occurred while finding an image.")
        }
    }
}
