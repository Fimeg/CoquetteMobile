package com.yourname.coquettemobile.core.tools

import com.yourname.coquettemobile.core.preferences.AppPreferences
import com.yourname.coquettemobile.core.logging.CoquetteLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.URL
import java.util.concurrent.TimeUnit
import javax.inject.Inject

/**
 * WebFetchTool - HTTP content fetching with safety controls
 * Based on desktop Coquette's web-fetch.ts patterns
 */
class WebFetchTool @Inject constructor(
    private val appPreferences: AppPreferences,
    private val logger: CoquetteLogger
) : MobileTool {
    
    override val name = "WebFetchTool"
    override val description = "Fetch content from web URLs with privacy and safety controls"
    override val riskLevel = RiskLevel.MEDIUM
    override val requiredPermissions = listOf("android.permission.INTERNET")
    
    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()
    
    companion object {
        private const val MAX_CONTENT_LENGTH = 500_000 // 500KB limit, increased from 100KB
        private const val USER_AGENT = "CoquetteMobile/1.0"
    }
    
    override suspend fun execute(params: Map<String, Any>): ToolResult = withContext(Dispatchers.IO) {
        val url = params["url"] as? String
            ?: return@withContext ToolResult.error("Missing required parameter: url")
        
        try {
            // Validate URL
            val validationError = validateUrl(url)
            if (validationError != null) {
                return@withContext ToolResult.error(validationError)
            }
            
            // Convert GitHub blob URLs to raw URLs (like desktop)
            var fetchUrl = convertGitHubUrl(url)
            
            // Auto-convert HTTP to HTTPS for security
            if (fetchUrl.startsWith("http://")) {
                fetchUrl = fetchUrl.replace("http://", "https://")
                logger.d("WebFetchTool", "Auto-converted to HTTPS: $fetchUrl")
            }
            
            // Check if private IP (desktop safety pattern)
            if (isPrivateIp(fetchUrl)) {
                return@withContext ToolResult.error(
                    "Cannot fetch from private IP addresses for security reasons"
                )
            }
            
            val request = Request.Builder()
                .url(fetchUrl)
                .addHeader("User-Agent", USER_AGENT)
                .build()
                
            val response = client.newCall(request).execute()
            
            if (!response.isSuccessful) {
                return@withContext ToolResult.error(
                    "HTTP ${response.code}: ${response.message}"
                )
            }
            
            val content = response.body?.string() ?: ""
            
            // Enforce content length limit
            val limitedContent = if (content.length > MAX_CONTENT_LENGTH) {
                content.take(MAX_CONTENT_LENGTH) + "\n\n[Content truncated at ${MAX_CONTENT_LENGTH} characters]"
            } else {
                content
            }
            
            ToolResult.success(
                output = limitedContent,
                metadata = mapOf(
                    "url" to fetchUrl,
                    "original_url" to url,
                    "content_length" to content.length,
                    "truncated" to (content.length > MAX_CONTENT_LENGTH),
                    "content_type" to (response.header("Content-Type") ?: "unknown")
                )
            )
            
        } catch (e: Exception) {
            logger.e("WebFetchTool", "Failed to fetch URL: $url - Exception: ${e.message}")
            ToolResult.error("Failed to fetch URL: ${e.message}")
        }
    }

    override suspend fun executeStreaming(
        params: Map<String, Any>,
        onProgress: (String) -> Unit
    ): ToolResult = withContext(Dispatchers.IO) {
        val url = params["url"] as? String
            ?: return@withContext ToolResult.error("Missing required parameter: url")
        
        try {
            // Validate URL
            val validationError = validateUrl(url)
            if (validationError != null) {
                return@withContext ToolResult.error(validationError)
            }
            
            // Convert GitHub blob URLs to raw URLs (like desktop)
            var fetchUrl = convertGitHubUrl(url)
            
            // Auto-convert HTTP to HTTPS for security
            if (fetchUrl.startsWith("http://")) {
                fetchUrl = fetchUrl.replace("http://", "https://")
                logger.d("WebFetchTool", "Auto-converted to HTTPS: $fetchUrl")
                onProgress("Auto-converted to HTTPS: $fetchUrl")
            }
            
            // Check if private IP (desktop safety pattern)
            if (isPrivateIp(fetchUrl)) {
                val error = "Cannot fetch from private IP addresses for security reasons"
                onProgress(error)
                return@withContext ToolResult.error(error)
            }
            
            onProgress("Connecting to $fetchUrl...")
            
            val request = Request.Builder()
                .url(fetchUrl)
                .addHeader("User-Agent", USER_AGENT)
                .build()
                
            val response = client.newCall(request).execute()
            
            if (!response.isSuccessful) {
                val error = "HTTP ${response.code}: ${response.message}"
                onProgress(error)
                return@withContext ToolResult.error(error)
            }
            
            onProgress("Downloading content...")
            
            val content = response.body?.string() ?: ""
            
            // Enforce content length limit
            val limitedContent = if (content.length > MAX_CONTENT_LENGTH) {
                content.take(MAX_CONTENT_LENGTH) + "\n\n[Content truncated at ${MAX_CONTENT_LENGTH} characters]"
            } else {
                content
            }
            
            onProgress("Content downloaded (${content.length} characters)")
            
            ToolResult.success(
                output = limitedContent,
                metadata = mapOf(
                    "url" to fetchUrl,
                    "original_url" to url,
                    "content_length" to content.length,
                    "truncated" to (content.length > MAX_CONTENT_LENGTH),
                    "content_type" to (response.header("Content-Type") ?: "unknown")
                )
            )
            
        } catch (e: Exception) {
            val error = "Failed to fetch URL: ${e.message}"
            logger.e("WebFetchTool", "Failed to fetch URL: $url - Exception: ${e.message}")
            onProgress(error)
            ToolResult.error(error)
        }
    }
    
    private fun validateUrl(url: String): String? {
        return try {
            val parsedUrl = URL(url)
            when {
                !parsedUrl.protocol.startsWith("http") -> 
                    "Only HTTP and HTTPS URLs are allowed"
                parsedUrl.host.isNullOrBlank() -> 
                    "Invalid URL: missing host"
                else -> null
            }
        } catch (e: Exception) {
            "Invalid URL format: ${e.message}"
        }
    }
    
    private fun convertGitHubUrl(url: String): String {
        // Convert GitHub blob URL to raw URL (desktop pattern)
        return if (url.contains("github.com") && url.contains("/blob/")) {
            url.replace("github.com", "raw.githubusercontent.com")
                .replace("/blob/", "/")
        } else {
            url
        }
    }
    
    private fun isPrivateIp(url: String): Boolean {
        return try {
            val host = URL(url).host
            when {
                host == "localhost" -> true
                host == "127.0.0.1" -> true
                host.startsWith("192.168.") -> true
                host.startsWith("10.") -> true
                host.startsWith("172.") && host.split(".")[1].toIntOrNull()?.let {
                    it in 16..31 
                } == true -> true
                else -> false
            }
        } catch (e: Exception) {
            false
        }
    }
    
    override fun getDescription(params: Map<String, Any>): String {
        val url = params["url"] as? String ?: "unknown"
        return "Fetching content from: $url"
    }
    
    override fun validateParams(params: Map<String, Any>): String? {
        val url = params["url"] as? String
        return when {
            url.isNullOrBlank() -> "URL parameter is required"
            else -> validateUrl(url)
        }
    }
    
    override fun getParameterSchema(): String {
        return """
            Parameters:
            - url (required, string): The HTTP/HTTPS URL to fetch content from
              
            Example: {"url": "https://example.com/page"}
        """.trimIndent()
    }
}
