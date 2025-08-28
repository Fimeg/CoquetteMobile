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
 * EditTool - Modify existing files in-place
 * Based on desktop Coquette's edit.ts patterns
 * Android equivalent of sed/awk but AI-powered
 */
class EditTool @Inject constructor(
    private val context: Context,
    private val logger: CoquetteLogger
) : MobileTool {
    
    override val name = "EditTool"
    override val description = "Modify existing files in-place with find/replace, line insertion, or content transformation"
    override val riskLevel = RiskLevel.HIGH // File modification is high risk
    override val requiredPermissions = listOf(
        "android.permission.READ_EXTERNAL_STORAGE",
        "android.permission.WRITE_EXTERNAL_STORAGE"
    )
    
    companion object {
        private const val MAX_FILE_SIZE = 10_000_000 // 10MB limit
        private const val MAX_PATH_LENGTH = 500
    }
    
    enum class EditOperation {
        REPLACE, INSERT_LINE, APPEND_LINE, DELETE_LINE, REPLACE_LINE
    }
    
    override suspend fun execute(params: Map<String, Any>): ToolResult = withContext(Dispatchers.IO) {
        val filePath = params["path"] as? String
            ?: return@withContext ToolResult.error("Missing required parameter: path")
        
        val operation = params["operation"] as? String
            ?: return@withContext ToolResult.error("Missing required parameter: operation")
        
        try {
            val file = resolveFilePath(filePath)
            
            // Validate file
            when {
                !file.exists() -> return@withContext ToolResult.error("File not found: $filePath")
                !file.canRead() -> return@withContext ToolResult.error("Cannot read file: $filePath")
                !file.canWrite() -> return@withContext ToolResult.error("Cannot write to file: $filePath")
                file.length() > MAX_FILE_SIZE -> return@withContext ToolResult.error("File too large: ${file.length()} bytes")
                file.isDirectory() -> return@withContext ToolResult.error("Path is directory, not file: $filePath")
            }
            
            val encoding = params["encoding"] as? String ?: "UTF-8"
            val originalContent = file.readText(Charset.forName(encoding))
            val originalLines = originalContent.split('\n')
            
            // Create backup
            val backup = params["backup"] as? Boolean ?: true
            if (backup) {
                val backupFile = File(file.absolutePath + ".backup")
                backupFile.writeText(originalContent, Charset.forName(encoding))
            }
            
            // Perform edit operation
            val newContent = when (EditOperation.valueOf(operation.uppercase())) {
                EditOperation.REPLACE -> performReplace(originalContent, params)
                EditOperation.INSERT_LINE -> performInsertLine(originalLines, params)
                EditOperation.APPEND_LINE -> performAppendLine(originalLines, params)
                EditOperation.DELETE_LINE -> performDeleteLine(originalLines, params)
                EditOperation.REPLACE_LINE -> performReplaceLine(originalLines, params)
            }
            
            // Write modified content
            file.writeText(newContent, Charset.forName(encoding))
            
            // Verify changes
            val newSize = file.length()
            val linesChanged = originalLines.size - newContent.split('\n').size
            
            val metadata = mapOf(
                "path" to file.absolutePath,
                "operation" to operation,
                "originalSize" to originalContent.length,
                "newSize" to newSize,
                "originalLines" to originalLines.size,
                "newLines" to newContent.split('\n').size,
                "linesChanged" to linesChanged,
                "backupCreated" to backup,
                "encoding" to encoding
            )
            
            val summary = """
File edited successfully: ${file.absolutePath}
Operation: $operation
Size: ${originalContent.length} → ${newSize} bytes
Lines: ${originalLines.size} → ${newContent.split('\n').size}
            """.trimIndent()
            
            ToolResult.success(summary, metadata)
            
        } catch (e: Exception) {
            logger.e("EditTool", "Error editing file $filePath: ${e.message}")
            ToolResult.error("Failed to edit file: ${e.message}")
        }
    }
    
    override suspend fun executeStreaming(
        params: Map<String, Any>,
        onProgress: (String) -> Unit
    ): ToolResult = withContext(Dispatchers.IO) {
        val filePath = params["path"] as? String
            ?: return@withContext ToolResult.error("Missing required parameter: path")
        
        onProgress("Starting file edit: $filePath")
        
        // Delegate to main execute function for now - streaming could be added for large files
        execute(params)
    }
    
    private fun performReplace(content: String, params: Map<String, Any>): String {
        val findText = params["find"] as? String
            ?: throw IllegalArgumentException("Missing 'find' parameter for replace operation")
        val replaceText = params["replace"] as? String
            ?: throw IllegalArgumentException("Missing 'replace' parameter for replace operation")
        
        val ignoreCase = params["ignoreCase"] as? Boolean ?: false
        val replaceAll = params["replaceAll"] as? Boolean ?: true
        
        return if (replaceAll) {
            content.replace(findText, replaceText, ignoreCase = ignoreCase)
        } else {
            content.replaceFirst(findText, replaceText, ignoreCase = ignoreCase)
        }
    }
    
    private fun performInsertLine(lines: List<String>, params: Map<String, Any>): String {
        val lineNumber = (params["lineNumber"] as? Number)?.toInt()
            ?: throw IllegalArgumentException("Missing 'lineNumber' parameter for insert operation")
        val text = params["text"] as? String
            ?: throw IllegalArgumentException("Missing 'text' parameter for insert operation")
        
        val adjustedLineNumber = (lineNumber - 1).coerceIn(0, lines.size)
        val newLines = lines.toMutableList()
        newLines.add(adjustedLineNumber, text)
        return newLines.joinToString("\n")
    }
    
    private fun performAppendLine(lines: List<String>, params: Map<String, Any>): String {
        val text = params["text"] as? String
            ?: throw IllegalArgumentException("Missing 'text' parameter for append operation")
        
        return lines.joinToString("\n") + "\n" + text
    }
    
    private fun performDeleteLine(lines: List<String>, params: Map<String, Any>): String {
        val lineNumber = (params["lineNumber"] as? Number)?.toInt()
            ?: throw IllegalArgumentException("Missing 'lineNumber' parameter for delete operation")
        
        val adjustedLineNumber = lineNumber - 1
        if (adjustedLineNumber < 0 || adjustedLineNumber >= lines.size) {
            throw IllegalArgumentException("Line number $lineNumber out of range (1-${lines.size})")
        }
        
        val newLines = lines.toMutableList()
        newLines.removeAt(adjustedLineNumber)
        return newLines.joinToString("\n")
    }
    
    private fun performReplaceLine(lines: List<String>, params: Map<String, Any>): String {
        val lineNumber = (params["lineNumber"] as? Number)?.toInt()
            ?: throw IllegalArgumentException("Missing 'lineNumber' parameter for replace line operation")
        val text = params["text"] as? String
            ?: throw IllegalArgumentException("Missing 'text' parameter for replace line operation")
        
        val adjustedLineNumber = lineNumber - 1
        if (adjustedLineNumber < 0 || adjustedLineNumber >= lines.size) {
            throw IllegalArgumentException("Line number $lineNumber out of range (1-${lines.size})")
        }
        
        val newLines = lines.toMutableList()
        newLines[adjustedLineNumber] = text
        return newLines.joinToString("\n")
    }
    
    private fun resolveFilePath(path: String): File {
        return when {
            path.startsWith("/") -> File(path)
            path.startsWith("~/") -> {
                val relativePath = path.removePrefix("~/")
                File(Environment.getExternalStorageDirectory(), relativePath)
            }
            else -> File(context.filesDir, path)
        }
    }
    
    override fun getDescription(params: Map<String, Any>): String {
        val path = params["path"] as? String ?: "unknown"
        val operation = params["operation"] as? String ?: "unknown"
        return "Editing file: $path (operation: $operation)"
    }
    
    override fun validateParams(params: Map<String, Any>): String? {
        val path = params["path"] as? String
        val operation = params["operation"] as? String
        val lineNumber = params["lineNumber"] as? Number
        
        return when {
            path.isNullOrBlank() -> "Path parameter is required"
            operation.isNullOrBlank() -> "Operation parameter is required"
            operation.uppercase() !in EditOperation.values().map { it.name } -> 
                "Invalid operation. Must be one of: ${EditOperation.values().joinToString()}"
            lineNumber != null && lineNumber.toInt() < 1 -> "Line numbers start from 1"
            else -> null
        }
    }
    
    override fun getParameterSchema(): String {
        return """
            Parameters:
            - path (required, string): File path to edit
            - operation (required, string): Edit operation type
              • REPLACE: Find and replace text
              • INSERT_LINE: Insert new line at position
              • APPEND_LINE: Add line at end of file
              • DELETE_LINE: Remove specific line
              • REPLACE_LINE: Replace entire line
            - backup (optional, boolean): Create .backup file (default: true)
            - encoding (optional, string): Text encoding (default: UTF-8)
            
            Operation-specific parameters:
            REPLACE:
              - find (required): Text to find
              - replace (required): Replacement text
              - ignoreCase (optional): Case insensitive (default: false)
              - replaceAll (optional): Replace all occurrences (default: true)
            
            INSERT_LINE/DELETE_LINE/REPLACE_LINE:
              - lineNumber (required): Line number (1-based)
              - text (required for insert/replace): New line content
            
            APPEND_LINE:
              - text (required): Line to append
              
            Examples:
            {"path": "config.txt", "operation": "replace", "find": "old_value", "replace": "new_value"}
            {"path": "notes.md", "operation": "insert_line", "lineNumber": 5, "text": "## New Section"}
            {"path": "script.sh", "operation": "append_line", "text": "echo 'Done'"}
        """.trimIndent()
    }
}