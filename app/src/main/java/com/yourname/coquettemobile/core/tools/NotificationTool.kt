package com.yourname.coquettemobile.core.tools

import android.content.Context
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * NotificationTool - Read and manage device notifications
 * Essential for Google Assistant replacement functionality
 */
@Singleton  
class NotificationTool @Inject constructor(
    @ApplicationContext private val context: Context
) : MobileTool {
    
    override val name = "notification"
    override val description = "Read and manage device notifications"
    override val requiredPermissions = listOf("android.permission.BIND_NOTIFICATION_LISTENER_SERVICE")
    override val riskLevel = RiskLevel.MEDIUM
    
    override fun getDescription(params: Map<String, Any>): String {
        val action = params["action"] as? String ?: "read"
        return when (action) {
            "read", "list" -> "Read active notifications"
            "count" -> "Count active notifications"
            "clear" -> "Clear specific notification"
            "summary" -> "Get notification summary by app"
            else -> "Manage notifications"
        }
    }
    
    override fun validateParams(params: Map<String, Any>): String? {
        val action = params["action"] as? String
        if (action != null && action !in listOf("read", "list", "count", "clear", "summary")) {
            return "Invalid action: $action. Must be one of: read, list, count, clear, summary"
        }
        
        if (action == "clear" && params["id"] == null) {
            return "ID required for clear action"
        }
        
        return null
    }
    
    override fun getParameterSchema(): String {
        return "action: \"read\"|\"list\"|\"count\"|\"clear\"|\"summary\", id?: string, limit?: number, package?: string"
    }
    
    override suspend fun execute(parameters: Map<String, Any>): ToolResult = withContext(Dispatchers.IO) {
        executeStreaming(parameters) { /* ignore progress for sync execution */ }
    }

    override suspend fun executeStreaming(
        parameters: Map<String, Any>,
        onProgress: (String) -> Unit
    ): ToolResult {
        val action = parameters["action"] as? String ?: "read"
        
        return when (action) {
            "read", "list" -> readNotifications(parameters, onProgress)
            "count" -> countNotifications(onProgress)
            "clear" -> clearNotification(parameters["id"] as? String, onProgress)
            "summary" -> getNotificationSummary(onProgress)
            else -> ToolResult.error("Unknown action: $action. Available: read, count, clear, summary")
        }
    }
    
    private suspend fun readNotifications(parameters: Map<String, Any>, onProgress: (String) -> Unit): ToolResult {
        onProgress("🔍 Checking notification access...")
        if (!hasNotificationAccess()) {
            onProgress("❌ Notification access not granted")
            return ToolResult.error("""
                Notification access not granted. Please:
                1. Go to Settings → Apps → CoquetteMobile → Permissions
                2. Enable 'Notification Access'
                3. Or go to Settings → Accessibility → Notification Access → Enable CoquetteMobile
            """.trimIndent())
        }
        
        val limit = (parameters["limit"] as? Number)?.toInt() ?: 10
        val packageName = parameters["package"] as? String
        val priority = parameters["priority"] as? String
        
        onProgress("📱 Scanning for notifications...")
        
        return try {
            val notifications = getActiveNotifications(limit, packageName, priority)
            
            if (notifications.isEmpty()) {
                onProgress("✅ No active notifications found")
                ToolResult.success("No active notifications found", mapOf("count" to 0, "notifications" to emptyList<Map<String, Any>>()))
            } else {
                onProgress("📊 Processing ${notifications.size} notifications...")
                
                val notificationData = notifications.map { notification ->
                    mapOf(
                        "id" to notification.id,
                        "package" to notification.packageName,
                        "app" to getAppName(notification.packageName),
                        "title" to (notification.notification.extras.getString("android.title") ?: "No title"),
                        "text" to (notification.notification.extras.getString("android.text") ?: "No content"),
                        "time" to notification.postTime,
                        "ongoing" to notification.isOngoing,
                        "clearable" to notification.isClearable,
                        "priority" to notification.notification.priority
                    )
                }
                
                val summary = "Found ${notifications.size} active notifications:\n" +
                    notificationData.take(5).joinToString("\n") { 
                        "📱 ${it["app"]}: ${it["title"]}"
                    } + if (notifications.size > 5) "\n... and ${notifications.size - 5} more" else ""
                
                onProgress("✅ Notification scan complete!")
                ToolResult.success(summary, mapOf("count" to notifications.size, "notifications" to notificationData))
            }
        } catch (e: Exception) {
            onProgress("❌ Failed to read notifications")
            ToolResult.error("Failed to read notifications: ${e.message}")
        }
    }
    
    private suspend fun countNotifications(onProgress: (String) -> Unit): ToolResult {
        onProgress("🔍 Checking notification access...")
        if (!hasNotificationAccess()) {
            onProgress("❌ Notification access not granted")
            return ToolResult.error("Notification access required")
        }
        
        onProgress("📊 Counting notifications...")
        
        return try {
            val notifications = getActiveNotifications(limit = 100)
            val count = notifications.size
            val clearableCount = notifications.count { it.isClearable }
            val ongoingCount = notifications.count { it.isOngoing }
            
            onProgress("📱 Found $count total notifications")
            
            val summary = "📱 Notification Status:\n" +
                "Total: $count notifications\n" +
                "Clearable: $clearableCount\n" +
                "Ongoing: $ongoingCount"
            
            onProgress("✅ Notification count complete!")
            ToolResult.success(summary, mapOf(
                "total" to count,
                "clearable" to clearableCount, 
                "ongoing" to ongoingCount
            ))
        } catch (e: Exception) {
            onProgress("❌ Failed to count notifications")
            ToolResult.error("Failed to count notifications: ${e.message}")
        }
    }
    
    private suspend fun clearNotification(notificationId: String?, onProgress: (String) -> Unit): ToolResult {
        onProgress("🔍 Checking notification access...")
        if (!hasNotificationAccess()) {
            onProgress("❌ Notification access not granted")
            return ToolResult.error("Notification access required")
        }
        
        if (notificationId == null) {
            onProgress("❌ Notification ID required")
            return ToolResult.error("Notification ID required for clear action")
        }
        
        onProgress("🗑️ Attempting to clear notification $notificationId...")
        
        return try {
            // Note: This would require a NotificationListenerService to be implemented
            // For now, return a helpful message
            onProgress("⚠️ Clear functionality requires NotificationListenerService")
            ToolResult.success("Clear notification functionality requires NotificationListenerService implementation", 
                mapOf("action" to "clear", "id" to notificationId))
        } catch (e: Exception) {
            onProgress("❌ Failed to clear notification")
            ToolResult.error("Failed to clear notification: ${e.message}")
        }
    }
    
    private suspend fun getNotificationSummary(onProgress: (String) -> Unit): ToolResult {
        onProgress("🔍 Checking notification access...")
        if (!hasNotificationAccess()) {
            onProgress("❌ Notification access not granted")
            return ToolResult.error("Notification access required")
        }
        
        onProgress("📊 Generating notification summary...")
        
        return try {
            val notifications = getActiveNotifications(limit = 50)
            val appGroups = notifications.groupBy { it.packageName }
            
            onProgress("📱 Analyzing ${notifications.size} notifications from ${appGroups.size} apps...")
            
            val summary = StringBuilder("📱 Notification Summary:\n\n")
            
            appGroups.entries.sortedByDescending { it.value.size }.take(10).forEach { (pkg, notifs) ->
                val appName = getAppName(pkg)
                summary.append("${appName}: ${notifs.size} notifications\n")
            }
            
            val totalCount = notifications.size
            val appCount = appGroups.size
            
            summary.insert(0, "Total: $totalCount notifications from $appCount apps\n\n")
            
            onProgress("✅ Notification summary complete!")
            ToolResult.success(summary.toString(), mapOf(
                "total_notifications" to totalCount,
                "app_count" to appCount,
                "app_breakdown" to appGroups.mapValues { it.value.size }
            ))
        } catch (e: Exception) {
            onProgress("❌ Failed to get notification summary")
            ToolResult.error("Failed to get notification summary: ${e.message}")
        }
    }
    
    private fun hasNotificationAccess(): Boolean {
        // Check if the app has notification listener permission
        // This is a simplified check - real implementation would be more thorough
        return try {
            val enabledNotificationListeners = android.provider.Settings.Secure.getString(
                context.contentResolver,
                "enabled_notification_listeners"
            )
            enabledNotificationListeners?.contains(context.packageName) == true
        } catch (e: Exception) {
            false
        }
    }
    
    private fun getActiveNotifications(limit: Int, packageName: String? = null, priority: String? = null): List<StatusBarNotification> {
        // This is a placeholder - real implementation would require NotificationListenerService
        // For now, return empty list with TODO for proper implementation
        return emptyList()
    }
    
    private fun getAppName(packageName: String): String {
        return try {
            val packageManager = context.packageManager
            val applicationInfo = packageManager.getApplicationInfo(packageName, 0)
            packageManager.getApplicationLabel(applicationInfo).toString()
        } catch (e: Exception) {
            packageName // Fallback to package name if app name not found
        }
    }
    
    fun getKeywords(): List<String> {
        return listOf(
            "notification", "notifications", "notif", "alerts", "messages",
            "unread", "pending", "clear", "dismiss", "check messages"
        )
    }
    
    fun calculateRelevanceScore(request: String): Float {
        val keywords = getKeywords()
        val requestWords = request.lowercase().split(" ")
        val matches = keywords.count { keyword ->
            requestWords.any { it.contains(keyword) }
        }
        
        // Higher score for exact notification-related terms
        val exactMatches = keywords.count { keyword ->
            request.lowercase().contains(keyword)
        }
        
        return ((matches + exactMatches * 2).toFloat() / (keywords.size + 2)).coerceIn(0.0f, 1.0f)
    }
}

/**
 * NotificationListenerService implementation - needs to be declared in AndroidManifest.xml
 * This service would be used to actually access and manage notifications
 */
class CoquetteNotificationListenerService : NotificationListenerService() {
    
    companion object {
        private val activeNotifications = mutableListOf<StatusBarNotification>()
        
        @Synchronized
        fun getActiveNotifications(): List<StatusBarNotification> {
            return activeNotifications.toList()
        }
        
        @Synchronized
        fun addNotification(notification: StatusBarNotification) {
            activeNotifications.add(notification)
            // Keep only recent 100 notifications to prevent memory issues
            if (activeNotifications.size > 100) {
                activeNotifications.removeAt(0)
            }
        }
        
        @Synchronized
        fun removeNotification(key: String) {
            activeNotifications.removeAll { existing -> 
                existing.key == key 
            }
        }
    }
    
    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        sbn?.let { 
            addNotification(it)
        }
    }
    
    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        sbn?.let {
            removeNotification(it.key)
        }
    }
}
