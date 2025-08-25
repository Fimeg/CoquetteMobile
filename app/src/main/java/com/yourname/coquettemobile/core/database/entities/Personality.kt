package com.yourname.coquettemobile.core.database.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "personalities")
data class Personality(
    @PrimaryKey val id: String,
    val name: String,
    val emoji: String,
    val systemPrompt: String,
    val description: String,
    val isDefault: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),

                       // Unity architecture fields
                       val unifiedModel: String? = null,
                       val useUnifiedMode: Boolean = false,
                       val customSystemPrompt: String? = null,
                       val toolPreferences: String? = null,

                       // Refactored fields for modules and tool awareness
                       val toolAwarenessPrompt: String = "",
                       val modules: Map<String, String> = emptyMap(),
                       val toolPrompts: Map<String, String> = emptyMap()
)
