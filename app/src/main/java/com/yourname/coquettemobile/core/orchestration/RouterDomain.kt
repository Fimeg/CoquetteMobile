package com.yourname.coquettemobile.core.orchestration

/**
 * Defines the specialized domains for Tool Routers
 * Each domain represents a category of operations that require domain expertise
 */
enum class RouterDomain(val description: String) {
    ANDROID_INTELLIGENCE("Information gathering, reconnaissance, and environment analysis"),
    DESKTOP_EXPLOIT("Desktop HID operations, keyboard/mouse injection, and physical device control"),
    NETWORK_OPERATIONS("Network communication, data transfer, and connectivity"),
    FILE_OPERATIONS("File system manipulation, data extraction, and storage management"),
    DEVICE_CONTROL("Hardware interaction, HID operations, and device manipulation"),
    SECURITY_OPERATIONS("Vulnerability assessment, penetration testing, and security analysis"),
    DATA_PROCESSING("Content analysis, extraction, transformation, and synthesis"),
    COMMUNICATION("Messaging, notifications, and inter-system communication"),
    WEB_INTELLIGENCE("Web scraping, content extraction, and online data gathering")
}