package com.yourname.coquettemobile.core.tools

import android.content.Context
import android.os.Environment
import com.yourname.coquettemobile.core.logging.CoquetteLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.charset.Charset
import javax.inject.Inject

/**
 * WriteFileTool - Create and write files to Android storage
 * Based on desktop Coquette's write-file.ts patterns
 * Handles Android scoped storage, permissions, and security
 */
class WriteFileTool @Inject constructor(
    private val context: Context,
    private val logger: CoquetteLogger
) : MobileTool {
    
    override val name = "WriteFileTool"
    override val description = "Create and write files to device storage (notes, configs, scripts, etc.)"
    override val riskLevel = RiskLevel.HIGH // File creation is higher risk than reading
    override val requiredPermissions = listOf(
        "android.permission.WRITE_EXTERNAL_STORAGE"
    )
    
    companion object {
        private const val MAX_FILE_SIZE = 10_000_000 // 10MB limit
        private const val MAX_PATH_LENGTH = 500
        
        // Safe directories for writing (avoid system areas)
        private val ALLOWED_BASE_PATHS = setOf(
            "Documents", "Download", "Downloads", "Pictures", "Movies", "Music", 
            "DCIM", "Android/data", "Android/media"
        )
        
        // Restricted directories for security
        private val RESTRICTED_PATHS = setOf(
            "/system", "/root", "/proc", "/dev", "/sys", 
            "/data/system", "/data/misc", "/cache", "/vendor"
        )
        
        // Dangerous file extensions to prevent
        private val RESTRICTED_EXTENSIONS = setOf(
            "exe", "bat", "cmd", "scr", "com", "pif", "vbs", "js", "jar", 
            "app", "deb", "rpm", "dmg", "pkg"
        )
    }
    
    override suspend fun execute(params: Map<String, Any>): ToolResult = withContext(Dispatchers.IO) {
        val filePath = params["path"] as? String
            ?: return@withContext ToolResult.error("Missing required parameter: path")
            
        val content = params["content"] as? String
            ?: return@withContext ToolResult.error("Missing required parameter: content")
        
        try {
            // Security validation
            val securityError = validateFileSecurity(filePath, content)
            if (securityError != null) {
                return@withContext ToolResult.error(securityError)
            }
            
            val file = resolveFilePath(filePath)
            val createDirs = params["createDirs"] as? Boolean ?: true
            val overwrite = params["overwrite"] as? Boolean ?: false
            val encoding = params["encoding"] as? String ?: "UTF-8"
            
            // Check if file exists and overwrite policy
            if (file.exists() && !overwrite) {
                return@withContext ToolResult.error(
                    "File already exists: $filePath (set overwrite=true to replace)"
                )
            }
            
            // Create parent directories if needed
            if (createDirs) {
                val parentDir = file.parentFile
                if (parentDir != null && !parentDir.exists()) {
                    val created = parentDir.mkdirs()
                    if (!created && !parentDir.exists()) {
                        return@withContext ToolResult.error(
                            "Failed to create parent directory: ${parentDir.absolutePath}"
                        )
                    }
                    logger.d("WriteFileTool", "Created directories: ${parentDir.absolutePath}")
                }
            }
            
            // Check if parent directory is writable
            val parentDir = file.parentFile
            if (parentDir != null && !parentDir.canWrite()) {
                return@withContext ToolResult.error(
                    "Cannot write to directory: ${parentDir.absolutePath} (permission denied)"
                )
            }
            
            // Write the file
            try {
                file.writeText(content, Charset.forName(encoding))
            } catch (e: Exception) {
                return@withContext ToolResult.error(
                    "Failed to write file: ${e.message}"
                )
            }
            
            // Verify the write was successful
            if (!file.exists()) {
                return@withContext ToolResult.error(
                    "File write appeared to succeed but file doesn't exist: $filePath"
                )
            }
            
            val actualSize = file.length()
            val expectedSize = content.toByteArray(Charset.forName(encoding)).size
            
            val metadata = mapOf(
                "path" to file.absolutePath,
                "size" to actualSize,
                "expectedSize" to expectedSize,
                "created" to !file.exists(),
                "overwritten" to (file.exists() && overwrite),
                "encoding" to encoding,
                "parentCreated" to createDirs,
                "writable" to file.canWrite(),
                "readable" to file.canRead()
            )
            
            val successMessage = if (actualSize == expectedSize.toLong()) {
                "Successfully wrote file: ${file.absolutePath} (${actualSize} bytes)"
            } else {
                "File written but size mismatch: expected ${expectedSize}, actual ${actualSize} bytes"
            }
            
            ToolResult.success(successMessage, metadata)
            
        } catch (e: SecurityException) {
            ToolResult.error("Security error: ${e.message}")
        } catch (e: Exception) {
            logger.e("WriteFileTool", "Error writing file $filePath: ${e.message}")
            ToolResult.error("Failed to write file: ${e.message}")
        }
    }
    
    override suspend fun executeStreaming(
        params: Map<String, Any>,
        onProgress: (String) -> Unit
    ): ToolResult = withContext(Dispatchers.IO) {
        val filePath = params["path"] as? String
            ?: return@withContext ToolResult.error("Missing required parameter: path")
            
        val content = params["content"] as? String
            ?: return@withContext ToolResult.error("Missing required parameter: content")
        
        onProgress("Validating write request for: $filePath")
        
        try {
            // Security validation
            val securityError = validateFileSecurity(filePath, content)
            if (securityError != null) {
                onProgress("Security check failed")
                return@withContext ToolResult.error(securityError)
            }
            
            val file = resolveFilePath(filePath)
            val createDirs = params["createDirs"] as? Boolean ?: true
            val overwrite = params["overwrite"] as? Boolean ?: false
            val encoding = params["encoding"] as? String ?: "UTF-8"
            
            onProgress("Checking file existence and permissions...")
            
            // Check if file exists and overwrite policy
            if (file.exists() && !overwrite) {
                val error = "File already exists: $filePath (set overwrite=true to replace)"
                onProgress(error)
                return@withContext ToolResult.error(error)
            }
            
            // Create parent directories if needed
            if (createDirs) {
                val parentDir = file.parentFile
                if (parentDir != null && !parentDir.exists()) {
                    onProgress("Creating parent directories...")
                    val created = parentDir.mkdirs()
                    if (!created && !parentDir.exists()) {
                        val error = "Failed to create parent directory: ${parentDir.absolutePath}"
                        onProgress(error)
                        return@withContext ToolResult.error(error)
                    }
                    onProgress("Created directories: ${parentDir.absolutePath}")
                }
            }
            
            // Check if parent directory is writable
            val parentDir = file.parentFile
            if (parentDir != null && !parentDir.canWrite()) {
                val error = "Cannot write to directory: ${parentDir.absolutePath} (permission denied)"
                onProgress(error)
                return@withContext ToolResult.error(error)
            }
            
            onProgress("Writing file content (${content.length} characters)...")
            
            // Write the file
            try {
                file.writeText(content, Charset.forName(encoding))
            } catch (e: Exception) {
                val error = "Failed to write file: ${e.message}"
                onProgress(error)
                return@withContext ToolResult.error(error)
            }
            
            onProgress("Verifying file write...")
            
            // Verify the write was successful
            if (!file.exists()) {
                val error = "File write appeared to succeed but file doesn't exist: $filePath"
                onProgress(error)
                return@withContext ToolResult.error(error)
            }
            
            val actualSize = file.length()
            val expectedSize = content.toByteArray(Charset.forName(encoding)).size
            
            onProgress("File written successfully: ${actualSize} bytes")
            
            val metadata = mapOf(
                "path" to file.absolutePath,
                "size" to actualSize,
                "expectedSize" to expectedSize,
                "created" to !file.exists(),
                "overwritten" to (file.exists() && overwrite),
                "encoding" to encoding,
                "parentCreated" to createDirs,
                "writable" to file.canWrite(),
                "readable" to file.canRead()
            )
            
            val successMessage = if (actualSize == expectedSize.toLong()) {
                "Successfully wrote file: ${file.absolutePath} (${actualSize} bytes)"
            } else {
                "File written but size mismatch: expected ${expectedSize}, actual ${actualSize} bytes"
            }
            
            ToolResult.success(successMessage, metadata)
            
        } catch (e: SecurityException) {
            val error = "Security error: ${e.message}"
            onProgress(error)
            ToolResult.error(error)
        } catch (e: Exception) {
            logger.e("WriteFileTool", "Error writing file $filePath: ${e.message}")
            val error = "Failed to write file: ${e.message}"
            onProgress(error)
            ToolResult.error(error)
        }
    }
    
    /**
     * Validate file path and content for security
     */
    private fun validateFileSecurity(filePath: String, content: String): String? {
        // Path length check
        if (filePath.length > MAX_PATH_LENGTH) {
            return "File path too long: ${filePath.length} characters (max: $MAX_PATH_LENGTH)"
        }
        
        // Content size check  
        if (content.length > MAX_FILE_SIZE) {
            return "Content too large: ${content.length} characters (max: $MAX_FILE_SIZE)"
        }
        
        // Check for restricted paths
        val normalizedPath = filePath.lowercase()
        for (restrictedPath in RESTRICTED_PATHS) {
            if (normalizedPath.startsWith(restrictedPath)) {
                return "Access denied: Cannot write to restricted system path: $restrictedPath"
            }
        }
        
        // Check for path traversal attempts
        if (filePath.contains("..")) {
            return "Security error: Path traversal not allowed"
        }
        
        // Check for dangerous file extensions
        val extension = filePath.substringAfterLast(".", "").lowercase()
        if (RESTRICTED_EXTENSIONS.contains(extension)) {
            return "Security error: Cannot create files with extension: .$extension"
        }
        
        // Check for absolute paths to sensitive areas
        if (filePath.startsWith("/system") || 
            filePath.startsWith("/data") && !filePath.startsWith("/data/media")) {
            return "Access denied: Cannot write to system directories"
        }
        
        // Basic content validation - check for suspicious patterns
        val suspiciousPatterns = listOf(
            "#!/bin/bash", "#!/bin/sh", "cmd.exe", "powershell",
            "<script", "javascript:", "eval(", "exec("
        )
        
        for (pattern in suspiciousPatterns) {
            if (content.lowercase().contains(pattern.lowercase())) {
                logger.w("WriteFileTool", "Suspicious pattern detected in content: $pattern")
                // Log but don't block - might be legitimate code/config files
            }
        }
        
        return null
    }
    
    /**
     * Resolve file path handling Android-specific storage locations
     */
    private fun resolveFilePath(path: String): File {
        return when {
            // Absolute path
            path.startsWith("/") -> File(path)
            
            // Relative to external storage public directories
            path.startsWith("~/") || path.startsWith("Documents/") || 
            path.startsWith("Download/") || path.startsWith("Downloads/") ||
            path.startsWith("Pictures/") || path.startsWith("Music/") ||
            path.startsWith("Movies/") -> {
                val relativePath = path.removePrefix("~/")
                File(Environment.getExternalStorageDirectory(), relativePath)
            }
            
            // App internal storage (safest default)
            else -> File(context.filesDir, path)
        }
    }
    
    override fun getDescription(params: Map<String, Any>): String {
        val path = params["path"] as? String ?: "unknown"
        val contentLength = (params["content"] as? String)?.length ?: 0
        return "Writing file: $path ($contentLength characters)"
    }
    
    override fun validateParams(params: Map<String, Any>): String? {
        val path = params["path"] as? String
        val content = params["content"] as? String
        
        return when {
            path.isNullOrBlank() -> "Path parameter is required"
            content == null -> "Content parameter is required"
            path.length > MAX_PATH_LENGTH -> "Path too long (max $MAX_PATH_LENGTH characters)"
            content.length > MAX_FILE_SIZE -> "Content too large (max $MAX_FILE_SIZE characters)"
            else -> null
        }
    }
    
    override fun getParameterSchema(): String {
        return """
            Parameters:
            - path (required, string): File path to write
              • Use absolute paths: /storage/emulated/0/Documents/file.txt
              • Use relative paths: Documents/notes.txt, config.json
              • Use shortcuts: ~/Downloads/data.csv
            - content (required, string): Content to write to the file
            - overwrite (optional, boolean): Allow overwriting existing files (default: false)
            - createDirs (optional, boolean): Create parent directories if needed (default: true)
            - encoding (optional, string): Text encoding (default: UTF-8)
              • Common encodings: UTF-8, UTF-16, ISO-8859-1
              
            Examples:
            {"path": "Documents/notes.txt", "content": "Hello World"}
            {"path": "config.json", "content": "{\"setting\": true}", "overwrite": true}
            {"path": "~/Downloads/data.csv", "content": "name,value\ntest,123", "createDirs": true}
        """.trimIndent()
    }
}