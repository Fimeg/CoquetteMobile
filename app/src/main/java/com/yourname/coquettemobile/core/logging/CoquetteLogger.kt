package com.yourname.coquettemobile.core.logging

import android.content.Context
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CoquetteLogger @Inject constructor(
    private val context: Context
) {
    private val logScope = CoroutineScope(Dispatchers.IO)
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US)
    private val fileNameFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)
    
    private val logsDir by lazy {
        File(context.filesDir, "logs").apply {
            if (!exists()) mkdirs()
        }
    }
    
    enum class Level {
        DEBUG, INFO, WARNING, ERROR
    }
    
    fun d(tag: String, message: String) = log(Level.DEBUG, tag, message)
    fun i(tag: String, message: String) = log(Level.INFO, tag, message)
    fun w(tag: String, message: String) = log(Level.WARNING, tag, message)
    fun e(tag: String, message: String) = log(Level.ERROR, tag, message)
    
    private fun log(level: Level, tag: String, message: String) {
        // Always log to Android logcat
        when (level) {
            Level.DEBUG -> Log.d(tag, message)
            Level.INFO -> Log.i(tag, message)
            Level.WARNING -> Log.w(tag, message)
            Level.ERROR -> Log.e(tag, message)
        }
        
        // Also write to file
        logScope.launch {
            writeToFile(level, tag, message)
        }
    }
    
    private fun writeToFile(level: Level, tag: String, message: String) {
        try {
            val timestamp = dateFormat.format(Date())
            val fileName = "coquette-${fileNameFormat.format(Date())}.log"
            val logFile = File(logsDir, fileName)
            
            val logEntry = "$timestamp [${level.name}] $tag: $message\n"
            logFile.appendText(logEntry)
            
            // Keep only last 7 days of logs
            cleanOldLogs()
        } catch (e: Exception) {
            Log.e("CoquetteLogger", "Failed to write log: ${e.message}")
        }
    }
    
    private fun cleanOldLogs() {
        try {
            val cutoffTime = System.currentTimeMillis() - (7 * 24 * 60 * 60 * 1000) // 7 days
            logsDir.listFiles()?.forEach { file ->
                if (file.lastModified() < cutoffTime) {
                    file.delete()
                }
            }
        } catch (e: Exception) {
            Log.e("CoquetteLogger", "Failed to clean old logs: ${e.message}")
        }
    }
    
    fun getLogFiles(): List<File> {
        return logsDir.listFiles()?.sortedByDescending { it.lastModified() }?.toList() 
            ?: emptyList()
    }
    
    fun clearAllLogs() {
        try {
            logsDir.listFiles()?.forEach { it.delete() }
            Log.i("CoquetteLogger", "All logs cleared")
        } catch (e: Exception) {
            Log.e("CoquetteLogger", "Failed to clear logs: ${e.message}")
        }
    }
    
    fun getLogContent(file: File): String {
        return try {
            file.readText()
        } catch (e: Exception) {
            "Error reading log file: ${e.message}"
        }
    }
}