package com.yourname.coquettemobile.core.tools

import android.content.Context
import android.os.Environment
import com.yourname.coquettemobile.core.logging.CoquetteLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileNotFoundException
import java.nio.charset.Charset
import javax.inject.Inject

/**
 * FileTool - Read files from Android storage
 * Based on desktop Coquette's read-file.ts patterns
 * Handles Android scoped storage, permissions, and security
 */
class FileTool @Inject constructor(
    private val context: Context,
    private val logger: CoquetteLogger
) : MobileTool {
    
    override val name = "FileTool"
    override val description = "Read files from device storage (text, code, configs, etc.)"
    override val riskLevel = RiskLevel.MEDIUM
    override val requiredPermissions = listOf(
        "android.permission.READ_EXTERNAL_STORAGE"
    )
    
    companion object {
        private const val MAX_FILE_SIZE = 1_000_000 // 1MB limit
        private const val MAX_BINARY_PREVIEW = 512 // Preview bytes for binary files
        
        // Safe file extensions for reading
        private val TEXT_EXTENSIONS = setOf(
            "txt", "md", "json", "xml", "html", "htm", "css", "js", "ts", "kt", "java",
            "py", "cpp", "c", "h", "gradle", "properties", "yml", "yaml", "toml",
            "conf", "config", "ini", "log", "csv", "sql", "sh", "bat", "ps1"
        )
        
        // Restricted directories for security
        private val RESTRICTED_PATHS = setOf(
            "/system", "/root", "/proc", "/dev", "/sys", 
            "/data/system", "/data/misc", "/cache"
        )
    }
    
    override suspend fun execute(params: Map<String, Any>): ToolResult = withContext(Dispatchers.IO) {
        val filePath = params["path"] as? String
            ?: return@withContext ToolResult.error("Missing required parameter: path")
        
        try {
            // Security validation
            val securityError = validateFileSecurity(filePath)
            if (securityError != null) {
                return@withContext ToolResult.error(securityError)
            }
            
            val file = resolveFilePath(filePath)
            
            // Check file existence and readability
            when {
                !file.exists() -> return@withContext ToolResult.error(
                    "File not found: $filePath"
                )
                !file.canRead() -> return@withContext ToolResult.error(
                    "Cannot read file: $filePath (permission denied)"
                )
                file.isDirectory() -> return@withContext ToolResult.error(
                    "Path is a directory, not a file: $filePath (use ListTool instead)"
                )
                file.length() > MAX_FILE_SIZE -> return@withContext ToolResult.error(
                    "File too large: ${file.length()} bytes (max: ${MAX_FILE_SIZE} bytes)"
                )
            }
            
            // Determine if file is text or binary
            val isTextFile = isTextFile(file)
            val encoding = params["encoding"] as? String ?: "UTF-8"
            
            val content = if (isTextFile) {
                // Read as text file
                try {
                    file.readText(Charset.forName(encoding))
                } catch (e: Exception) {
                    // Fallback to UTF-8 if specified encoding fails
                    file.readText(Charset.forName("UTF-8"))
                }
            } else {
                // Binary file - provide preview and metadata
                val preview = file.readBytes().take(MAX_BINARY_PREVIEW)
                val hexPreview = preview.joinToString(" ") { "%02x".format(it) }
                
                """
                Binary file detected: $filePath
                Size: ${file.length()} bytes
                Modified: ${java.util.Date(file.lastModified())}
                
                Hex preview (first $MAX_BINARY_PREVIEW bytes):
                $hexPreview
                
                Note: Use appropriate tools for binary file analysis
                """.trimIndent()
            }
            
            val metadata = mapOf(
                "path" to file.absolutePath,
                "size" to file.length(),
                "lastModified" to file.lastModified(),
                "canWrite" to file.canWrite(),
                "isText" to isTextFile,
                "encoding" to encoding,
                "extension" to (file.extension.takeIf { it.isNotEmpty() } ?: "none")
            )
            
            ToolResult.success(content, metadata)
            
        } catch (e: SecurityException) {
            ToolResult.error("Security error: ${e.message}")
        } catch (e: FileNotFoundException) {
            ToolResult.error("File not found: $filePath")
        } catch (e: Exception) {
            logger.e("FileTool", "Error reading file $filePath: ${e.message}")
            ToolResult.error("Failed to read file: ${e.message}")
        }
    }
    
    override suspend fun executeStreaming(
        params: Map<String, Any>,
        onProgress: (String) -> Unit
    ): ToolResult = withContext(Dispatchers.IO) {
        val filePath = params["path"] as? String
            ?: return@withContext ToolResult.error("Missing required parameter: path")
        
        onProgress("Validating file path: $filePath")
        
        try {
            // Security validation
            val securityError = validateFileSecurity(filePath)
            if (securityError != null) {
                onProgress("Security check failed")
                return@withContext ToolResult.error(securityError)
            }
            
            val file = resolveFilePath(filePath)
            
            onProgress("Checking file accessibility...")
            
            // Check file existence and readability
            when {
                !file.exists() -> {
                    onProgress("File not found")
                    return@withContext ToolResult.error("File not found: $filePath")
                }
                !file.canRead() -> {
                    onProgress("Permission denied")
                    return@withContext ToolResult.error("Cannot read file: $filePath (permission denied)")
                }
                file.isDirectory() -> {
                    onProgress("Path is directory")
                    return@withContext ToolResult.error("Path is a directory, not a file: $filePath (use ListTool instead)")
                }
                file.length() > MAX_FILE_SIZE -> {
                    onProgress("File too large: ${file.length()} bytes")
                    return@withContext ToolResult.error("File too large: ${file.length()} bytes (max: ${MAX_FILE_SIZE} bytes)")
                }
            }
            
            onProgress("Reading file content (${file.length()} bytes)...")
            
            // Determine if file is text or binary
            val isTextFile = isTextFile(file)
            val encoding = params["encoding"] as? String ?: "UTF-8"
            
            onProgress(if (isTextFile) "Processing as text file" else "Processing as binary file")
            
            val content = if (isTextFile) {
                // Read as text file
                try {
                    file.readText(Charset.forName(encoding))
                } catch (e: Exception) {
                    onProgress("Encoding failed, falling back to UTF-8")
                    file.readText(Charset.forName("UTF-8"))
                }
            } else {
                // Binary file - provide preview and metadata
                val preview = file.readBytes().take(MAX_BINARY_PREVIEW)
                val hexPreview = preview.joinToString(" ") { "%02x".format(it) }
                
                """
                Binary file detected: $filePath
                Size: ${file.length()} bytes
                Modified: ${java.util.Date(file.lastModified())}
                
                Hex preview (first $MAX_BINARY_PREVIEW bytes):
                $hexPreview
                
                Note: Use appropriate tools for binary file analysis
                """.trimIndent()
            }
            
            onProgress("File read successfully")
            
            val metadata = mapOf(
                "path" to file.absolutePath,
                "size" to file.length(),
                "lastModified" to file.lastModified(),
                "canWrite" to file.canWrite(),
                "isText" to isTextFile,
                "encoding" to encoding,
                "extension" to (file.extension.takeIf { it.isNotEmpty() } ?: "none")
            )
            
            ToolResult.success(content, metadata)
            
        } catch (e: SecurityException) {
            val error = "Security error: ${e.message}"
            onProgress(error)
            ToolResult.error(error)
        } catch (e: FileNotFoundException) {
            val error = "File not found: $filePath"
            onProgress(error)
            ToolResult.error(error)
        } catch (e: Exception) {
            logger.e("FileTool", "Error reading file $filePath: ${e.message}")
            val error = "Failed to read file: ${e.message}"
            onProgress(error)
            ToolResult.error(error)
        }
    }
    
    /**
     * Validate file path for security (prevent access to sensitive system files)
     */
    private fun validateFileSecurity(filePath: String): String? {
        // Check for restricted paths
        val normalizedPath = filePath.lowercase()
        for (restrictedPath in RESTRICTED_PATHS) {
            if (normalizedPath.startsWith(restrictedPath)) {
                return "Access denied: Cannot read from restricted system path: $restrictedPath"
            }
        }
        
        // Check for path traversal attempts
        if (filePath.contains("..")) {
            return "Security error: Path traversal not allowed"
        }
        
        // Check for absolute paths to sensitive areas
        if (filePath.startsWith("/data") && !filePath.startsWith("/data/media")) {
            return "Access denied: Cannot read from /data directory"
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
            
            // Relative to external storage
            path.startsWith("~/") || path.startsWith("Downloads/") || 
            path.startsWith("Documents/") || path.startsWith("Pictures/") -> {
                val relativePath = path.removePrefix("~/")
                File(Environment.getExternalStorageDirectory(), relativePath)
            }
            
            // App internal storage (safest)
            else -> File(context.filesDir, path)
        }
    }
    
    /**
     * Determine if file is text-based or binary
     */
    private fun isTextFile(file: File): Boolean {
        val extension = file.extension.lowercase()
        
        // Check known text extensions
        if (TEXT_EXTENSIONS.contains(extension)) {
            return true
        }
        
        // For unknown extensions, sample the first few bytes
        try {
            val sample = file.readBytes().take(512)
            if (sample.isEmpty()) return true
            
            // Simple heuristic: if more than 90% of sampled bytes are printable ASCII, treat as text
            val printableCount = sample.count { byte ->
                byte.toInt() and 0xFF in 0x20..0x7E || byte.toInt() == 0x09 || byte.toInt() == 0x0A || byte.toInt() == 0x0D
            }
            
            return printableCount.toFloat() / sample.size > 0.9f
        } catch (e: Exception) {
            return false
        }
    }
    
    override fun getDescription(params: Map<String, Any>): String {
        val path = params["path"] as? String ?: "unknown"
        return "Reading file: $path"
    }
    
    override fun validateParams(params: Map<String, Any>): String? {
        val path = params["path"] as? String
        return when {
            path.isNullOrBlank() -> "Path parameter is required"
            path.length > 500 -> "Path too long (max 500 characters)"
            else -> null
        }
    }
    
    override fun getParameterSchema(): String {
        return """
            Parameters:
            - path (required, string): File path to read
              • Use absolute paths: /storage/emulated/0/Documents/file.txt
              • Use relative paths: Documents/notes.txt, config.json
              • Use shortcuts: ~/Downloads/data.csv
            - encoding (optional, string): Text encoding (default: UTF-8)
              • Common encodings: UTF-8, UTF-16, ISO-8859-1
              
            Examples:
            {"path": "/storage/emulated/0/Download/document.txt"}
            {"path": "Documents/notes.md", "encoding": "UTF-8"}
        """.trimIndent()
    }
}