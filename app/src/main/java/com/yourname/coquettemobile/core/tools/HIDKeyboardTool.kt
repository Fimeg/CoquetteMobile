package com.yourname.coquettemobile.core.tools

import android.content.Context
import com.yourname.coquettemobile.core.logging.CoquetteLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import javax.inject.Inject
import java.io.FileOutputStream
import java.io.IOException

/**
 * HIDKeyboardTool - AI-powered universal keyboard control
 * Based on android-hid-client for cross-device input automation
 * Requires rooted Android device with HID gadget support
 */
class HIDKeyboardTool @Inject constructor(
    private val context: Context,
    private val logger: CoquetteLogger
) : MobileTool {
    
    override val name = "HIDKeyboardTool"
    override val description = "Control any device as USB keyboard - type text, send key combinations, automate input"
    override val riskLevel = RiskLevel.CRITICAL // Root + system-level device control
    override val requiredPermissions = listOf(
        "android.permission.ACCESS_SUPERUSER" // Custom permission indicator
    )
    
    companion object {
        private const val KEYBOARD_DEVICE_PATH = "/dev/hidg0"
        private const val MAX_TEXT_LENGTH = 10000 // Prevent memory issues
        private const val DEFAULT_TYPING_SPEED = 50L // milliseconds between keystrokes
        
        // HID Report IDs
        private const val STANDARD_KEY: Byte = 0x01
        private const val MEDIA_KEY: Byte = 0x02
        
        // HID Modifier codes (bitmask)
        private const val NO_MODIFIER: Byte = 0x00
        private const val LEFT_CTRL: Byte = 0x01
        private const val LEFT_SHIFT: Byte = 0x02
        private const val LEFT_ALT: Byte = 0x04
        private const val LEFT_META: Byte = 0x08 // Windows key
        private const val RIGHT_CTRL: Byte = 0x10
        private const val RIGHT_SHIFT: Byte = 0x20
        private const val RIGHT_ALT: Byte = 0x40
        private const val RIGHT_META: Byte = 0x80.toByte()
    }
    
    override suspend fun execute(params: Map<String, Any>): ToolResult = withContext(Dispatchers.IO) {
        try {
            // Check if HID device exists
            if (!isHIDDeviceAvailable()) {
                return@withContext ToolResult.error(
                    "HID keyboard device not available. Requires rooted device with HID gadget configured. " +
                    "See: https://github.com/Arian04/android-hid-client for setup instructions."
                )
            }
            
            val action = params["action"] as? String ?: "type"
            val text = params["text"] as? String
            val keys = params["keys"] as? List<*>
            val typingSpeed = ((params["typingSpeed"] as? Number)?.toLong() ?: DEFAULT_TYPING_SPEED)
                .coerceIn(10L, 2000L)
            
            when (action.lowercase()) {
                "type" -> {
                    val textToType = text ?: return@withContext ToolResult.error("Missing 'text' parameter for type action")
                    if (textToType.length > MAX_TEXT_LENGTH) {
                        return@withContext ToolResult.error("Text too long (max $MAX_TEXT_LENGTH characters)")
                    }
                    performTyping(textToType, typingSpeed)
                }
                "key", "shortcut" -> {
                    val keyList = keys ?: return@withContext ToolResult.error("Missing 'keys' parameter for key action")
                    performKeySequence(keyList, typingSpeed)
                }
                "special" -> {
                    val specialKey = text ?: return@withContext ToolResult.error("Missing 'text' parameter for special key")
                    performSpecialKey(specialKey)
                }
                else -> return@withContext ToolResult.error("Invalid action: $action. Use 'type', 'key', 'shortcut', or 'special'")
            }
            
        } catch (e: IOException) {
            logger.e("HIDKeyboardTool", "HID device I/O error: ${e.message}")
            ToolResult.error("HID device error: ${e.message}")
        } catch (e: SecurityException) {
            ToolResult.error("Permission denied. Requires root access for HID device control.")
        } catch (e: Exception) {
            logger.e("HIDKeyboardTool", "Error in HID keyboard control: ${e.message}")
            ToolResult.error("Failed to control keyboard: ${e.message}")
        }
    }
    
    override suspend fun executeStreaming(
        params: Map<String, Any>,
        onProgress: (String) -> Unit
    ): ToolResult = withContext(Dispatchers.IO) {
        try {
            onProgress("Checking HID device availability...")
            
            if (!isHIDDeviceAvailable()) {
                onProgress("HID device not available")
                return@withContext ToolResult.error(
                    "HID keyboard device not available. Requires rooted device with HID gadget configured."
                )
            }
            
            val action = params["action"] as? String ?: "type"
            val text = params["text"] as? String
            val keys = params["keys"] as? List<*>
            val typingSpeed = ((params["typingSpeed"] as? Number)?.toLong() ?: DEFAULT_TYPING_SPEED)
                .coerceIn(10L, 2000L)
            
            onProgress("Executing HID keyboard action: $action")
            
            when (action.lowercase()) {
                "type" -> {
                    val textToType = text ?: return@withContext ToolResult.error("Missing 'text' parameter for type action")
                    if (textToType.length > MAX_TEXT_LENGTH) {
                        return@withContext ToolResult.error("Text too long (max $MAX_TEXT_LENGTH characters)")
                    }
                    performTypingWithProgress(textToType, typingSpeed, onProgress)
                }
                "key", "shortcut" -> {
                    val keyList = keys ?: return@withContext ToolResult.error("Missing 'keys' parameter for key action")
                    performKeySequenceWithProgress(keyList, typingSpeed, onProgress)
                }
                "special" -> {
                    val specialKey = text ?: return@withContext ToolResult.error("Missing 'text' parameter for special key")
                    onProgress("Sending special key: $specialKey")
                    performSpecialKey(specialKey)
                }
                else -> return@withContext ToolResult.error("Invalid action: $action. Use 'type', 'key', 'shortcut', or 'special'")
            }
            
        } catch (e: Exception) {
            val error = "Failed to control keyboard: ${e.message}"
            onProgress(error)
            ToolResult.error(error)
        }
    }
    
    /**
     * Type text character by character with speed control
     */
    private suspend fun performTyping(text: String, typingSpeed: Long): ToolResult {
        var successCount = 0
        var errorCount = 0
        
        for (char in text) {
            try {
                val scanCodes = convertCharToScanCodes(char)
                if (scanCodes != null) {
                    sendStandardKey(scanCodes[0], scanCodes[1])
                    successCount++
                } else {
                    logger.w("HIDKeyboardTool", "Could not convert character: '$char'")
                    errorCount++
                }
                
                if (typingSpeed > 0) {
                    delay(typingSpeed)
                }
            } catch (e: Exception) {
                errorCount++
                logger.e("HIDKeyboardTool", "Error typing character '$char': ${e.message}")
            }
        }
        
        val metadata = mapOf(
            "action" to "type",
            "textLength" to text.length,
            "charactersTyped" to successCount,
            "errors" to errorCount,
            "typingSpeed" to typingSpeed
        )
        
        val result = if (errorCount == 0) {
            "Successfully typed $successCount characters"
        } else {
            "Typed $successCount characters with $errorCount errors"
        }
        
        return ToolResult.success(result, metadata)
    }
    
    /**
     * Type text with progress updates
     */
    private suspend fun performTypingWithProgress(
        text: String, 
        typingSpeed: Long, 
        onProgress: (String) -> Unit
    ): ToolResult {
        var successCount = 0
        var errorCount = 0
        var lastProgressUpdate = 0L
        
        for ((index, char) in text.withIndex()) {
            try {
                val scanCodes = convertCharToScanCodes(char)
                if (scanCodes != null) {
                    sendStandardKey(scanCodes[0], scanCodes[1])
                    successCount++
                } else {
                    errorCount++
                }
                
                // Progress updates every 50 characters or 2 seconds
                val now = System.currentTimeMillis()
                if (index % 50 == 0 || now - lastProgressUpdate > 2000) {
                    val progress = ((index + 1).toFloat() / text.length * 100).toInt()
                    onProgress("Typing progress: $progress% ($successCount/$text.length characters)")
                    lastProgressUpdate = now
                }
                
                if (typingSpeed > 0) {
                    delay(typingSpeed)
                }
            } catch (e: Exception) {
                errorCount++
                logger.e("HIDKeyboardTool", "Error typing character '$char': ${e.message}")
            }
        }
        
        val metadata = mapOf(
            "action" to "type",
            "textLength" to text.length,
            "charactersTyped" to successCount,
            "errors" to errorCount,
            "typingSpeed" to typingSpeed
        )
        
        val result = if (errorCount == 0) {
            "Successfully typed $successCount characters"
        } else {
            "Typed $successCount characters with $errorCount errors"
        }
        
        return ToolResult.success(result, metadata)
    }
    
    /**
     * Send key combinations (Ctrl+C, Alt+Tab, etc.)
     */
    private suspend fun performKeySequence(keys: List<*>, typingSpeed: Long): ToolResult {
        val keyStrings = keys.map { it.toString().lowercase() }
        
        // Parse modifiers and main key
        val modifiers = keyStrings.filter { it in listOf("ctrl", "shift", "alt", "meta", "win", "cmd") }
        val mainKeys = keyStrings.filter { it !in modifiers }
        
        // Convert modifiers to bitmask
        var modifierByte: Byte = NO_MODIFIER
        for (modifier in modifiers) {
            modifierByte = (modifierByte.toInt() or when (modifier) {
                "ctrl" -> LEFT_CTRL.toInt()
                "shift" -> LEFT_SHIFT.toInt()
                "alt" -> LEFT_ALT.toInt()
                "meta", "win", "cmd" -> LEFT_META.toInt()
                else -> 0
            }).toByte()
        }
        
        // Send each main key with modifiers
        for (mainKey in mainKeys) {
            val keyCode = convertKeyNameToHIDCode(mainKey)
            if (keyCode != null) {
                sendStandardKey(modifierByte, keyCode)
                if (typingSpeed > 0) {
                    delay(typingSpeed)
                }
            } else {
                return ToolResult.error("Unknown key: $mainKey")
            }
        }
        
        val metadata = mapOf(
            "action" to "key_sequence",
            "modifiers" to modifiers,
            "keys" to mainKeys,
            "typingSpeed" to typingSpeed
        )
        
        return ToolResult.success("Sent key sequence: ${keyStrings.joinToString("+")}", metadata)
    }
    
    /**
     * Send key sequence with progress updates
     */
    private suspend fun performKeySequenceWithProgress(
        keys: List<*>, 
        typingSpeed: Long, 
        onProgress: (String) -> Unit
    ): ToolResult {
        val keyStrings = keys.map { it.toString().lowercase() }
        onProgress("Processing key sequence: ${keyStrings.joinToString("+")}")
        
        return performKeySequence(keys, typingSpeed)
    }
    
    /**
     * Send special/media keys
     */
    private suspend fun performSpecialKey(keyName: String): ToolResult {
        val mediaKeyCode = when (keyName.lowercase()) {
            "volume_up", "vol_up" -> 0xe9.toByte()
            "volume_down", "vol_down" -> 0xea.toByte()
            "play_pause", "play", "pause" -> 0xcd.toByte()
            "next_track", "next" -> 0xb5.toByte()
            "prev_track", "previous" -> 0xb6.toByte()
            else -> return ToolResult.error("Unknown special key: $keyName")
        }
        
        sendMediaKey(mediaKeyCode)
        
        val metadata = mapOf(
            "action" to "special_key",
            "key" to keyName
        )
        
        return ToolResult.success("Sent special key: $keyName", metadata)
    }
    
    /**
     * Send standard keyboard key (with modifiers)
     */
    private fun sendStandardKey(modifier: Byte, keyCode: Byte) {
        val report = byteArrayOf(STANDARD_KEY, modifier, 0, keyCode, 0)
        writeHIDReport(report)
        
        // Send release report
        val releaseReport = byteArrayOf(STANDARD_KEY, 0, 0, 0, 0)
        writeHIDReport(releaseReport)
    }
    
    /**
     * Send media key
     */
    private fun sendMediaKey(keyCode: Byte) {
        val report = byteArrayOf(MEDIA_KEY, keyCode, 0)
        writeHIDReport(report)
        
        // Send release report
        val releaseReport = byteArrayOf(MEDIA_KEY, 0, 0)
        writeHIDReport(releaseReport)
    }
    
    /**
     * Write HID report to character device
     */
    @Throws(IOException::class)
    private fun writeHIDReport(report: ByteArray) {
        FileOutputStream(KEYBOARD_DEVICE_PATH).use { outputStream ->
            outputStream.write(report)
        }
    }
    
    /**
     * Check if HID keyboard device is available
     */
    private fun isHIDDeviceAvailable(): Boolean {
        return try {
            val device = java.io.File(KEYBOARD_DEVICE_PATH)
            device.exists() && device.canWrite()
        } catch (e: Exception) {
            false
        }
    }
    
    /**
     * Convert character to HID scan codes [modifier, keyCode]
     */
    private fun convertCharToScanCodes(char: Char): ByteArray? {
        val key = char.toString().lowercase()
        var modifier: Byte = NO_MODIFIER
        var actualKey = key
        
        // Handle uppercase letters and shift characters
        if (char.isUpperCase()) {
            modifier = LEFT_SHIFT
            actualKey = char.lowercase().toString()
        } else if (char in "!@#$%^&*()_+{}|:\"<>?~") {
            modifier = LEFT_SHIFT
            actualKey = when (char) {
                '!' -> "1"
                '@' -> "2"
                '#' -> "3"
                '$' -> "4"
                '%' -> "5"
                '^' -> "6"
                '&' -> "7"
                '*' -> "8"
                '(' -> "9"
                ')' -> "0"
                '_' -> "-"
                '+' -> "="
                '{' -> "["
                '}' -> "]"
                '|' -> "\\"
                ':' -> ";"
                '"' -> "'"
                '<' -> ","
                '>' -> "."
                '?' -> "/"
                '~' -> "`"
                else -> key
            }
        }
        
        val keyCode = convertKeyNameToHIDCode(actualKey)
        return if (keyCode != null) {
            byteArrayOf(modifier, keyCode)
        } else null
    }
    
    /**
     * Convert key name to HID key code
     */
    private fun convertKeyNameToHIDCode(keyName: String): Byte? {
        return when (keyName.lowercase()) {
            // Letters
            "a" -> 0x04
            "b" -> 0x05
            "c" -> 0x06
            "d" -> 0x07
            "e" -> 0x08
            "f" -> 0x09
            "g" -> 0x0a
            "h" -> 0x0b
            "i" -> 0x0c
            "j" -> 0x0d
            "k" -> 0x0e
            "l" -> 0x0f
            "m" -> 0x10
            "n" -> 0x11
            "o" -> 0x12
            "p" -> 0x13
            "q" -> 0x14
            "r" -> 0x15
            "s" -> 0x16
            "t" -> 0x17
            "u" -> 0x18
            "v" -> 0x19
            "w" -> 0x1a
            "x" -> 0x1b
            "y" -> 0x1c
            "z" -> 0x1d
            
            // Numbers
            "1" -> 0x1e
            "2" -> 0x1f
            "3" -> 0x20
            "4" -> 0x21
            "5" -> 0x22
            "6" -> 0x23
            "7" -> 0x24
            "8" -> 0x25
            "9" -> 0x26
            "0" -> 0x27
            
            // Special characters
            "\n", "enter" -> 0x28
            "escape", "esc" -> 0x29
            "backspace" -> 0x2a
            "tab" -> 0x2b
            " ", "space" -> 0x2c
            "-" -> 0x2d
            "=" -> 0x2e
            "[" -> 0x2f
            "]" -> 0x30
            "\\" -> 0x31
            ";" -> 0x33
            "'" -> 0x34
            "`" -> 0x35
            "," -> 0x36
            "." -> 0x37
            "/" -> 0x38
            
            // Function keys
            "f1" -> 0x3a
            "f2" -> 0x3b
            "f3" -> 0x3c
            "f4" -> 0x3d
            "f5" -> 0x3e
            "f6" -> 0x3f
            "f7" -> 0x40
            "f8" -> 0x41
            "f9" -> 0x42
            "f10" -> 0x43
            "f11" -> 0x44
            "f12" -> 0x45
            
            // Arrow keys
            "right" -> 0x4f
            "left" -> 0x50
            "down" -> 0x51
            "up" -> 0x52
            
            // Other keys
            "insert" -> 0x49
            "home" -> 0x4a
            "page_up", "pageup" -> 0x4b
            "delete", "del" -> 0x4c
            "end" -> 0x4d
            "page_down", "pagedown" -> 0x4e
            
            else -> null
        }?.toByte()
    }
    
    override fun getDescription(params: Map<String, Any>): String {
        val action = params["action"] as? String ?: "control"
        val text = params["text"] as? String
        val keys = params["keys"] as? List<*>
        
        return when (action.lowercase()) {
            "type" -> "Typing text via HID: ${text?.take(50)}${if (text != null && text.length > 50) "..." else ""}"
            "key", "shortcut" -> "Sending key sequence: ${keys?.joinToString("+") ?: "unknown"}"
            "special" -> "Sending special key: ${text ?: "unknown"}"
            else -> "HID keyboard control: $action"
        }
    }
    
    override fun validateParams(params: Map<String, Any>): String? {
        val action = params["action"] as? String ?: "type"
        val text = params["text"] as? String
        val keys = params["keys"] as? List<*>
        val typingSpeed = params["typingSpeed"] as? Number
        
        return when {
            action.lowercase() !in listOf("type", "key", "shortcut", "special") -> 
                "Invalid action. Must be: type, key, shortcut, special"
            action.lowercase() == "type" && text.isNullOrBlank() -> 
                "text parameter required for type action"
            action.lowercase() in listOf("key", "shortcut") && (keys == null || keys.isEmpty()) -> 
                "keys parameter required for key/shortcut action"
            action.lowercase() == "special" && text.isNullOrBlank() -> 
                "text parameter required for special key action"
            text != null && text.length > MAX_TEXT_LENGTH -> 
                "Text too long (max $MAX_TEXT_LENGTH characters)"
            typingSpeed != null && (typingSpeed.toLong() < 10 || typingSpeed.toLong() > 2000) -> 
                "typingSpeed must be between 10-2000 milliseconds"
            else -> null
        }
    }
    
    override fun getParameterSchema(): String {
        return """
            Parameters:
            - action (required, string): Type of keyboard action
              • "type": Type text character by character
              • "key"/"shortcut": Send key combinations (Ctrl+C, Alt+Tab)
              • "special": Send media/special keys (volume, play/pause)
            - text (required for type/special): Text to type or special key name
            - keys (required for key/shortcut): Array of keys for combinations
              • Example: ["ctrl", "c"] for Ctrl+C
              • Example: ["alt", "tab"] for Alt+Tab
            - typingSpeed (optional, number): Milliseconds between keystrokes (10-2000, default: 50)
              
            Examples:
            {"action": "type", "text": "Hello World!", "typingSpeed": 100}
            {"action": "shortcut", "keys": ["ctrl", "c"]}
            {"action": "key", "keys": ["alt", "f4"]}
            {"action": "special", "text": "volume_up"}
            
            Special Keys: volume_up, volume_down, play_pause, next_track, prev_track
            
            Requirements:
            - Rooted Android device
            - HID gadget configured (/dev/hidg0 must exist)
            - Target device connected via USB
        """.trimIndent()
    }
}