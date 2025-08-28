package com.yourname.coquettemobile.core.tools

import com.yourname.coquettemobile.core.logging.CoquetteLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject

/**
 * ImageRendererTool - Renders a specified image URL directly in the chat.
 * This tool does not perform any search; it is given a direct URL to display.
 */
class ImageRendererTool @Inject constructor(
    private val logger: CoquetteLogger
) : MobileTool {
    override val name: String = "ImageRendererTool"
    override val description: String = "Renders an image in the chat from a given URL. The system provides the URL directly."
    override val requiredPermissions: List<String> = emptyList()
    override val riskLevel: RiskLevel = RiskLevel.LOW

    override fun getDescription(params: Map<String, Any>): String {
        val url = params["url"] as? String ?: "an unknown image"
        return "Displaying image from: $url"
    }

    override fun validateParams(params: Map<String, Any>): String? {
        val url = params["url"] as? String
        return if (url.isNullOrBlank()) {
            "URL parameter is required and cannot be blank."
        } else null
    }

    override fun getParameterSchema(): String {
        return """
            Parameters:
            - url (required, string): The direct URL of the image to render in the chat.
        """.trimIndent()
    }

    override suspend fun execute(parameters: Map<String, Any>): ToolResult = withContext(Dispatchers.IO) {
        // This tool's primary job is to create a specific message type for the UI to handle.
        // The "execution" is simply confirming the URL and passing it along.
        val url = parameters["url"] as? String
            ?: return@withContext ToolResult.error("Missing required 'url' parameter.")

        logger.i(name, "Preparing to render image from URL: $url")

        // The success of this tool triggers the ViewModel to create a special Image Message.
        // The output can be a simple confirmation, and the metadata holds the key info.
        ToolResult.success(
            output = "Image ready for display.",
            metadata = mapOf("imageUrl" to url)
        )
    }

    override suspend fun executeStreaming(
        parameters: Map<String, Any>,
        onProgress: (String) -> Unit
    ): ToolResult {
        val url = parameters["url"] as? String
            ?: return ToolResult.error("Missing required 'url' parameter.")

        onProgress("️ Preparing to display image...")
        logger.i(name, "Preparing to render image from URL: $url")
        onProgress("URL received: $url")

        return ToolResult.success(
            output = "Image ready for display.",
            metadata = mapOf("imageUrl" to url)
        )
    }
}
