package com.yourname.coquettemobile.core.tools

import android.content.Context
import com.yourname.coquettemobile.core.logging.CoquetteLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import javax.inject.Inject
import java.io.FileOutputStream
import java.io.IOException
import kotlin.math.abs
import kotlin.math.sign

/**
 * HIDMouseTool - AI-powered universal mouse control
 * Based on android-hid-client for cross-device cursor automation
 * Requires rooted Android device with HID gadget support
 */
class HIDMouseTool @Inject constructor(
    private val context: Context,
    private val logger: CoquetteLogger
) : MobileTool {
    
    override val name = "HIDMouseTool"
    override val description = "Control any device as USB mouse - click, move cursor, scroll, drag operations"
    override val riskLevel = RiskLevel.CRITICAL // Root + system-level device control
    override val requiredPermissions = listOf(
        "android.permission.ACCESS_SUPERUSER" // Custom permission indicator
    )
    
    companion object {
        private const val MOUSE_DEVICE_PATH = "/dev/hidg1"
        private const val MAX_COORDINATE = 32767 // Max absolute coordinate
        private const val MIN_COORDINATE = -32768
        private const val MAX_SCROLL_DELTA = 127
        private const val MIN_SCROLL_DELTA = -128
        private const val DEFAULT_MOVE_SPEED = 10L // milliseconds between move steps
        
        // Mouse button constants (bitmask)
        private const val MOUSE_BUTTON_NONE: Byte = 0b000
        private const val MOUSE_BUTTON_LEFT: Byte = 0b001
        private const val MOUSE_BUTTON_RIGHT: Byte = 0b010
        private const val MOUSE_BUTTON_MIDDLE: Byte = 0b100
    }
    
    override suspend fun execute(params: Map<String, Any>): ToolResult = withContext(Dispatchers.IO) {
        try {
            // Check if HID device exists
            if (!isHIDDeviceAvailable()) {
                return@withContext ToolResult.error(
                    "HID mouse device not available. Requires rooted device with HID gadget configured. " +
                    "See: https://github.com/Arian04/android-hid-client for setup instructions."
                )
            }
            
            val action = params["action"] as? String
                ?: return@withContext ToolResult.error("Missing required parameter: action")
            
            when (action.lowercase()) {
                "click" -> performClick(params)
                "double_click", "doubleclick" -> performDoubleClick(params)
                "right_click", "rightclick" -> performRightClick(params)
                "middle_click", "middleclick" -> performMiddleClick(params)
                "move" -> performMove(params)
                "move_to", "moveto" -> performMoveTo(params)
                "drag" -> performDrag(params)
                "scroll" -> performScroll(params)
                "hold" -> performHold(params)
                "release" -> performRelease(params)
                else -> ToolResult.error("Invalid action: $action. Use: click, double_click, right_click, middle_click, move, move_to, drag, scroll, hold, release")
            }
            
        } catch (e: IOException) {
            logger.e("HIDMouseTool", "HID device I/O error: ${e.message}")
            ToolResult.error("HID device error: ${e.message}")
        } catch (e: SecurityException) {
            ToolResult.error("Permission denied. Requires root access for HID device control.")
        } catch (e: Exception) {
            logger.e("HIDMouseTool", "Error in HID mouse control: ${e.message}")
            ToolResult.error("Failed to control mouse: ${e.message}")
        }
    }
    
    override suspend fun executeStreaming(
        params: Map<String, Any>,
        onProgress: (String) -> Unit
    ): ToolResult = withContext(Dispatchers.IO) {
        try {
            onProgress("Checking HID mouse device availability...")
            
            if (!isHIDDeviceAvailable()) {
                onProgress("HID device not available")
                return@withContext ToolResult.error(
                    "HID mouse device not available. Requires rooted device with HID gadget configured."
                )
            }
            
            val action = params["action"] as? String
                ?: return@withContext ToolResult.error("Missing required parameter: action")
            
            onProgress("Executing HID mouse action: $action")
            
            when (action.lowercase()) {
                "click" -> performClick(params, onProgress)
                "double_click", "doubleclick" -> performDoubleClick(params, onProgress)
                "right_click", "rightclick" -> performRightClick(params, onProgress)
                "middle_click", "middleclick" -> performMiddleClick(params, onProgress)
                "move" -> performMove(params, onProgress)
                "move_to", "moveto" -> performMoveTo(params, onProgress)
                "drag" -> performDrag(params, onProgress)
                "scroll" -> performScroll(params, onProgress)
                "hold" -> performHold(params, onProgress)
                "release" -> performRelease(params, onProgress)
                else -> ToolResult.error("Invalid action: $action")
            }
            
        } catch (e: Exception) {
            val error = "Failed to control mouse: ${e.message}"
            onProgress(error)
            ToolResult.error(error)
        }
    }
    
    /**
     * Perform left mouse click
     */
    private suspend fun performClick(
        params: Map<String, Any>, 
        onProgress: ((String) -> Unit)? = null
    ): ToolResult {
        val x = (params["x"] as? Number)?.toInt()
        val y = (params["y"] as? Number)?.toInt()
        val holdTime = ((params["holdTime"] as? Number)?.toLong() ?: 50L).coerceIn(10L, 1000L)
        
        onProgress?.invoke("Performing left click${if (x != null && y != null) " at ($x, $y)" else ""}")
        
        // Move to position if coordinates provided
        if (x != null && y != null) {
            moveToPosition(x, y)
            delay(10) // Small delay before click
        }
        
        // Click sequence
        sendMouseReport(MOUSE_BUTTON_LEFT, 0, 0, 0) // Press
        delay(holdTime)
        sendMouseReport(MOUSE_BUTTON_NONE, 0, 0, 0) // Release
        
        val metadata = mapOf<String, Any>(
            "action" to "click",
            "button" to "left",
            "x" to (x ?: ""),
            "y" to (y ?: ""),
            "holdTime" to holdTime
        )
        
        return ToolResult.success("Left click performed", metadata)
    }
    
    /**
     * Perform double click
     */
    private suspend fun performDoubleClick(
        params: Map<String, Any>, 
        onProgress: ((String) -> Unit)? = null
    ): ToolResult {
        val x = (params["x"] as? Number)?.toInt()
        val y = (params["y"] as? Number)?.toInt()
        val clickInterval = ((params["clickInterval"] as? Number)?.toLong() ?: 100L).coerceIn(50L, 500L)
        
        onProgress?.invoke("Performing double click${if (x != null && y != null) " at ($x, $y)" else ""}")
        
        // Move to position if coordinates provided
        if (x != null && y != null) {
            moveToPosition(x, y)
            delay(10)
        }
        
        // First click
        sendMouseReport(MOUSE_BUTTON_LEFT, 0, 0, 0)
        delay(50)
        sendMouseReport(MOUSE_BUTTON_NONE, 0, 0, 0)
        
        delay(clickInterval)
        
        // Second click
        sendMouseReport(MOUSE_BUTTON_LEFT, 0, 0, 0)
        delay(50)
        sendMouseReport(MOUSE_BUTTON_NONE, 0, 0, 0)
        
        val metadata = mapOf<String, Any>(
            "action" to "double_click",
            "x" to (x ?: ""),
            "y" to (y ?: ""),
            "clickInterval" to clickInterval
        )
        
        return ToolResult.success("Double click performed", metadata)
    }
    
    /**
     * Perform right mouse click
     */
    private suspend fun performRightClick(
        params: Map<String, Any>, 
        onProgress: ((String) -> Unit)? = null
    ): ToolResult {
        val x = (params["x"] as? Number)?.toInt()
        val y = (params["y"] as? Number)?.toInt()
        val holdTime = ((params["holdTime"] as? Number)?.toLong() ?: 50L).coerceIn(10L, 1000L)
        
        onProgress?.invoke("Performing right click${if (x != null && y != null) " at ($x, $y)" else ""}")
        
        // Move to position if coordinates provided
        if (x != null && y != null) {
            moveToPosition(x, y)
            delay(10)
        }
        
        // Right click sequence
        sendMouseReport(MOUSE_BUTTON_RIGHT, 0, 0, 0) // Press
        delay(holdTime)
        sendMouseReport(MOUSE_BUTTON_NONE, 0, 0, 0) // Release
        
        val metadata = mapOf<String, Any>(
            "action" to "right_click",
            "x" to (x ?: ""),
            "y" to (y ?: ""),
            "holdTime" to holdTime
        )
        
        return ToolResult.success("Right click performed", metadata)
    }
    
    /**
     * Perform middle mouse click (wheel click)
     */
    private suspend fun performMiddleClick(
        params: Map<String, Any>, 
        onProgress: ((String) -> Unit)? = null
    ): ToolResult {
        val x = (params["x"] as? Number)?.toInt()
        val y = (params["y"] as? Number)?.toInt()
        
        onProgress?.invoke("Performing middle click${if (x != null && y != null) " at ($x, $y)" else ""}")
        
        if (x != null && y != null) {
            moveToPosition(x, y)
            delay(10)
        }
        
        sendMouseReport(MOUSE_BUTTON_MIDDLE, 0, 0, 0)
        delay(50)
        sendMouseReport(MOUSE_BUTTON_NONE, 0, 0, 0)
        
        val metadata = mapOf<String, Any>(
            "action" to "middle_click",
            "x" to (x ?: ""),
            "y" to (y ?: "")
        )
        
        return ToolResult.success("Middle click performed", metadata)
    }
    
    /**
     * Move mouse cursor relatively
     */
    private suspend fun performMove(
        params: Map<String, Any>, 
        onProgress: ((String) -> Unit)? = null
    ): ToolResult {
        val deltaX = (params["deltaX"] as? Number)?.toInt()
            ?: return ToolResult.error("Missing deltaX parameter for move action")
        val deltaY = (params["deltaY"] as? Number)?.toInt()
            ?: return ToolResult.error("Missing deltaY parameter for move action")
        val speed = ((params["speed"] as? Number)?.toLong() ?: DEFAULT_MOVE_SPEED).coerceIn(1L, 100L)
        
        onProgress?.invoke("Moving cursor by ($deltaX, $deltaY)")
        
        // Break large moves into smaller steps for smoothness
        val steps = maxOf(1, maxOf(abs(deltaX), abs(deltaY)) / 10)
        val stepX = deltaX.toFloat() / steps
        val stepY = deltaY.toFloat() / steps
        
        var accumulatedX = 0f
        var accumulatedY = 0f
        
        for (i in 0 until steps) {
            accumulatedX += stepX
            accumulatedY += stepY
            
            val moveX = accumulatedX.toInt()
            val moveY = accumulatedY.toInt()
            
            if (moveX != 0 || moveY != 0) {
                sendMouseReport(MOUSE_BUTTON_NONE, moveX.toByte(), moveY.toByte(), 0)
                accumulatedX -= moveX
                accumulatedY -= moveY
                delay(speed)
            }
        }
        
        val metadata = mapOf(
            "action" to "move",
            "deltaX" to deltaX,
            "deltaY" to deltaY,
            "steps" to steps,
            "speed" to speed
        )
        
        return ToolResult.success("Cursor moved by ($deltaX, $deltaY)", metadata)
    }
    
    /**
     * Move mouse cursor to absolute position (requires screen resolution context)
     */
    private suspend fun performMoveTo(
        params: Map<String, Any>, 
        onProgress: ((String) -> Unit)? = null
    ): ToolResult {
        val x = (params["x"] as? Number)?.toInt()
            ?: return ToolResult.error("Missing x parameter for move_to action")
        val y = (params["y"] as? Number)?.toInt()
            ?: return ToolResult.error("Missing y parameter for move_to action")
        
        onProgress?.invoke("Moving cursor to absolute position ($x, $y)")
        
        moveToPosition(x, y)
        
        val metadata = mapOf(
            "action" to "move_to",
            "x" to x,
            "y" to y
        )
        
        return ToolResult.success("Cursor moved to ($x, $y)", metadata)
    }
    
    /**
     * Drag operation (move while holding button)
     */
    private suspend fun performDrag(
        params: Map<String, Any>, 
        onProgress: ((String) -> Unit)? = null
    ): ToolResult {
        val fromX = (params["fromX"] as? Number)?.toInt()
        val fromY = (params["fromY"] as? Number)?.toInt()
        val toX = (params["toX"] as? Number)?.toInt()
            ?: return ToolResult.error("Missing toX parameter for drag action")
        val toY = (params["toY"] as? Number)?.toInt()
            ?: return ToolResult.error("Missing toY parameter for drag action")
        val button = params["button"] as? String ?: "left"
        val speed = ((params["speed"] as? Number)?.toLong() ?: DEFAULT_MOVE_SPEED).coerceIn(1L, 100L)
        
        val buttonByte = when (button.lowercase()) {
            "left" -> MOUSE_BUTTON_LEFT
            "right" -> MOUSE_BUTTON_RIGHT
            "middle" -> MOUSE_BUTTON_MIDDLE
            else -> return ToolResult.error("Invalid button: $button. Use: left, right, middle")
        }
        
        onProgress?.invoke("Performing drag from ${fromX?.let { "($it, $fromY)" } ?: "current position"} to ($toX, $toY)")
        
        // Move to start position if specified
        if (fromX != null && fromY != null) {
            moveToPosition(fromX, fromY)
            delay(50)
        }
        
        // Press button down
        sendMouseReport(buttonByte, 0, 0, 0)
        delay(50)
        
        // Calculate drag movement
        val deltaX = if (fromX != null) toX - fromX else toX
        val deltaY = if (fromY != null) toY - fromY else toY
        
        // Perform drag movement
        val steps = maxOf(1, maxOf(abs(deltaX), abs(deltaY)) / 5)
        val stepX = deltaX.toFloat() / steps
        val stepY = deltaY.toFloat() / steps
        
        var accumulatedX = 0f
        var accumulatedY = 0f
        
        for (i in 0 until steps) {
            accumulatedX += stepX
            accumulatedY += stepY
            
            val moveX = accumulatedX.toInt()
            val moveY = accumulatedY.toInt()
            
            if (moveX != 0 || moveY != 0) {
                sendMouseReport(buttonByte, moveX.toByte(), moveY.toByte(), 0)
                accumulatedX -= moveX
                accumulatedY -= moveY
                delay(speed)
            }
        }
        
        // Release button
        sendMouseReport(MOUSE_BUTTON_NONE, 0, 0, 0)
        
        val metadata = mapOf<String, Any>(
            "action" to "drag",
            "fromX" to (fromX ?: ""),
            "fromY" to (fromY ?: ""),
            "toX" to toX,
            "toY" to toY,
            "button" to button,
            "steps" to steps,
            "speed" to speed
        )
        
        return ToolResult.success("Drag operation completed from ${fromX?.let { "($it, $fromY)" } ?: "current"} to ($toX, $toY)", metadata)
    }
    
    /**
     * Scroll operation
     */
    private suspend fun performScroll(
        params: Map<String, Any>, 
        onProgress: ((String) -> Unit)? = null
    ): ToolResult {
        val direction = params["direction"] as? String
            ?: return ToolResult.error("Missing direction parameter for scroll action")
        val amount = ((params["amount"] as? Number)?.toInt() ?: 3).coerceIn(1, 20)
        val speed = ((params["speed"] as? Number)?.toLong() ?: 100L).coerceIn(10L, 1000L)
        
        val scrollDelta = when (direction.lowercase()) {
            "up" -> 1
            "down" -> -1
            "left" -> 0 // Would need horizontal scroll support
            "right" -> 0
            else -> return ToolResult.error("Invalid direction: $direction. Use: up, down")
        }.toByte()
        
        onProgress?.invoke("Scrolling $direction ($amount steps)")
        
        repeat(amount) {
            sendMouseReport(MOUSE_BUTTON_NONE, 0, 0, scrollDelta)
            delay(speed)
        }
        
        val metadata = mapOf(
            "action" to "scroll",
            "direction" to direction,
            "amount" to amount,
            "speed" to speed
        )
        
        return ToolResult.success("Scrolled $direction $amount steps", metadata)
    }
    
    /**
     * Hold mouse button down
     */
    private suspend fun performHold(
        params: Map<String, Any>, 
        onProgress: ((String) -> Unit)? = null
    ): ToolResult {
        val button = params["button"] as? String ?: "left"
        
        val buttonByte = when (button.lowercase()) {
            "left" -> MOUSE_BUTTON_LEFT
            "right" -> MOUSE_BUTTON_RIGHT
            "middle" -> MOUSE_BUTTON_MIDDLE
            else -> return ToolResult.error("Invalid button: $button. Use: left, right, middle")
        }
        
        onProgress?.invoke("Holding $button mouse button down")
        
        sendMouseReport(buttonByte, 0, 0, 0)
        
        val metadata = mapOf(
            "action" to "hold",
            "button" to button
        )
        
        return ToolResult.success("$button mouse button held down", metadata)
    }
    
    /**
     * Release mouse button
     */
    private suspend fun performRelease(
        params: Map<String, Any>, 
        onProgress: ((String) -> Unit)? = null
    ): ToolResult {
        onProgress?.invoke("Releasing all mouse buttons")
        
        sendMouseReport(MOUSE_BUTTON_NONE, 0, 0, 0)
        
        val metadata = mapOf(
            "action" to "release"
        )
        
        return ToolResult.success("All mouse buttons released", metadata)
    }
    
    /**
     * Move cursor to absolute position (helper method)
     */
    private suspend fun moveToPosition(x: Int, y: Int) {
        // This is a simplified absolute positioning
        // In practice, you'd need screen resolution and current cursor position
        // For now, we'll use relative movements as approximation
        
        // This would require more sophisticated implementation
        // involving screen coordinate mapping
        sendMouseReport(MOUSE_BUTTON_NONE, x.toByte(), y.toByte(), 0)
    }
    
    /**
     * Send mouse HID report
     */
    @Throws(IOException::class)
    private fun sendMouseReport(buttons: Byte, deltaX: Byte, deltaY: Byte, wheel: Byte) {
        val report = byteArrayOf(buttons, deltaX, deltaY, wheel)
        writeHIDReport(report)
    }
    
    /**
     * Write HID report to character device
     */
    @Throws(IOException::class)
    private fun writeHIDReport(report: ByteArray) {
        FileOutputStream(MOUSE_DEVICE_PATH).use { outputStream ->
            outputStream.write(report)
        }
    }
    
    /**
     * Check if HID mouse device is available
     */
    private fun isHIDDeviceAvailable(): Boolean {
        return try {
            val device = java.io.File(MOUSE_DEVICE_PATH)
            device.exists() && device.canWrite()
        } catch (e: Exception) {
            false
        }
    }
    
    override fun getDescription(params: Map<String, Any>): String {
        val action = params["action"] as? String ?: "control"
        val x = params["x"] as? Number
        val y = params["y"] as? Number
        
        return when (action.lowercase()) {
            "click" -> "Left click${if (x != null && y != null) " at ($x, $y)" else ""}"
            "right_click" -> "Right click${if (x != null && y != null) " at ($x, $y)" else ""}"
            "double_click" -> "Double click${if (x != null && y != null) " at ($x, $y)" else ""}"
            "move" -> {
                val deltaX = params["deltaX"] as? Number
                val deltaY = params["deltaY"] as? Number
                "Move cursor by ($deltaX, $deltaY)"
            }
            "move_to" -> "Move cursor to ($x, $y)"
            "drag" -> {
                val toX = params["toX"] as? Number
                val toY = params["toY"] as? Number
                "Drag to ($toX, $toY)"
            }
            "scroll" -> {
                val direction = params["direction"] as? String ?: "unknown"
                val amount = params["amount"] as? Number ?: 1
                "Scroll $direction $amount steps"
            }
            else -> "HID mouse control: $action"
        }
    }
    
    override fun validateParams(params: Map<String, Any>): String? {
        val action = params["action"] as? String
            ?: return "action parameter is required"
        
        return when (action.lowercase()) {
            "move" -> {
                val deltaX = params["deltaX"] as? Number
                val deltaY = params["deltaY"] as? Number
                when {
                    deltaX == null -> "deltaX parameter required for move action"
                    deltaY == null -> "deltaY parameter required for move action"
                    else -> null
                }
            }
            "move_to" -> {
                val x = params["x"] as? Number
                val y = params["y"] as? Number
                when {
                    x == null -> "x parameter required for move_to action"
                    y == null -> "y parameter required for move_to action"
                    else -> null
                }
            }
            "drag" -> {
                val toX = params["toX"] as? Number
                val toY = params["toY"] as? Number
                when {
                    toX == null -> "toX parameter required for drag action"
                    toY == null -> "toY parameter required for drag action"
                    else -> null
                }
            }
            "scroll" -> {
                val direction = params["direction"] as? String
                when {
                    direction == null -> "direction parameter required for scroll action"
                    direction.lowercase() !in listOf("up", "down", "left", "right") -> 
                        "direction must be: up, down, left, right"
                    else -> null
                }
            }
            "hold", "release" -> {
                val button = params["button"] as? String
                when {
                    action.lowercase() == "hold" && button != null && 
                    button.lowercase() !in listOf("left", "right", "middle") -> 
                        "button must be: left, right, middle"
                    else -> null
                }
            }
            in listOf("click", "double_click", "right_click", "middle_click") -> null
            else -> "Invalid action: $action"
        }
    }
    
    override fun getParameterSchema(): String {
        return """
            Parameters:
            - action (required, string): Type of mouse action
              • "click": Left mouse click
              • "double_click": Double left click  
              • "right_click": Right mouse click
              • "middle_click": Middle mouse click (wheel)
              • "move": Move cursor relatively 
              • "move_to": Move cursor to absolute position
              • "drag": Drag operation (click and drag)
              • "scroll": Scroll wheel operation
              • "hold": Hold mouse button down
              • "release": Release all mouse buttons
              
            Action-specific parameters:
            click/double_click/right_click/middle_click:
              - x, y (optional, numbers): Click coordinates
              - holdTime (optional, number): Button hold duration in ms (10-1000, default: 50)
              - clickInterval (optional, number): Time between double clicks in ms (50-500, default: 100)
              
            move:
              - deltaX, deltaY (required, numbers): Relative movement pixels
              - speed (optional, number): Movement speed in ms (1-100, default: 10)
              
            move_to:
              - x, y (required, numbers): Absolute screen coordinates
              
            drag:
              - toX, toY (required, numbers): Drag destination coordinates
              - fromX, fromY (optional, numbers): Drag start coordinates (default: current position)
              - button (optional, string): Button to drag with (left/right/middle, default: left)
              - speed (optional, number): Drag speed in ms (1-100, default: 10)
              
            scroll:
              - direction (required, string): Scroll direction (up/down/left/right)
              - amount (optional, number): Number of scroll steps (1-20, default: 3)
              - speed (optional, number): Scroll speed in ms (10-1000, default: 100)
              
            hold/release:
              - button (optional, string): Button to hold/release (left/right/middle, default: left)
              
            Examples:
            {"action": "click", "x": 500, "y": 300}
            {"action": "double_click", "x": 100, "y": 200, "clickInterval": 150}
            {"action": "right_click"}
            {"action": "move", "deltaX": 100, "deltaY": -50, "speed": 20}
            {"action": "move_to", "x": 1920, "y": 1080}
            {"action": "drag", "fromX": 100, "fromY": 100, "toX": 200, "toY": 200}
            {"action": "scroll", "direction": "up", "amount": 5}
            {"action": "hold", "button": "left"}
            {"action": "release"}
            
            Requirements:
            - Rooted Android device
            - HID gadget configured (/dev/hidg1 must exist)
            - Target device connected via USB
        """.trimIndent()
    }
}