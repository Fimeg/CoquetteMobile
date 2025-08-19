package com.yourname.coquettemobile.core.tools

import android.content.Context
import android.os.BatteryManager
import android.os.Build
import android.os.Environment
import android.os.StatFs
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.telephony.TelephonyManager
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import java.text.DecimalFormat

@Singleton
class DeviceContextTool @Inject constructor(
    @ApplicationContext private val context: Context
) : MobileTool {
    
    override val name = "device_context"
    override val description = "Get comprehensive device status, battery, storage, network, and system information"
    override val requiredPermissions = listOf(
        "android.permission.ACCESS_NETWORK_STATE"
    )
    override val riskLevel = RiskLevel.LOW
    
    override suspend fun execute(parameters: Map<String, Any>): ToolResult {
        return try {
            val action = parameters["action"] as? String ?: "all"
            
            val result = when (action) {
                "battery" -> getBatteryInfo()
                "storage" -> getStorageInfo()
                "network" -> getNetworkInfo()
                "system" -> getSystemInfo()
                "performance" -> getPerformanceInfo()
                "all" -> getCompleteDeviceContext()
                else -> return ToolResult.error("Unknown action: $action. Available: battery, storage, network, system, performance, all")
            }
            
            ToolResult.success(result)
            
        } catch (e: Exception) {
            ToolResult.error("Failed to get device context: ${e.message}")
        }
    }
    
    private fun getBatteryInfo(): String {
        val batteryManager = context.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
        
        val level = batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
        val isCharging = batteryManager.isCharging
        val temperature = batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_AVERAGE)
        val voltage = batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW)
        
        val health = when (batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_STATUS)) {
            BatteryManager.BATTERY_STATUS_CHARGING -> "Charging"
            BatteryManager.BATTERY_STATUS_DISCHARGING -> "Discharging"
            BatteryManager.BATTERY_STATUS_FULL -> "Full"
            BatteryManager.BATTERY_STATUS_NOT_CHARGING -> "Not Charging"
            else -> "Unknown"
        }
        
        return """
            🔋 **Battery Status**
            • Level: $level%
            • Status: $health
            • Charging: ${if (isCharging) "Yes" else "No"}
            • Temperature: ${temperature / 10.0}°C
            • Health: ${getBatteryHealthDescription()}
        """.trimIndent()
    }
    
    private fun getStorageInfo(): String {
        val internalStat = StatFs(Environment.getDataDirectory().path)
        val externalStat = if (Environment.getExternalStorageState() == Environment.MEDIA_MOUNTED) {
            StatFs(Environment.getExternalStorageDirectory().path)
        } else null
        
        val internalAvailable = formatBytes(internalStat.availableBytes)
        val internalTotal = formatBytes(internalStat.totalBytes)
        val internalUsed = formatBytes(internalStat.totalBytes - internalStat.availableBytes)
        val internalUsedPercent = ((internalStat.totalBytes - internalStat.availableBytes) * 100.0 / internalStat.totalBytes).toInt()
        
        var storageInfo = """
            💾 **Storage Information**
            
            📱 **Internal Storage**
            • Total: $internalTotal
            • Used: $internalUsed ($internalUsedPercent%)
            • Available: $internalAvailable
        """.trimIndent()
        
        if (externalStat != null) {
            val externalAvailable = formatBytes(externalStat.availableBytes)
            val externalTotal = formatBytes(externalStat.totalBytes)
            val externalUsed = formatBytes(externalStat.totalBytes - externalStat.availableBytes)
            val externalUsedPercent = ((externalStat.totalBytes - externalStat.availableBytes) * 100.0 / externalStat.totalBytes).toInt()
            
            storageInfo += """
                
                💳 **External Storage**
                • Total: $externalTotal
                • Used: $externalUsed ($externalUsedPercent%)
                • Available: $externalAvailable
            """.trimIndent()
        }
        
        return storageInfo
    }
    
    private fun getNetworkInfo(): String {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = connectivityManager.activeNetwork
        val networkCapabilities = connectivityManager.getNetworkCapabilities(network)
        
        if (networkCapabilities == null) {
            return "🌐 **Network Status**: No active connection"
        }
        
        val connectionType = when {
            networkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "Wi-Fi"
            networkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "Cellular"
            networkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> "Ethernet"
            networkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_VPN) -> "VPN"
            else -> "Unknown"
        }
        
        val isMetered = connectivityManager.isActiveNetworkMetered
        val linkDownstreamBandwidth = networkCapabilities.linkDownstreamBandwidthKbps
        val linkUpstreamBandwidth = networkCapabilities.linkUpstreamBandwidthKbps
        
        var networkInfo = """
            🌐 **Network Information**
            • Type: $connectionType
            • Metered: ${if (isMetered) "Yes" else "No"}
            • Download Speed: ${if (linkDownstreamBandwidth > 0) "${linkDownstreamBandwidth} Kbps" else "Unknown"}
            • Upload Speed: ${if (linkUpstreamBandwidth > 0) "${linkUpstreamBandwidth} Kbps" else "Unknown"}
        """.trimIndent()
        
        // Add cellular info if available
        if (connectionType == "Cellular") {
            val telephonyManager = context.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
            try {
                val networkOperator = telephonyManager.networkOperatorName
                val networkType = telephonyManager.dataNetworkType
                networkInfo += "\n• Carrier: $networkOperator"
                networkInfo += "\n• Technology: ${getNetworkTypeDescription(networkType)}"
            } catch (e: SecurityException) {
                networkInfo += "\n• Carrier: Permission required"
            }
        }
        
        return networkInfo
    }
    
    private fun getSystemInfo(): String {
        val runtime = Runtime.getRuntime()
        val totalMemory = runtime.totalMemory()
        val freeMemory = runtime.freeMemory()
        val usedMemory = totalMemory - freeMemory
        val maxMemory = runtime.maxMemory()
        
        return """
            📱 **System Information**
            • Device: ${Build.MANUFACTURER} ${Build.MODEL}
            • Android: ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})
            • Processor: ${Build.SUPPORTED_ABIS.firstOrNull() ?: "Unknown"}
            • Available RAM: ${formatBytes(freeMemory)}
            • Used RAM: ${formatBytes(usedMemory)}
            • Max RAM: ${formatBytes(maxMemory)}
            • CPU Cores: ${Runtime.getRuntime().availableProcessors()}
        """.trimIndent()
    }
    
    private fun getPerformanceInfo(): String {
        val runtime = Runtime.getRuntime()
        val memoryUsagePercent = ((runtime.totalMemory() - runtime.freeMemory()) * 100.0 / runtime.maxMemory()).toInt()
        
        val batteryManager = context.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
        val batteryLevel = batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
        
        val performanceStatus = when {
            batteryLevel < 20 -> "⚠️ Low Battery - Consider power saving"
            memoryUsagePercent > 85 -> "⚠️ High Memory Usage - Close apps"
            memoryUsagePercent > 70 -> "⚡ Moderate Load"
            else -> "✅ Optimal Performance"
        }
        
        return """
            ⚡ **Performance Status**
            • Overall: $performanceStatus
            • Memory Usage: $memoryUsagePercent%
            • Battery Level: $batteryLevel%
            
            📊 **Recommendations**
            ${getPerformanceRecommendations(batteryLevel, memoryUsagePercent)}
        """.trimIndent()
    }
    
    private fun getCompleteDeviceContext(): String {
        return """
            📱 **Complete Device Context**
            
            ${getBatteryInfo()}
            
            ${getStorageInfo()}
            
            ${getNetworkInfo()}
            
            ${getSystemInfo()}
            
            ${getPerformanceInfo()}
            
            🕒 **Context Generated**: ${java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date())}
        """.trimIndent()
    }
    
    private fun formatBytes(bytes: Long): String {
        val df = DecimalFormat("#.##")
        return when {
            bytes >= 1024 * 1024 * 1024 -> "${df.format(bytes / (1024.0 * 1024.0 * 1024.0))} GB"
            bytes >= 1024 * 1024 -> "${df.format(bytes / (1024.0 * 1024.0))} MB"
            bytes >= 1024 -> "${df.format(bytes / 1024.0)} KB"
            else -> "$bytes B"
        }
    }
    
    private fun getBatteryHealthDescription(): String {
        val batteryManager = context.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
        val health = batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_STATUS)
        
        return when (health) {
            BatteryManager.BATTERY_HEALTH_GOOD -> "Good"
            BatteryManager.BATTERY_HEALTH_OVERHEAT -> "Overheating"
            BatteryManager.BATTERY_HEALTH_DEAD -> "Dead"
            BatteryManager.BATTERY_HEALTH_OVER_VOLTAGE -> "Over Voltage"
            BatteryManager.BATTERY_HEALTH_COLD -> "Cold"
            else -> "Unknown"
        }
    }
    
    private fun getNetworkTypeDescription(networkType: Int): String {
        return when (networkType) {
            TelephonyManager.NETWORK_TYPE_1xRTT -> "1xRTT"
            TelephonyManager.NETWORK_TYPE_CDMA -> "CDMA"
            TelephonyManager.NETWORK_TYPE_EDGE -> "EDGE"
            TelephonyManager.NETWORK_TYPE_EHRPD -> "eHRPD"
            TelephonyManager.NETWORK_TYPE_EVDO_0 -> "EVDO rev. 0"
            TelephonyManager.NETWORK_TYPE_EVDO_A -> "EVDO rev. A"
            TelephonyManager.NETWORK_TYPE_EVDO_B -> "EVDO rev. B"
            TelephonyManager.NETWORK_TYPE_GPRS -> "GPRS"
            TelephonyManager.NETWORK_TYPE_HSDPA -> "HSDPA"
            TelephonyManager.NETWORK_TYPE_HSPA -> "HSPA"
            TelephonyManager.NETWORK_TYPE_HSPAP -> "HSPA+"
            TelephonyManager.NETWORK_TYPE_HSUPA -> "HSUPA"
            TelephonyManager.NETWORK_TYPE_IDEN -> "iDen"
            TelephonyManager.NETWORK_TYPE_LTE -> "LTE"
            TelephonyManager.NETWORK_TYPE_UMTS -> "UMTS"
            TelephonyManager.NETWORK_TYPE_UNKNOWN -> "Unknown"
            else -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && networkType == TelephonyManager.NETWORK_TYPE_NR) "5G" else "Unknown"
        }
    }
    
    private fun getPerformanceRecommendations(batteryLevel: Int, memoryUsage: Int): String {
        val recommendations = mutableListOf<String>()
        
        if (batteryLevel < 20) {
            recommendations.add("• Enable battery saver mode")
            recommendations.add("• Reduce screen brightness")
            recommendations.add("• Close unnecessary apps")
        }
        
        if (memoryUsage > 85) {
            recommendations.add("• Close background apps")
            recommendations.add("• Restart device if issues persist")
            recommendations.add("• Clear app caches")
        }
        
        if (batteryLevel > 80 && memoryUsage < 50) {
            recommendations.add("• System running optimally")
            recommendations.add("• Good time for intensive tasks")
        }
        
        return if (recommendations.isEmpty()) {
            "• No specific recommendations"
        } else {
            recommendations.joinToString("\n")
        }
    }
}