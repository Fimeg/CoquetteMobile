package com.yourname.coquettemobile.core.tools

import android.content.Context
import android.os.Environment
import com.yourname.coquettemobile.core.logging.CoquetteLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.charset.Charset
import java.util.regex.Pattern
import javax.inject.Inject

/**
 * GrepTool - Search content within files (like ripgrep)
 * Based on desktop Coquette's grep.ts patterns
 * Android equivalent of `rg "pattern" --type kt`
 */
class GrepTool @Inject constructor(
    private val context: Context,
    private val logger: CoquetteLogger
) : MobileTool {
    
    override val name = "GrepTool"
    override val description = "Search for text patterns within file contents (ripgrep-style)"
    override val riskLevel = RiskLevel.LOW // Read-only content search
    override val requiredPermissions = listOf(
        "android.permission.READ_EXTERNAL_STORAGE"
    )
    
    companion object {
        private const val MAX_FILE_SIZE = 10_000_000 // 10MB per file limit
        private const val MAX_TOTAL_SIZE = 50_000_000 // 50MB total search limit
        private const val MAX_MATCHES = 1000 // Max matches to return
        private const val MAX_DEPTH = 10 // Max directory depth
        private const val MAX_LINE_LENGTH = 500 // Truncate long lines
        
        // Text file extensions to search
        private val TEXT_EXTENSIONS = setOf(
            "txt", "md", "json", "xml", "html", "htm", "css", "js", "ts", "kt", "java",
            "py", "cpp", "c", "h", "gradle", "properties", "yml", "yaml", "toml",
            "conf", "config", "ini", "log", "csv", "sql", "sh", "bat", "go", "rs", "rb"
        )
        
        // Restricted directories for security
        private val RESTRICTED_PATHS = setOf(
            "/system", "/root", "/proc", "/dev", "/sys", 
            "/data/system", "/data/misc", "/cache"
        )
    }
    
    data class GrepMatch(
        val file: String,
        val lineNumber: Int,
        val line: String,
        val matchStart: Int,
        val matchEnd: Int,
        val beforeContext: List<String> = emptyList(),
        val afterContext: List<String> = emptyList()
    )
    
    override suspend fun execute(params: Map<String, Any>): ToolResult = withContext(Dispatchers.IO) {
        val pattern = params["pattern"] as? String
            ?: return@withContext ToolResult.error("Missing required parameter: pattern")
        
        val basePath = params["path"] as? String ?: "."
        val filePattern = params["filePattern"] as? String // glob for files to search
        val recursive = params["recursive"] as? Boolean ?: true
        val ignoreCase = params["ignoreCase"] as? Boolean ?: false
        val wholeWord = params["wholeWord"] as? Boolean ?: false
        val maxDepth = ((params["maxDepth"] as? Number)?.toInt() ?: MAX_DEPTH).coerceAtMost(MAX_DEPTH)
        val contextBefore = ((params["contextBefore"] as? Number)?.toInt() ?: 0).coerceIn(0, 10)
        val contextAfter = ((params["contextAfter"] as? Number)?.toInt() ?: 0).coerceIn(0, 10)
        val limit = ((params["limit"] as? Number)?.toInt() ?: MAX_MATCHES).coerceAtMost(MAX_MATCHES)
        val encoding = params["encoding"] as? String ?: "UTF-8"
        
        try {
            // Security validation
            val securityError = validatePathSecurity(basePath)
            if (securityError != null) {
                return@withContext ToolResult.error(securityError)
            }
            
            val baseDirectory = resolveDirectoryPath(basePath)
            
            // Check base directory
            when {
                !baseDirectory.exists() -> return@withContext ToolResult.error(
                    "Base directory not found: $basePath"
                )
                !baseDirectory.canRead() -> return@withContext ToolResult.error(
                    "Cannot read base directory: $basePath (permission denied)"
                )
            }
            
            // Compile search pattern
            val searchRegex = try {
                compileSearchPattern(pattern, ignoreCase, wholeWord)
            } catch (e: Exception) {
                return@withContext ToolResult.error("Invalid search pattern: $pattern - ${e.message}")
            }
            
            // Compile file filter pattern if provided
            val fileFilterRegex = filePattern?.let { 
                try {
                    globToRegex(it)
                } catch (e: Exception) {
                    return@withContext ToolResult.error("Invalid file pattern: $filePattern - ${e.message}")
                }
            }
            
            // Search for matches
            val matches = mutableListOf<GrepMatch>()
            var totalSizeProcessed = 0L
            val searchStartTime = System.currentTimeMillis()
            
            searchInFiles(
                directory = baseDirectory,
                searchRegex = searchRegex,
                fileFilterRegex = fileFilterRegex,
                matches = matches,
                currentDepth = 0,
                maxDepth = if (recursive) maxDepth else 0,
                contextBefore = contextBefore,
                contextAfter = contextAfter,
                limit = limit,
                encoding = encoding,
                totalSizeProcessed = { totalSizeProcessed },
                onSizeUpdate = { totalSizeProcessed = it }
            )
            
            val searchTime = System.currentTimeMillis() - searchStartTime
            
            // Format output
            val output = formatGrepResults(matches, pattern, baseDirectory, contextBefore > 0 || contextAfter > 0)
            
            val metadata = mapOf(
                "pattern" to pattern,
                "basePath" to baseDirectory.absolutePath,
                "totalMatches" to matches.size,
                "filesSearched" to matches.map { it.file }.distinct().size,
                "totalSizeProcessed" to totalSizeProcessed,
                "searchTime" to searchTime,
                "recursive" to recursive,
                "ignoreCase" to ignoreCase,
                "wholeWord" to wholeWord,
                "contextBefore" to contextBefore,
                "contextAfter" to contextAfter,
                "truncated" to (matches.size >= limit)
            )
            
            ToolResult.success(output, metadata)
            
        } catch (e: SecurityException) {
            ToolResult.error("Security error: ${e.message}")
        } catch (e: Exception) {
            logger.e("GrepTool", "Error during grep search $pattern: ${e.message}")
            ToolResult.error("Failed to search file contents: ${e.message}")
        }
    }
    
    override suspend fun executeStreaming(
        params: Map<String, Any>,
        onProgress: (String) -> Unit
    ): ToolResult = withContext(Dispatchers.IO) {
        val pattern = params["pattern"] as? String
            ?: return@withContext ToolResult.error("Missing required parameter: pattern")
        
        val basePath = params["path"] as? String ?: "."
        val filePattern = params["filePattern"] as? String
        val recursive = params["recursive"] as? Boolean ?: true
        val ignoreCase = params["ignoreCase"] as? Boolean ?: false
        val wholeWord = params["wholeWord"] as? Boolean ?: false
        val maxDepth = ((params["maxDepth"] as? Number)?.toInt() ?: MAX_DEPTH).coerceAtMost(MAX_DEPTH)
        val contextBefore = ((params["contextBefore"] as? Number)?.toInt() ?: 0).coerceIn(0, 10)
        val contextAfter = ((params["contextAfter"] as? Number)?.toInt() ?: 0).coerceIn(0, 10)
        val limit = ((params["limit"] as? Number)?.toInt() ?: MAX_MATCHES).coerceAtMost(MAX_MATCHES)
        val encoding = params["encoding"] as? String ?: "UTF-8"
        
        onProgress("Starting content search for pattern: $pattern")
        
        try {
            // Security validation
            val securityError = validatePathSecurity(basePath)
            if (securityError != null) {
                onProgress("Security check failed")
                return@withContext ToolResult.error(securityError)
            }
            
            val baseDirectory = resolveDirectoryPath(basePath)
            
            onProgress("Validating base directory: ${baseDirectory.absolutePath}")
            
            // Check base directory
            when {
                !baseDirectory.exists() -> {
                    onProgress("Base directory not found")
                    return@withContext ToolResult.error("Base directory not found: $basePath")
                }
                !baseDirectory.canRead() -> {
                    onProgress("Permission denied")
                    return@withContext ToolResult.error("Cannot read base directory: $basePath (permission denied)")
                }
            }
            
            onProgress("Compiling search pattern...")
            
            // Compile search pattern
            val searchRegex = try {
                compileSearchPattern(pattern, ignoreCase, wholeWord)
            } catch (e: Exception) {
                val error = "Invalid search pattern: $pattern - ${e.message}"
                onProgress(error)
                return@withContext ToolResult.error(error)
            }
            
            // Compile file filter pattern if provided
            val fileFilterRegex = filePattern?.let { 
                try {
                    onProgress("Compiling file filter: $filePattern")
                    globToRegex(it)
                } catch (e: Exception) {
                    val error = "Invalid file pattern: $filePattern - ${e.message}"
                    onProgress(error)
                    return@withContext ToolResult.error(error)
                }
            }
            
            onProgress("Searching files for matches...")
            
            // Search for matches with progress updates
            val matches = mutableListOf<GrepMatch>()
            var totalSizeProcessed = 0L
            var filesProcessed = 0
            var lastProgressUpdate = 0L
            val searchStartTime = System.currentTimeMillis()
            
            searchInFilesWithProgress(
                directory = baseDirectory,
                searchRegex = searchRegex,
                fileFilterRegex = fileFilterRegex,
                matches = matches,
                currentDepth = 0,
                maxDepth = if (recursive) maxDepth else 0,
                contextBefore = contextBefore,
                contextAfter = contextAfter,
                limit = limit,
                encoding = encoding,
                totalSizeProcessed = { totalSizeProcessed },
                onSizeUpdate = { totalSizeProcessed = it },
                onProgress = { matchCount, fileName ->
                    filesProcessed++
                    val now = System.currentTimeMillis()
                    if (now - lastProgressUpdate > 1500 || matchCount % 20 == 0) {
                        val sizeStr = formatFileSize(totalSizeProcessed)
                        onProgress("$matchCount matches in $filesProcessed files ($sizeStr processed) - ${fileName.takeLast(40)}")
                        lastProgressUpdate = now
                    }
                }
            )
            
            val searchTime = System.currentTimeMillis() - searchStartTime
            
            onProgress("Search complete: ${matches.size} matches in ${matches.map { it.file }.distinct().size} files")
            
            // Format output
            val output = formatGrepResults(matches, pattern, baseDirectory, contextBefore > 0 || contextAfter > 0)
            
            val metadata = mapOf(
                "pattern" to pattern,
                "basePath" to baseDirectory.absolutePath,
                "totalMatches" to matches.size,
                "filesSearched" to matches.map { it.file }.distinct().size,
                "totalSizeProcessed" to totalSizeProcessed,
                "searchTime" to searchTime,
                "recursive" to recursive,
                "ignoreCase" to ignoreCase,
                "wholeWord" to wholeWord,
                "contextBefore" to contextBefore,
                "contextAfter" to contextAfter,
                "truncated" to (matches.size >= limit)
            )
            
            ToolResult.success(output, metadata)
            
        } catch (e: SecurityException) {
            val error = "Security error: ${e.message}"
            onProgress(error)
            ToolResult.error(error)
        } catch (e: Exception) {
            logger.e("GrepTool", "Error during grep search $pattern: ${e.message}")
            val error = "Failed to search file contents: ${e.message}"
            onProgress(error)
            ToolResult.error(error)
        }
    }
    
    /**
     * Search for matches in files recursively
     */
    private fun searchInFiles(
        directory: File,
        searchRegex: Regex,
        fileFilterRegex: Regex?,
        matches: MutableList<GrepMatch>,
        currentDepth: Int,
        maxDepth: Int,
        contextBefore: Int,
        contextAfter: Int,
        limit: Int,
        encoding: String,
        totalSizeProcessed: () -> Long,
        onSizeUpdate: (Long) -> Unit
    ) {
        if (matches.size >= limit || currentDepth > maxDepth || totalSizeProcessed() > MAX_TOTAL_SIZE) return
        
        try {
            val files = directory.listFiles() ?: return
            
            for (file in files) {
                if (matches.size >= limit || totalSizeProcessed() > MAX_TOTAL_SIZE) break
                
                if (file.isDirectory() && currentDepth < maxDepth && file.canRead()) {
                    // Recurse into subdirectories
                    searchInFiles(
                        file, searchRegex, fileFilterRegex, matches, currentDepth + 1, maxDepth,
                        contextBefore, contextAfter, limit, encoding, totalSizeProcessed, onSizeUpdate
                    )
                } else if (file.isFile() && shouldSearchFile(file, fileFilterRegex)) {
                    searchInFile(file, searchRegex, matches, contextBefore, contextAfter, limit, encoding, totalSizeProcessed, onSizeUpdate)
                }
            }
        } catch (e: SecurityException) {
            logger.d("GrepTool", "Skipping directory due to permissions: ${directory.absolutePath}")
        }
    }
    
    /**
     * Search with progress updates
     */
    private fun searchInFilesWithProgress(
        directory: File,
        searchRegex: Regex,
        fileFilterRegex: Regex?,
        matches: MutableList<GrepMatch>,
        currentDepth: Int,
        maxDepth: Int,
        contextBefore: Int,
        contextAfter: Int,
        limit: Int,
        encoding: String,
        totalSizeProcessed: () -> Long,
        onSizeUpdate: (Long) -> Unit,
        onProgress: (Int, String) -> Unit
    ) {
        if (matches.size >= limit || currentDepth > maxDepth || totalSizeProcessed() > MAX_TOTAL_SIZE) return
        
        try {
            val files = directory.listFiles() ?: return
            
            for (file in files) {
                if (matches.size >= limit || totalSizeProcessed() > MAX_TOTAL_SIZE) break
                
                if (file.isDirectory() && currentDepth < maxDepth && file.canRead()) {
                    // Recurse into subdirectories
                    searchInFilesWithProgress(
                        file, searchRegex, fileFilterRegex, matches, currentDepth + 1, maxDepth,
                        contextBefore, contextAfter, limit, encoding, totalSizeProcessed, onSizeUpdate, onProgress
                    )
                } else if (file.isFile() && shouldSearchFile(file, fileFilterRegex)) {
                    val beforeMatches = matches.size
                    searchInFile(file, searchRegex, matches, contextBefore, contextAfter, limit, encoding, totalSizeProcessed, onSizeUpdate)
                    if (matches.size > beforeMatches) {
                        onProgress(matches.size, file.name)
                    }
                }
            }
        } catch (e: SecurityException) {
            logger.d("GrepTool", "Skipping directory due to permissions: ${directory.absolutePath}")
        }
    }
    
    /**
     * Search within a single file
     */
    private fun searchInFile(
        file: File,
        searchRegex: Regex,
        matches: MutableList<GrepMatch>,
        contextBefore: Int,
        contextAfter: Int,
        limit: Int,
        encoding: String,
        totalSizeProcessed: () -> Long,
        onSizeUpdate: (Long) -> Unit
    ) {
        if (matches.size >= limit || file.length() > MAX_FILE_SIZE) return
        
        try {
            val lines = file.readText(Charset.forName(encoding)).split('\n')
            onSizeUpdate(totalSizeProcessed() + file.length())
            
            for ((lineIndex, line) in lines.withIndex()) {
                if (matches.size >= limit) break
                
                val matchResult = searchRegex.find(line)
                if (matchResult != null) {
                    val beforeLines = if (contextBefore > 0) {
                        lines.subList(
                            maxOf(0, lineIndex - contextBefore),
                            lineIndex
                        )
                    } else emptyList()
                    
                    val afterLines = if (contextAfter > 0) {
                        lines.subList(
                            lineIndex + 1,
                            minOf(lines.size, lineIndex + 1 + contextAfter)
                        )
                    } else emptyList()
                    
                    val truncatedLine = if (line.length > MAX_LINE_LENGTH) {
                        line.take(MAX_LINE_LENGTH) + "..."
                    } else line
                    
                    matches.add(
                        GrepMatch(
                            file = file.absolutePath,
                            lineNumber = lineIndex + 1,
                            line = truncatedLine,
                            matchStart = matchResult.range.first,
                            matchEnd = matchResult.range.last + 1,
                            beforeContext = beforeLines,
                            afterContext = afterLines
                        )
                    )
                }
            }
        } catch (e: Exception) {
            // Skip files that can't be read as text
            logger.d("GrepTool", "Skipping file due to read error: ${file.absolutePath} - ${e.message}")
        }
    }
    
    /**
     * Check if file should be searched based on filters
     */
    private fun shouldSearchFile(file: File, fileFilterRegex: Regex?): Boolean {
        // Size check
        if (file.length() > MAX_FILE_SIZE) return false
        
        // File filter check
        if (fileFilterRegex != null) {
            return fileFilterRegex.containsMatchIn(file.name)
        }
        
        // Default: search known text file types
        val extension = file.extension.lowercase()
        return TEXT_EXTENSIONS.contains(extension) || extension.isEmpty()
    }
    
    /**
     * Compile search pattern with options
     */
    private fun compileSearchPattern(pattern: String, ignoreCase: Boolean, wholeWord: Boolean): Regex {
        var regexPattern = if (wholeWord) {
            "\\b${Pattern.quote(pattern)}\\b"
        } else {
            Pattern.quote(pattern)
        }
        
        val options = if (ignoreCase) {
            setOf(RegexOption.IGNORE_CASE)
        } else {
            emptySet()
        }
        
        return Regex(regexPattern, options)
    }
    
    /**
     * Convert glob pattern to regex for file filtering
     */
    private fun globToRegex(pattern: String): Regex {
        val regexPattern = pattern
            .replace(".", "\\.")      // Escape dots
            .replace("*", ".*")       // * matches any characters
            .replace("?", ".")        // ? matches single character
        
        return Regex("^$regexPattern$", RegexOption.IGNORE_CASE)
    }
    
    /**
     * Format grep search results
     */
    private fun formatGrepResults(
        matches: List<GrepMatch>,
        pattern: String,
        baseDirectory: File,
        hasContext: Boolean
    ): String {
        if (matches.isEmpty()) {
            return "No matches found for pattern: $pattern\nSearched in: ${baseDirectory.absolutePath}"
        }
        
        val header = """
Pattern: $pattern
Base: ${baseDirectory.absolutePath}
Found: ${matches.size} matches in ${matches.map { it.file }.distinct().size} files

        """.trimIndent()
        
        val results = matches.groupBy { it.file }.map { (file, fileMatches) ->
            val relativePath = if (file.startsWith(baseDirectory.absolutePath)) {
                "." + file.removePrefix(baseDirectory.absolutePath)
            } else {
                file
            }
            
            val fileHeader = "=== $relativePath ==="
            val matchLines = fileMatches.map { match ->
                val lineStr = "${match.lineNumber}:".padStart(6)
                val content = if (hasContext) {
                    buildString {
                        // Before context
                        match.beforeContext.forEachIndexed { index, contextLine ->
                            val contextLineNum = match.lineNumber - match.beforeContext.size + index
                            appendLine("${contextLineNum}:".padStart(6) + " " + contextLine.take(MAX_LINE_LENGTH))
                        }
                        // Matching line
                        appendLine("$lineStr ${match.line}")
                        // After context
                        match.afterContext.forEachIndexed { index, contextLine ->
                            val contextLineNum = match.lineNumber + 1 + index
                            appendLine("${contextLineNum}:".padStart(6) + " " + contextLine.take(MAX_LINE_LENGTH))
                        }
                    }.trimEnd()
                } else {
                    "$lineStr ${match.line}"
                }
                content
            }
            
            fileHeader + "\n" + matchLines.joinToString("\n") + "\n"
        }
        
        return header + results.joinToString("\n")
    }
    
    /**
     * Format file size in human-readable format
     */
    private fun formatFileSize(bytes: Long): String {
        if (bytes == 0L) return "0 B"
        
        val units = arrayOf("B", "KB", "MB", "GB")
        var size = bytes.toDouble()
        var unitIndex = 0
        
        while (size >= 1024 && unitIndex < units.size - 1) {
            size /= 1024
            unitIndex++
        }
        
        return "%.1f %s".format(size, units[unitIndex])
    }
    
    /**
     * Validate path for security
     */
    private fun validatePathSecurity(path: String): String? {
        val normalizedPath = path.lowercase()
        for (restrictedPath in RESTRICTED_PATHS) {
            if (normalizedPath.startsWith(restrictedPath)) {
                return "Access denied: Cannot search restricted system path: $restrictedPath"
            }
        }
        
        if (path.contains("..")) {
            return "Security error: Path traversal not allowed"
        }
        
        return null
    }
    
    /**
     * Resolve directory path handling Android-specific storage locations
     */
    private fun resolveDirectoryPath(path: String): File {
        return when {
            path == "." || path.isEmpty() -> context.filesDir
            path == ".." -> context.filesDir.parentFile ?: context.filesDir
            path == "~" -> Environment.getExternalStorageDirectory()
            path.startsWith("/") -> File(path)
            
            // Common Android directories
            path == "Downloads" || path == "Download" -> 
                File(Environment.getExternalStorageDirectory(), "Download")
            path == "Documents" -> 
                File(Environment.getExternalStorageDirectory(), "Documents")
            
            path.startsWith("~/") -> {
                val relativePath = path.removePrefix("~/")
                File(Environment.getExternalStorageDirectory(), relativePath)
            }
            
            else -> File(context.filesDir, path)
        }
    }
    
    override fun getDescription(params: Map<String, Any>): String {
        val pattern = params["pattern"] as? String ?: "unknown"
        val path = params["path"] as? String ?: "current directory"
        return "Searching for '$pattern' in files under $path"
    }
    
    override fun validateParams(params: Map<String, Any>): String? {
        val pattern = params["pattern"] as? String
        val contextBefore = params["contextBefore"] as? Number
        val contextAfter = params["contextAfter"] as? Number
        val limit = params["limit"] as? Number
        
        return when {
            pattern.isNullOrBlank() -> "Pattern parameter is required"
            contextBefore != null && contextBefore.toInt() < 0 -> "contextBefore cannot be negative"
            contextAfter != null && contextAfter.toInt() < 0 -> "contextAfter cannot be negative" 
            contextBefore != null && contextBefore.toInt() > 10 -> "contextBefore too large (max: 10)"
            contextAfter != null && contextAfter.toInt() > 10 -> "contextAfter too large (max: 10)"
            limit != null && limit.toInt() < 1 -> "limit must be positive"
            limit != null && limit.toInt() > MAX_MATCHES -> "limit too large (max: $MAX_MATCHES)"
            else -> null
        }
    }
    
    override fun getParameterSchema(): String {
        return """
            Parameters:
            - pattern (required, string): Text pattern to search for
              • Use literal text: "function main"
              • Case sensitive by default
            - path (optional, string): Base directory to search (default: current directory)
              • Use shortcuts: ~, Downloads, Documents
            - filePattern (optional, string): Glob pattern to filter files to search
              • Examples: "*.kt", "*.{java,kt}", "test_*.txt"
            - recursive (optional, boolean): Search subdirectories (default: true)
            - ignoreCase (optional, boolean): Case-insensitive search (default: false)
            - wholeWord (optional, boolean): Match whole words only (default: false)
            - contextBefore (optional, number): Lines of context before match (default: 0, max: 10)
            - contextAfter (optional, number): Lines of context after match (default: 0, max: 10)
            - maxDepth (optional, number): Maximum directory depth (default: 10)
            - limit (optional, number): Maximum matches to return (default: 1000)
            - encoding (optional, string): Text encoding (default: UTF-8)
              
            Examples:
            {"pattern": "TODO", "path": "~/Documents", "filePattern": "*.kt"}
            {"pattern": "class MainActivity", "recursive": true, "ignoreCase": true}
            {"pattern": "error", "contextBefore": 2, "contextAfter": 2, "limit": 50}
        """.trimIndent()
    }
}