package com.yourname.coquettemobile.core.tools

import android.content.Context
import android.os.Environment
import com.yourname.coquettemobile.core.logging.CoquetteLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.*
import javax.inject.Inject

/**
 * ListTool - Browse file system directories
 * Based on desktop Coquette's ls.ts patterns
 * Handles Android scoped storage and permissions
 */
class ListTool @Inject constructor(
    private val context: Context,
    private val logger: CoquetteLogger
) : MobileTool {
    
    override val name = "ListTool"
    override val description = "List directory contents (files and folders) with details"
    override val riskLevel = RiskLevel.LOW // Read-only directory browsing
    override val requiredPermissions = listOf(
        "android.permission.READ_EXTERNAL_STORAGE"
    )
    
    companion object {
        private const val MAX_FILES = 1000 // Limit to prevent memory issues
        
        // Restricted directories for security
        private val RESTRICTED_PATHS = setOf(
            "/system", "/root", "/proc", "/dev", "/sys", 
            "/data/system", "/data/misc", "/cache"
        )
    }
    
    enum class SortBy {
        NAME, SIZE, DATE, TYPE
    }
    
    enum class SortOrder {
        ASC, DESC
    }
    
    data class FileEntry(
        val name: String,
        val path: String,
        val isDirectory: Boolean,
        val size: Long,
        val lastModified: Long,
        val canRead: Boolean,
        val canWrite: Boolean,
        val isHidden: Boolean,
        val extension: String
    )
    
    override suspend fun execute(params: Map<String, Any>): ToolResult = withContext(Dispatchers.IO) {
        val dirPath = params["path"] as? String ?: "."
        
        try {
            // Security validation
            val securityError = validateDirectorySecurity(dirPath)
            if (securityError != null) {
                return@withContext ToolResult.error(securityError)
            }
            
            val directory = resolveDirectoryPath(dirPath)
            
            // Check directory existence and readability
            when {
                !directory.exists() -> return@withContext ToolResult.error(
                    "Directory not found: $dirPath"
                )
                !directory.isDirectory() -> return@withContext ToolResult.error(
                    "Path is not a directory: $dirPath (use FileTool to read files)"
                )
                !directory.canRead() -> return@withContext ToolResult.error(
                    "Cannot read directory: $dirPath (permission denied)"
                )
            }
            
            // Get listing parameters
            val sortBy = SortBy.valueOf((params["sortBy"] as? String)?.uppercase() ?: "NAME")
            val sortOrder = SortOrder.valueOf((params["sortOrder"] as? String)?.uppercase() ?: "ASC")
            val showHidden = params["showHidden"] as? Boolean ?: false
            val details = params["details"] as? Boolean ?: true
            val filter = params["filter"] as? String // regex filter
            val limit = (params["limit"] as? Number)?.toInt() ?: MAX_FILES
            
            // List directory contents
            val files = directory.listFiles() ?: emptyArray()
            
            if (files.size > MAX_FILES) {
                logger.w("ListTool", "Directory has ${files.size} files, limiting to $MAX_FILES")
            }
            
            // Convert to FileEntry objects
            var entries = files.take(limit).map { file ->
                FileEntry(
                    name = file.name,
                    path = file.absolutePath,
                    isDirectory = file.isDirectory(),
                    size = if (file.isDirectory()) 0 else file.length(),
                    lastModified = file.lastModified(),
                    canRead = file.canRead(),
                    canWrite = file.canWrite(),
                    isHidden = file.isHidden() || file.name.startsWith("."),
                    extension = file.extension.takeIf { it.isNotEmpty() } ?: ""
                )
            }
            
            // Apply filters
            if (!showHidden) {
                entries = entries.filter { !it.isHidden }
            }
            
            if (!filter.isNullOrBlank()) {
                try {
                    val regex = Regex(filter, RegexOption.IGNORE_CASE)
                    entries = entries.filter { regex.containsMatchIn(it.name) }
                } catch (e: Exception) {
                    // Invalid regex - treat as simple contains filter
                    entries = entries.filter { it.name.contains(filter, ignoreCase = true) }
                }
            }
            
            // Apply sorting
            entries = when (sortBy) {
                SortBy.NAME -> entries.sortedBy { it.name.lowercase() }
                SortBy.SIZE -> entries.sortedBy { it.size }
                SortBy.DATE -> entries.sortedBy { it.lastModified }
                SortBy.TYPE -> entries.sortedBy { 
                    if (it.isDirectory) "0" else it.extension.lowercase()
                }
            }
            
            if (sortOrder == SortOrder.DESC) {
                entries = entries.reversed()
            }
            
            // Format output
            val output = if (details) {
                formatDetailedListing(entries, directory)
            } else {
                formatSimpleListing(entries)
            }
            
            val metadata = mapOf(
                "directory" to directory.absolutePath,
                "totalFiles" to files.size,
                "filteredFiles" to entries.size,
                "directories" to entries.count { it.isDirectory },
                "files" to entries.count { !it.isDirectory },
                "sortBy" to sortBy.name,
                "sortOrder" to sortOrder.name,
                "showHidden" to showHidden,
                "filter" to (filter ?: ""),
                "hasMore" to (files.size > limit)
            )
            
            ToolResult.success(output, metadata)
            
        } catch (e: SecurityException) {
            ToolResult.error("Security error: ${e.message}")
        } catch (e: Exception) {
            logger.e("ListTool", "Error listing directory $dirPath: ${e.message}")
            ToolResult.error("Failed to list directory: ${e.message}")
        }
    }
    
    override suspend fun executeStreaming(
        params: Map<String, Any>,
        onProgress: (String) -> Unit
    ): ToolResult = withContext(Dispatchers.IO) {
        val dirPath = params["path"] as? String ?: "."
        
        onProgress("Listing directory: $dirPath")
        
        try {
            // Security validation
            val securityError = validateDirectorySecurity(dirPath)
            if (securityError != null) {
                onProgress("Security check failed")
                return@withContext ToolResult.error(securityError)
            }
            
            val directory = resolveDirectoryPath(dirPath)
            
            onProgress("Checking directory accessibility...")
            
            // Check directory existence and readability
            when {
                !directory.exists() -> {
                    onProgress("Directory not found")
                    return@withContext ToolResult.error("Directory not found: $dirPath")
                }
                !directory.isDirectory() -> {
                    onProgress("Path is not a directory")
                    return@withContext ToolResult.error("Path is not a directory: $dirPath (use FileTool to read files)")
                }
                !directory.canRead() -> {
                    onProgress("Permission denied")
                    return@withContext ToolResult.error("Cannot read directory: $dirPath (permission denied)")
                }
            }
            
            onProgress("Reading directory contents...")
            
            // Get listing parameters
            val sortBy = SortBy.valueOf((params["sortBy"] as? String)?.uppercase() ?: "NAME")
            val sortOrder = SortOrder.valueOf((params["sortOrder"] as? String)?.uppercase() ?: "ASC")
            val showHidden = params["showHidden"] as? Boolean ?: false
            val details = params["details"] as? Boolean ?: true
            val filter = params["filter"] as? String
            val limit = (params["limit"] as? Number)?.toInt() ?: MAX_FILES
            
            // List directory contents
            val files = directory.listFiles() ?: emptyArray()
            
            onProgress("Found ${files.size} items")
            
            if (files.size > MAX_FILES) {
                onProgress("Large directory - limiting to $MAX_FILES items")
            }
            
            onProgress("Processing file information...")
            
            // Convert to FileEntry objects
            var entries = files.take(limit).map { file ->
                FileEntry(
                    name = file.name,
                    path = file.absolutePath,
                    isDirectory = file.isDirectory(),
                    size = if (file.isDirectory()) 0 else file.length(),
                    lastModified = file.lastModified(),
                    canRead = file.canRead(),
                    canWrite = file.canWrite(),
                    isHidden = file.isHidden() || file.name.startsWith("."),
                    extension = file.extension.takeIf { it.isNotEmpty() } ?: ""
                )
            }
            
            // Apply filters
            if (!showHidden) {
                entries = entries.filter { !it.isHidden }
                onProgress("Filtered out hidden files")
            }
            
            if (!filter.isNullOrBlank()) {
                val beforeCount = entries.size
                try {
                    val regex = Regex(filter, RegexOption.IGNORE_CASE)
                    entries = entries.filter { regex.containsMatchIn(it.name) }
                } catch (e: Exception) {
                    entries = entries.filter { it.name.contains(filter, ignoreCase = true) }
                }
                onProgress("Applied filter: ${beforeCount} -> ${entries.size} items")
            }
            
            onProgress("Sorting by ${sortBy.name} (${sortOrder.name})...")
            
            // Apply sorting
            entries = when (sortBy) {
                SortBy.NAME -> entries.sortedBy { it.name.lowercase() }
                SortBy.SIZE -> entries.sortedBy { it.size }
                SortBy.DATE -> entries.sortedBy { it.lastModified }
                SortBy.TYPE -> entries.sortedBy { 
                    if (it.isDirectory) "0" else it.extension.lowercase()
                }
            }
            
            if (sortOrder == SortOrder.DESC) {
                entries = entries.reversed()
            }
            
            onProgress("Formatting output (${entries.size} items)...")
            
            // Format output
            val output = if (details) {
                formatDetailedListing(entries, directory)
            } else {
                formatSimpleListing(entries)
            }
            
            val metadata = mapOf(
                "directory" to directory.absolutePath,
                "totalFiles" to files.size,
                "filteredFiles" to entries.size,
                "directories" to entries.count { it.isDirectory },
                "files" to entries.count { !it.isDirectory },
                "sortBy" to sortBy.name,
                "sortOrder" to sortOrder.name,
                "showHidden" to showHidden,
                "filter" to (filter ?: ""),
                "hasMore" to (files.size > limit)
            )
            
            onProgress("Directory listing complete")
            ToolResult.success(output, metadata)
            
        } catch (e: SecurityException) {
            val error = "Security error: ${e.message}"
            onProgress(error)
            ToolResult.error(error)
        } catch (e: Exception) {
            logger.e("ListTool", "Error listing directory $dirPath: ${e.message}")
            val error = "Failed to list directory: ${e.message}"
            onProgress(error)
            ToolResult.error(error)
        }
    }
    
    /**
     * Validate directory path for security
     */
    private fun validateDirectorySecurity(dirPath: String): String? {
        // Check for restricted paths
        val normalizedPath = dirPath.lowercase()
        for (restrictedPath in RESTRICTED_PATHS) {
            if (normalizedPath.startsWith(restrictedPath)) {
                return "Access denied: Cannot list restricted system path: $restrictedPath"
            }
        }
        
        // Check for path traversal attempts
        if (dirPath.contains("..")) {
            return "Security error: Path traversal not allowed"
        }
        
        return null
    }
    
    /**
     * Resolve directory path handling Android-specific storage locations
     */
    private fun resolveDirectoryPath(path: String): File {
        return when {
            // Current directory shortcut
            path == "." || path.isEmpty() -> context.filesDir
            
            // Parent directory shortcut
            path == ".." -> context.filesDir.parentFile ?: context.filesDir
            
            // Home directory shortcut
            path == "~" -> Environment.getExternalStorageDirectory()
            
            // Absolute path
            path.startsWith("/") -> File(path)
            
            // Common Android directories
            path == "Downloads" || path == "Download" -> 
                File(Environment.getExternalStorageDirectory(), "Download")
            path == "Documents" -> 
                File(Environment.getExternalStorageDirectory(), "Documents")
            path == "Pictures" -> 
                File(Environment.getExternalStorageDirectory(), "Pictures")
            path == "Music" -> 
                File(Environment.getExternalStorageDirectory(), "Music")
            path == "Movies" -> 
                File(Environment.getExternalStorageDirectory(), "Movies")
            
            // Relative to external storage
            path.startsWith("~/") -> {
                val relativePath = path.removePrefix("~/")
                File(Environment.getExternalStorageDirectory(), relativePath)
            }
            
            // App internal storage (safest default)
            else -> File(context.filesDir, path)
        }
    }
    
    /**
     * Format detailed directory listing
     */
    private fun formatDetailedListing(entries: List<FileEntry>, directory: File): String {
        val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
        val maxNameLength = entries.maxOfOrNull { it.name.length }?.coerceAtMost(50) ?: 20
        
        val header = """
Directory: ${directory.absolutePath}
Total: ${entries.size} items (${entries.count { it.isDirectory }} directories, ${entries.count { !it.isDirectory }} files)

${"Type".padEnd(4)} ${"Permissions".padEnd(11)} ${"Size".padStart(10)} ${"Modified".padEnd(16)} Name
${"----".padEnd(4)} ${"-----------".padEnd(11)} ${"----------".padStart(10)} ${"----------------".padEnd(16)} ----
        """.trimIndent()
        
        val lines = entries.map { entry ->
            val type = if (entry.isDirectory) "DIR" else "FILE"
            val permissions = buildString {
                append(if (entry.canRead) "r" else "-")
                append(if (entry.canWrite) "w" else "-")
                append(if (entry.isDirectory) "x" else "-")
                append(if (entry.isHidden) "h" else "-")
            }
            val size = if (entry.isDirectory) "" else formatFileSize(entry.size)
            val date = dateFormat.format(Date(entry.lastModified))
            val name = entry.name.take(maxNameLength) + if (entry.name.length > maxNameLength) "..." else ""
            
            "${type.padEnd(4)} ${permissions.padEnd(11)} ${size.padStart(10)} ${date.padEnd(16)} $name"
        }
        
        return header + "\n" + lines.joinToString("\n")
    }
    
    /**
     * Format simple directory listing
     */
    private fun formatSimpleListing(entries: List<FileEntry>): String {
        return entries.joinToString("\n") { entry ->
            if (entry.isDirectory) {
                "${entry.name}/"
            } else {
                entry.name
            }
        }
    }
    
    /**
     * Format file size in human-readable format
     */
    private fun formatFileSize(bytes: Long): String {
        if (bytes == 0L) return "0 B"
        
        val units = arrayOf("B", "KB", "MB", "GB", "TB")
        var size = bytes.toDouble()
        var unitIndex = 0
        
        while (size >= 1024 && unitIndex < units.size - 1) {
            size /= 1024
            unitIndex++
        }
        
        return if (unitIndex == 0) {
            "${size.toInt()} ${units[unitIndex]}"
        } else {
            "%.1f %s".format(size, units[unitIndex])
        }
    }
    
    override fun getDescription(params: Map<String, Any>): String {
        val path = params["path"] as? String ?: "current directory"
        return "Listing directory: $path"
    }
    
    override fun validateParams(params: Map<String, Any>): String? {
        val sortBy = params["sortBy"] as? String
        val sortOrder = params["sortOrder"] as? String
        val limit = params["limit"] as? Number
        
        return when {
            sortBy != null && !listOf("name", "size", "date", "type").contains(sortBy.lowercase()) -> 
                "Invalid sortBy value. Must be one of: name, size, date, type"
            sortOrder != null && !listOf("asc", "desc").contains(sortOrder.lowercase()) -> 
                "Invalid sortOrder value. Must be one of: asc, desc"
            limit != null && limit.toInt() < 1 -> 
                "Limit must be positive"
            limit != null && limit.toInt() > MAX_FILES -> 
                "Limit too large (max: $MAX_FILES)"
            else -> null
        }
    }
    
    override fun getParameterSchema(): String {
        return """
            Parameters:
            - path (optional, string): Directory path to list (default: current directory)
              • Use absolute paths: /storage/emulated/0/Documents
              • Use relative paths: Documents, Pictures
              • Use shortcuts: ~, ~/Downloads, .
              • Use common names: Downloads, Documents, Pictures, Music, Movies
            - sortBy (optional, string): Sort criterion (default: name)
              • Options: name, size, date, type
            - sortOrder (optional, string): Sort order (default: asc)
              • Options: asc, desc
            - details (optional, boolean): Show detailed listing (default: true)
            - showHidden (optional, boolean): Show hidden files (default: false)  
            - filter (optional, string): Filter files by name pattern (regex supported)
            - limit (optional, number): Max number of files to show (default: 1000)
              
            Examples:
            {"path": "Downloads", "sortBy": "date", "sortOrder": "desc"}
            {"path": "~/Documents", "details": true, "showHidden": false}
            {"path": "/storage/emulated/0/Pictures", "filter": ".*\\.jpg$", "limit": 50}
        """.trimIndent()
    }
}