package com.yourname.coquettemobile.core.tools

import android.content.Context
import android.os.Environment
import com.yourname.coquettemobile.core.logging.CoquetteLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.*
import java.util.regex.Pattern
import javax.inject.Inject

/**
 * GlobTool - Find files by pattern matching
 * Based on desktop Coquette's glob.ts patterns
 * Android equivalent of `find . -name "pattern"` with glob support
 */
class GlobTool @Inject constructor(
    private val context: Context,
    private val logger: CoquetteLogger
) : MobileTool {
    
    override val name = "GlobTool"
    override val description = "Find files by name patterns (glob/wildcard matching)"
    override val riskLevel = RiskLevel.LOW // Read-only file discovery
    override val requiredPermissions = listOf(
        "android.permission.READ_EXTERNAL_STORAGE"
    )
    
    companion object {
        private const val MAX_RESULTS = 1000 // Prevent memory issues
        private const val MAX_DEPTH = 10 // Prevent infinite recursion
        
        // Restricted directories for security
        private val RESTRICTED_PATHS = setOf(
            "/system", "/root", "/proc", "/dev", "/sys", 
            "/data/system", "/data/misc", "/cache"
        )
    }
    
    data class GlobMatch(
        val path: String,
        val name: String,
        val isDirectory: Boolean,
        val size: Long,
        val lastModified: Long,
        val depth: Int,
        val parent: String
    )
    
    override suspend fun execute(params: Map<String, Any>): ToolResult = withContext(Dispatchers.IO) {
        val pattern = params["pattern"] as? String
            ?: return@withContext ToolResult.error("Missing required parameter: pattern")
        
        val basePath = params["path"] as? String ?: "."
        val recursive = params["recursive"] as? Boolean ?: true
        val maxDepth = ((params["maxDepth"] as? Number)?.toInt() ?: MAX_DEPTH).coerceAtMost(MAX_DEPTH)
        val includeHidden = params["includeHidden"] as? Boolean ?: false
        val filesOnly = params["filesOnly"] as? Boolean ?: false
        val dirsOnly = params["dirsOnly"] as? Boolean ?: false
        val ignoreCase = params["ignoreCase"] as? Boolean ?: true
        val limit = ((params["limit"] as? Number)?.toInt() ?: MAX_RESULTS).coerceAtMost(MAX_RESULTS)
        
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
                !baseDirectory.isDirectory() -> return@withContext ToolResult.error(
                    "Base path is not a directory: $basePath"
                )
                !baseDirectory.canRead() -> return@withContext ToolResult.error(
                    "Cannot read base directory: $basePath (permission denied)"
                )
            }
            
            // Convert glob pattern to regex
            val regex = try {
                globToRegex(pattern, ignoreCase)
            } catch (e: Exception) {
                return@withContext ToolResult.error("Invalid pattern: $pattern - ${e.message}")
            }
            
            // Search for matching files
            val matches = mutableListOf<GlobMatch>()
            val searchStartTime = System.currentTimeMillis()
            
            searchFiles(
                directory = baseDirectory,
                regex = regex,
                matches = matches,
                currentDepth = 0,
                maxDepth = if (recursive) maxDepth else 0,
                includeHidden = includeHidden,
                filesOnly = filesOnly,
                dirsOnly = dirsOnly,
                limit = limit,
                basePath = baseDirectory.absolutePath
            )
            
            val searchTime = System.currentTimeMillis() - searchStartTime
            
            // Sort results by path
            matches.sortBy { it.path }
            
            // Format output
            val output = formatGlobResults(matches, pattern, baseDirectory)
            
            val metadata = mapOf(
                "pattern" to pattern,
                "basePath" to baseDirectory.absolutePath,
                "totalMatches" to matches.size,
                "searchTime" to searchTime,
                "recursive" to recursive,
                "maxDepth" to maxDepth,
                "includeHidden" to includeHidden,
                "filesOnly" to filesOnly,
                "dirsOnly" to dirsOnly,
                "ignoreCase" to ignoreCase,
                "truncated" to (matches.size >= limit)
            )
            
            ToolResult.success(output, metadata)
            
        } catch (e: SecurityException) {
            ToolResult.error("Security error: ${e.message}")
        } catch (e: Exception) {
            logger.e("GlobTool", "Error during glob search $pattern: ${e.message}")
            ToolResult.error("Failed to search files: ${e.message}")
        }
    }
    
    override suspend fun executeStreaming(
        params: Map<String, Any>,
        onProgress: (String) -> Unit
    ): ToolResult = withContext(Dispatchers.IO) {
        val pattern = params["pattern"] as? String
            ?: return@withContext ToolResult.error("Missing required parameter: pattern")
        
        val basePath = params["path"] as? String ?: "."
        val recursive = params["recursive"] as? Boolean ?: true
        val maxDepth = ((params["maxDepth"] as? Number)?.toInt() ?: MAX_DEPTH).coerceAtMost(MAX_DEPTH)
        val includeHidden = params["includeHidden"] as? Boolean ?: false
        val filesOnly = params["filesOnly"] as? Boolean ?: false
        val dirsOnly = params["dirsOnly"] as? Boolean ?: false
        val ignoreCase = params["ignoreCase"] as? Boolean ?: true
        val limit = ((params["limit"] as? Number)?.toInt() ?: MAX_RESULTS).coerceAtMost(MAX_RESULTS)
        
        onProgress("Starting glob search for pattern: $pattern")
        
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
                !baseDirectory.isDirectory() -> {
                    onProgress("Path is not a directory")
                    return@withContext ToolResult.error("Base path is not a directory: $basePath")
                }
                !baseDirectory.canRead() -> {
                    onProgress("Permission denied")
                    return@withContext ToolResult.error("Cannot read base directory: $basePath (permission denied)")
                }
            }
            
            onProgress("Converting glob pattern to regex...")
            
            // Convert glob pattern to regex
            val regex = try {
                globToRegex(pattern, ignoreCase)
            } catch (e: Exception) {
                val error = "Invalid pattern: $pattern - ${e.message}"
                onProgress(error)
                return@withContext ToolResult.error(error)
            }
            
            onProgress("Searching for matches (max depth: ${if (recursive) maxDepth else 0})...")
            
            // Search for matching files with progress updates
            val matches = mutableListOf<GlobMatch>()
            val searchStartTime = System.currentTimeMillis()
            var lastProgressUpdate = 0L
            
            searchFilesWithProgress(
                directory = baseDirectory,
                regex = regex,
                matches = matches,
                currentDepth = 0,
                maxDepth = if (recursive) maxDepth else 0,
                includeHidden = includeHidden,
                filesOnly = filesOnly,
                dirsOnly = dirsOnly,
                limit = limit,
                basePath = baseDirectory.absolutePath,
                onProgress = { matchCount, currentPath ->
                    val now = System.currentTimeMillis()
                    if (now - lastProgressUpdate > 1000 || matchCount % 50 == 0) { // Update every second or 50 matches
                        onProgress("Found $matchCount matches, scanning: ${currentPath.takeLast(50)}")
                        lastProgressUpdate = now
                    }
                }
            )
            
            val searchTime = System.currentTimeMillis() - searchStartTime
            
            onProgress("Search complete: ${matches.size} matches found in ${searchTime}ms")
            
            // Sort results by path
            matches.sortBy { it.path }
            onProgress("Sorting and formatting results...")
            
            // Format output
            val output = formatGlobResults(matches, pattern, baseDirectory)
            
            val metadata = mapOf(
                "pattern" to pattern,
                "basePath" to baseDirectory.absolutePath,
                "totalMatches" to matches.size,
                "searchTime" to searchTime,
                "recursive" to recursive,
                "maxDepth" to maxDepth,
                "includeHidden" to includeHidden,
                "filesOnly" to filesOnly,
                "dirsOnly" to dirsOnly,
                "ignoreCase" to ignoreCase,
                "truncated" to (matches.size >= limit)
            )
            
            ToolResult.success(output, metadata)
            
        } catch (e: SecurityException) {
            val error = "Security error: ${e.message}"
            onProgress(error)
            ToolResult.error(error)
        } catch (e: Exception) {
            logger.e("GlobTool", "Error during glob search $pattern: ${e.message}")
            val error = "Failed to search files: ${e.message}"
            onProgress(error)
            ToolResult.error(error)
        }
    }
    
    /**
     * Recursively search for files matching the pattern
     */
    private fun searchFiles(
        directory: File,
        regex: Regex,
        matches: MutableList<GlobMatch>,
        currentDepth: Int,
        maxDepth: Int,
        includeHidden: Boolean,
        filesOnly: Boolean,
        dirsOnly: Boolean,
        limit: Int,
        basePath: String
    ) {
        if (matches.size >= limit || currentDepth > maxDepth) return
        
        try {
            val files = directory.listFiles() ?: return
            
            for (file in files) {
                if (matches.size >= limit) break
                
                // Skip hidden files if not requested
                if (!includeHidden && (file.isHidden || file.name.startsWith("."))) {
                    continue
                }
                
                // Check if filename matches pattern
                val isMatch = regex.containsMatchIn(file.name)
                
                if (isMatch) {
                    // Apply file type filters
                    val shouldInclude = when {
                        filesOnly && file.isDirectory() -> false
                        dirsOnly && !file.isDirectory() -> false
                        else -> true
                    }
                    
                    if (shouldInclude) {
                        matches.add(
                            GlobMatch(
                                path = file.absolutePath,
                                name = file.name,
                                isDirectory = file.isDirectory(),
                                size = if (file.isDirectory()) 0 else file.length(),
                                lastModified = file.lastModified(),
                                depth = currentDepth,
                                parent = file.parent ?: ""
                            )
                        )
                    }
                }
                
                // Recurse into subdirectories
                if (file.isDirectory() && currentDepth < maxDepth && file.canRead()) {
                    searchFiles(
                        file, regex, matches, currentDepth + 1, maxDepth,
                        includeHidden, filesOnly, dirsOnly, limit, basePath
                    )
                }
            }
        } catch (e: SecurityException) {
            // Skip directories we can't read
            logger.d("GlobTool", "Skipping directory due to permissions: ${directory.absolutePath}")
        }
    }
    
    /**
     * Search with progress updates
     */
    private fun searchFilesWithProgress(
        directory: File,
        regex: Regex,
        matches: MutableList<GlobMatch>,
        currentDepth: Int,
        maxDepth: Int,
        includeHidden: Boolean,
        filesOnly: Boolean,
        dirsOnly: Boolean,
        limit: Int,
        basePath: String,
        onProgress: (Int, String) -> Unit
    ) {
        if (matches.size >= limit || currentDepth > maxDepth) return
        
        try {
            val files = directory.listFiles() ?: return
            onProgress(matches.size, directory.absolutePath)
            
            for (file in files) {
                if (matches.size >= limit) break
                
                // Skip hidden files if not requested
                if (!includeHidden && (file.isHidden || file.name.startsWith("."))) {
                    continue
                }
                
                // Check if filename matches pattern
                val isMatch = regex.containsMatchIn(file.name)
                
                if (isMatch) {
                    // Apply file type filters
                    val shouldInclude = when {
                        filesOnly && file.isDirectory() -> false
                        dirsOnly && !file.isDirectory() -> false
                        else -> true
                    }
                    
                    if (shouldInclude) {
                        matches.add(
                            GlobMatch(
                                path = file.absolutePath,
                                name = file.name,
                                isDirectory = file.isDirectory(),
                                size = if (file.isDirectory()) 0 else file.length(),
                                lastModified = file.lastModified(),
                                depth = currentDepth,
                                parent = file.parent ?: ""
                            )
                        )
                    }
                }
                
                // Recurse into subdirectories
                if (file.isDirectory() && currentDepth < maxDepth && file.canRead()) {
                    searchFilesWithProgress(
                        file, regex, matches, currentDepth + 1, maxDepth,
                        includeHidden, filesOnly, dirsOnly, limit, basePath, onProgress
                    )
                }
            }
        } catch (e: SecurityException) {
            // Skip directories we can't read
            logger.d("GlobTool", "Skipping directory due to permissions: ${directory.absolutePath}")
        }
    }
    
    /**
     * Convert glob pattern to regex
     */
    private fun globToRegex(pattern: String, ignoreCase: Boolean): Regex {
        val regexPattern = pattern
            .replace(".", "\\.")      // Escape dots
            .replace("*", ".*")       // * matches any characters
            .replace("?", ".")        // ? matches single character
            .replace("[", "\\[")      // Escape brackets (basic implementation)
            .replace("]", "\\]")
        
        val options = if (ignoreCase) {
            setOf(RegexOption.IGNORE_CASE)
        } else {
            emptySet()
        }
        
        return Regex("^$regexPattern$", options)
    }
    
    /**
     * Format glob search results
     */
    private fun formatGlobResults(
        matches: List<GlobMatch>,
        pattern: String,
        baseDirectory: File
    ): String {
        if (matches.isEmpty()) {
            return "No files found matching pattern: $pattern\nSearched in: ${baseDirectory.absolutePath}"
        }
        
        val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
        
        val header = """
Pattern: $pattern
Base: ${baseDirectory.absolutePath}
Found: ${matches.size} matches

Type  Size      Modified         Path
----  --------  ---------------  ----
        """.trimIndent()
        
        val lines = matches.map { match ->
            val type = if (match.isDirectory) "DIR " else "FILE"
            val size = if (match.isDirectory) "        " else formatFileSize(match.size).padStart(8)
            val date = dateFormat.format(Date(match.lastModified))
            val relativePath = if (match.path.startsWith(baseDirectory.absolutePath)) {
                "." + match.path.removePrefix(baseDirectory.absolutePath)
            } else {
                match.path
            }
            
            "$type  $size  $date  $relativePath"
        }
        
        return header + "\n" + lines.joinToString("\n")
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
        
        return if (unitIndex == 0) {
            "${size.toInt()}"
        } else {
            "%.1f%s".format(size, units[unitIndex])
        }
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
            path == "Pictures" -> 
                File(Environment.getExternalStorageDirectory(), "Pictures")
            
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
        return "Finding files matching '$pattern' in $path"
    }
    
    override fun validateParams(params: Map<String, Any>): String? {
        val pattern = params["pattern"] as? String
        val maxDepth = params["maxDepth"] as? Number
        val limit = params["limit"] as? Number
        
        return when {
            pattern.isNullOrBlank() -> "Pattern parameter is required"
            maxDepth != null && maxDepth.toInt() < 0 -> "maxDepth cannot be negative"
            maxDepth != null && maxDepth.toInt() > MAX_DEPTH -> "maxDepth too large (max: $MAX_DEPTH)"
            limit != null && limit.toInt() < 1 -> "limit must be positive"
            limit != null && limit.toInt() > MAX_RESULTS -> "limit too large (max: $MAX_RESULTS)"
            else -> null
        }
    }
    
    override fun getParameterSchema(): String {
        return """
            Parameters:
            - pattern (required, string): Glob pattern to match filenames
              • Use * for any characters: *.kt, test*.txt
              • Use ? for single character: file?.log
              • Examples: "*.jpg", "config.*", "test_*.txt"
            - path (optional, string): Base directory to search (default: current directory)
              • Use shortcuts: ~, Downloads, Documents
              • Use absolute paths: /storage/emulated/0/Documents
            - recursive (optional, boolean): Search subdirectories (default: true)
            - maxDepth (optional, number): Maximum recursion depth (default: 10)
            - includeHidden (optional, boolean): Include hidden files (default: false)
            - filesOnly (optional, boolean): Only return files, not directories (default: false)
            - dirsOnly (optional, boolean): Only return directories, not files (default: false)
            - ignoreCase (optional, boolean): Case-insensitive matching (default: true)
            - limit (optional, number): Maximum results to return (default: 1000)
              
            Examples:
            {"pattern": "*.kt", "path": "~/Documents", "recursive": true}
            {"pattern": "config.*", "path": ".", "includeHidden": true}
            {"pattern": "test_*.txt", "filesOnly": true, "limit": 50}
        """.trimIndent()
    }
}